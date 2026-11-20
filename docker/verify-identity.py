"""End-to-end identity checks with disposable MySQL, Redis and two Java processes."""

import base64
import concurrent.futures
import http.client
import json
import os
import re
import shutil
import socket
import subprocess
import tempfile
import time
import uuid
from contextlib import ExitStack
from http.cookies import SimpleCookie
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "backend/creatorhub-api/target/creatorhub-api-0.1.0-SNAPSHOT.jar"
PASSWORD = "local integration password"
DB_PASSWORD = "identity-test-db"
SESSION_COOKIE = "CREATORHUB_SESSION"
PREFIX = "/api/v1"


def docker(*args):
    return subprocess.check_output(["docker", *args], text=True, stderr=subprocess.STDOUT).strip()


def wait_for(check, description, timeout=60):
    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            if check():
                return
        except (OSError, subprocess.CalledProcessError) as error:
            last_error = error
        time.sleep(0.25)
    raise AssertionError(f"Timed out waiting for {description}: {last_error}")


def mysql(container, sql):
    return docker(
        "exec", container, "sh", "-ec",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N creatorhub -e "$1"',
        "mysql", sql,
    )


def port_for(container, port):
    return int(docker("port", container, f"{port}/tcp").rsplit(":", 1)[1])


def launch_container(stack, name, port, image, *options):
    docker("run", "-d", "--name", name, "-p", f"127.0.0.1::{port}", *options, image)
    stack.callback(lambda: docker("rm", "-fv", name))


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def request(port, method, path, *, cookies=None, csrf=None, body=None, status=200, code=None):
    headers = {}
    if cookies:
        headers["Cookie"] = cookies
    if csrf:
        headers[csrf["headerName"]] = csrf["token"]
    if body is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(body)
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=7)
    try:
        connection.request(method, PREFIX + path, body, headers)
        response = connection.getresponse()
        raw = response.read()
        payload = json.loads(raw) if raw else {}
        allowed_statuses = (status,) if isinstance(status, int) else status
        assert response.status in allowed_statuses, (method, path, response.status, payload)
        if code:
            assert payload.get("code") == code and payload.get("message"), (path, payload)
        return payload, response
    finally:
        connection.close()


def csrf_pair(port, cookies=None):
    payload, response = request(port, "GET", "/auth/csrf", cookies=cookies)
    assert "no-store" in response.getheader("Cache-Control", "")
    return payload, cookie_from(response) if cookies is None else cookies


def cookie_from(response):
    field = response.getheader("Set-Cookie", "")
    header = SimpleCookie(field)
    assert SESSION_COOKIE in header, field
    return f"{SESSION_COOKIE}={header[SESSION_COOKIE].value}"


def login(port, email, *, password=PASSWORD, status=200, code=None):
    token, anonymous_cookie = csrf_pair(port)
    payload, response = request(
        port, "POST", "/auth/login", cookies=anonymous_cookie, csrf=token,
        body={"email": email, "password": password}, status=status, code=code,
    )
    if status == 200:
        authenticated_cookie = cookie_from(response)
        assert authenticated_cookie != anonymous_cookie
        assert "HttpOnly" in response.getheader("Set-Cookie")
        assert "SameSite=Lax" in response.getheader("Set-Cookie")
        assert isinstance(payload["userId"], str)
        return authenticated_cookie, anonymous_cookie, payload
    return payload


def registration(email, name, password=PASSWORD):
    return {"email": email, "password": password, "displayName": name}


def register(port, body, status=201, code=None):
    token, cookie = csrf_pair(port)
    return request(port, "POST", "/auth/register", cookies=cookie, csrf=token,
                   body=body, status=status, code=code)[0]


def start_api(stack, log_dir, java, db_port, redis_port):
    port = free_port()
    env = {
        **os.environ,
        "CREATORHUB_BIND_ADDRESS": "127.0.0.1",
        "CREATORHUB_PORT": str(port),
        "CREATORHUB_DB_URL": f"jdbc:mysql://127.0.0.1:{db_port}/creatorhub?connectionTimeZone=UTC",
        "CREATORHUB_DB_USER": "creatorhub",
        "MYSQL_APP_PASSWORD": DB_PASSWORD,
        "CREATORHUB_DB_BASELINE_ON_MIGRATE": "false",
        "CREATORHUB_REDIS_HOST": "127.0.0.1",
        "CREATORHUB_REDIS_PORT": str(redis_port),
        "CREATORHUB_SESSION_NAMESPACE": "creatorhub:identity-test",
        "CREATORHUB_SESSION_TIMEOUT": "30m",
        "CREATORHUB_SECURE_COOKIE": "false",
    }
    log = log_dir / f"api-{port}.log"
    with log.open("w") as stream:
        proc = subprocess.Popen([java, "-jar", str(JAR)], stdout=stream, stderr=subprocess.STDOUT, env=env)
    stack.callback(stop_process, proc)

    def ready():
        if proc.poll() is not None:
            raise AssertionError(f"API {port} exited. See {log}")
        return request(port, "GET", "/auth/csrf")[0]["token"]

    wait_for(ready, f"API on port {port}")
    return port


def stop_process(proc):
    if proc.poll() is None:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait()


def verify_sessions(a, b, db, redis):
    # Seed the two accounts through the real registration entry point.
    alice = register(a, registration("  ALICE@example.test  ", "Alice"))
    bob = register(b, registration("bob@example.test", "Bob"))
    assert alice["userId"] != bob["userId"]
    alice_cookie, pre_login_cookie, logged_in = login(a, "Alice@example.test")
    assert logged_in == {**alice, "displayName": "Alice"}
    bob_cookie, _, _ = login(b, "bob@example.test")
    same_account_cookie, _, _ = login(a, "alice@example.test")
    assert same_account_cookie != alice_cookie

    alice_me, response = request(b, "GET", "/me", cookies=alice_cookie)
    assert alice_me == {**alice, "email": "alice@example.test", "displayName": "Alice"}
    assert "no-store" in response.getheader("Cache-Control", "")
    assert request(a, "GET", "/me", cookies=bob_cookie)[0]["userId"] == bob["userId"]
    for denied in (None, pre_login_cookie):
        request(b, "GET", "/me", cookies=denied, status=401, code="UNAUTHENTICATED")

    mysql(db, "UPDATE user_account SET status='DISABLED' WHERE email_normalized='bob@example.test'")
    bad = login(a, "alice@example.test", password="different password", status=401, code="INVALID_CREDENTIALS")
    assert bad == login(a, "unknown@example.test", status=401, code="INVALID_CREDENTIALS")
    assert bad == login(a, "bob@example.test", status=401, code="INVALID_CREDENTIALS")
    mysql(db, "UPDATE user_account SET status='ACTIVE' WHERE email_normalized='bob@example.test'")

    old_token, _ = csrf_pair(a, cookies=alice_cookie)
    request(b, "POST", "/auth/logout", cookies=alice_cookie, csrf=old_token, status=204)
    for listener in (a, b):
        request(listener, "GET", "/me", cookies=alice_cookie, status=401, code="UNAUTHENTICATED")
    assert request(b, "GET", "/me", cookies=same_account_cookie)[0]["userId"] == alice["userId"]
    token, anonymous_cookie = csrf_pair(a)
    request(b, "POST", "/auth/logout", cookies=anonymous_cookie, csrf=token,
            status=401, code="UNAUTHENTICATED")

    expiry_cookie, _, _ = login(a, "alice@example.test")
    session_id = base64.b64decode(expiry_cookie.split("=", 1)[1]).decode("ascii")
    key = f"creatorhub:identity-test:sessions:{session_id}"
    assert 1700 <= int(docker("exec", redis, "redis-cli", "TTL", key)) <= 1800
    assert docker("exec", redis, "redis-cli", "EXPIRE", key, "1") == "1"
    time.sleep(1.25)
    request(b, "GET", "/me", cookies=expiry_cookie, status=401, code="UNAUTHENTICATED")

    for method, path, body in [
        ("POST", "/auth/login", {"email": "alice@example.test", "password": PASSWORD}),
        ("POST", "/auth/register", registration("blocked@example.test", "Blocked")),
        ("POST", "/auth/logout", None),
        ("PATCH", "/me/profile", {"displayName": "Blocked"}),
    ]:
        request(b, method, path, cookies=same_account_cookie, body=body, status=403, code="CSRF_INVALID")
    print("PASS: cross-instance identity, session rotation, logout, expiry, CSRF and credential errors")
    return same_account_cookie, bob_cookie


def verify_registration_and_profile(a, b, db, alice_cookie, bob_cookie):
    first = csrf_pair(a)
    second = csrf_pair(b)

    def submit(port, state, email):
        token, cookie = state
        return request(port, "POST", "/auth/register", cookies=cookie, csrf=token,
                       body=registration(email, "Shared nickname"), status=(201, 409))

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        submissions = [
            pool.submit(submit, a, first, "race@example.test"),
            pool.submit(submit, b, second, " RACE@EXAMPLE.TEST "),
        ]
        results = [submission.result() for submission in submissions]
    assert sorted(response.status for _, response in results) == [201, 409]
    assert next(payload for payload, response in results if response.status == 409)["code"] == "EMAIL_ALREADY_REGISTERED"
    count = mysql(db, "SELECT COUNT(*) FROM user_account a JOIN user_profile p ON a.id=p.user_id "
                      "WHERE a.email_normalized='race@example.test'")
    assert count == "1", count
    assert mysql(db, "SELECT COUNT(*) FROM user_account WHERE email_normalized='race@example.test' "
                     "AND password_hash LIKE '$argon2id$%'") == "1"
    register(a, registration("invalid@example.test", " ", "short"), status=400, code="INVALID_REQUEST")

    mysql(db, "CREATE TRIGGER identity_test_fail_profile BEFORE INSERT ON user_profile "
              "FOR EACH ROW SET NEW.display_name=IF(NEW.display_name='Rollback fixture', NULL, NEW.display_name)")
    try:
        register(a, registration("rollback@example.test", "Rollback fixture"),
                 status=500, code="INTERNAL_ERROR")
    finally:
        mysql(db, "DROP TRIGGER identity_test_fail_profile")
    assert mysql(db, "SELECT COUNT(*) FROM user_account WHERE email_normalized='rollback@example.test'") == "0"

    csrf, _ = csrf_pair(a, cookies=alice_cookie)
    original_bob = request(b, "GET", "/me", cookies=bob_cookie)[0]
    request(b, "PATCH", "/me/profile", cookies=alice_cookie, csrf=csrf,
            body={"userId": original_bob["userId"], "displayName": "Intrusion"},
            status=400, code="INVALID_REQUEST")
    request(b, "PATCH", "/me/profile", cookies=alice_cookie, csrf=csrf,
            body={"displayName": 42}, status=400, code="INVALID_REQUEST")
    assert request(a, "GET", "/me", cookies=alice_cookie)[0]["displayName"] == "Alice"
    updated = request(b, "PATCH", "/me/profile", cookies=alice_cookie, csrf=csrf,
                      body={"displayName": "  Bob  "})[0]
    assert updated["displayName"] == "Bob"  # Duplicate names are allowed.
    assert request(a, "GET", "/me", cookies=alice_cookie)[0]["displayName"] == "Bob"
    assert request(a, "GET", "/me", cookies=bob_cookie)[0] == original_bob
    print("PASS: concurrent registration, transactional rollback and profile ownership")


def verify_indexes(db):
    rows = ", ".join(f"('index-{i}@example.test', 'test-index-fixture', 'DISABLED')" for i in range(512))
    mysql(db, "INSERT INTO user_account (email_normalized,password_hash,status) VALUES " + rows)
    mysql(db, "INSERT INTO user_profile (user_id,display_name) "
              "SELECT id,'Index fixture' FROM user_account WHERE email_normalized LIKE 'index-%'")
    mysql(db, "ANALYZE TABLE user_account, user_profile")
    user_id = int(mysql(db, "SELECT id FROM user_account WHERE email_normalized='alice@example.test'"))
    explained = [
        ("SELECT id,password_hash,status FROM user_account WHERE email_normalized='alice@example.test'",
         {"uk_user_account_email"}),
        (f"SELECT email_normalized FROM user_account WHERE id={user_id}", {"PRIMARY"}),
        (f"SELECT user_id,display_name FROM user_profile WHERE user_id={user_id}", {"PRIMARY"}),
        (f"UPDATE user_profile SET display_name='Alice' WHERE user_id={user_id}", {"PRIMARY"}),
    ]
    for statement, expected_keys in explained:
        plan = mysql(db, f"EXPLAIN FORMAT=JSON {statement}")
        keys = set(re.findall(r'"key":\s*"([^"]+)"', plan))
        assert keys == expected_keys, (statement, keys, plan)
    index_count = mysql(db, "SELECT COUNT(*) FROM information_schema.statistics "
                            "WHERE table_schema='creatorhub' AND table_name IN ('user_account','user_profile')")
    assert index_count == "3", index_count
    print("PASS: MySQL uses email unique index and user primary keys for identity paths")


def measure_login(a, b):
    def sample(port):
        token, anonymous_cookie = csrf_pair(port)
        started = time.perf_counter()
        request(port, "POST", "/auth/login", cookies=anonymous_cookie, csrf=token,
                body={"email": "alice@example.test", "password": PASSWORD})
        return (time.perf_counter() - started) * 1000

    ports = [a, b] * 12
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        durations = sorted(pool.map(sample, ports))
    print(f"Login sample: {len(durations)} requests, 4 in flight, "
          f"P95 {durations[22]:.1f} ms (local, CSRF fetch excluded)")


def verify_redis_outage(a, b, redis, cookie):
    token, _ = csrf_pair(a, cookies=cookie)
    docker("stop", "-t", "1", redis)
    cases = [
        (a, "GET", "/me", None, None),
        (b, "GET", "/auth/csrf", None, None),
        (a, "POST", "/auth/login", token, {"email": "alice@example.test", "password": PASSWORD}),
        (b, "POST", "/auth/logout", token, None),
    ]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        requests = [
            pool.submit(request, port, method, path, cookies=cookie, csrf=csrf, body=body,
                        status=503, code="SESSION_UNAVAILABLE")
            for port, method, path, csrf, body in cases
        ]
        for result in requests:
            result.result()
    print("PASS: Redis outage returns 503 for login, logout, /me and CSRF")


def main():
    if not JAR.is_file():
        raise SystemExit("Build backend/pom.xml with Maven before running this check.")
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java") if java_home else (shutil.which("java") or "java")
    suffix = uuid.uuid4().hex[:10]
    db, redis = f"creatorhub-identity-mysql-{suffix}", f"creatorhub-identity-redis-{suffix}"
    log_dir = Path(tempfile.mkdtemp(prefix="creatorhub-identity-"))
    print(f"Identity verification logs: {log_dir}", flush=True)
    with ExitStack() as stack:
        launch_container(stack, db, 3306, "mysql:8.4.13",
                         "-e", f"MYSQL_ROOT_PASSWORD={DB_PASSWORD}", "-e", "MYSQL_DATABASE=creatorhub",
                         "-e", "MYSQL_USER=creatorhub", "-e", f"MYSQL_PASSWORD={DB_PASSWORD}")
        launch_container(stack, redis, 6379, "redis:8.2.11-alpine")
        wait_for(lambda: mysql(db, "SELECT 1"), "MySQL")
        db_port, redis_port = port_for(db, 3306), port_for(redis, 6379)
        a = start_api(stack, log_dir, java, db_port, redis_port)
        b = start_api(stack, log_dir, java, db_port, redis_port)
        alice_cookie, bob_cookie = verify_sessions(a, b, db, redis)
        verify_registration_and_profile(a, b, db, alice_cookie, bob_cookie)
        verify_indexes(db)
        measure_login(a, b)
        verify_redis_outage(a, b, redis, alice_cookie)
    print("Identity verification passed.")


if __name__ == "__main__":
    main()
