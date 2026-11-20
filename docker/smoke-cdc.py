"""Verify MySQL INSERT, UPDATE and DELETE reach the local Kafka topic."""

import json
import subprocess
import sys
import time
from pathlib import Path
from uuid import uuid4


ROOT = Path(__file__).resolve().parents[1]
TOPIC = "creatorhub.cdc"
TABLE = "cdc_smoke_probe"
KAFKA_BIN = "/opt/kafka/bin"


def compose(*args, check=True):
    return subprocess.run(
        ["docker", "compose", *args],
        cwd=ROOT,
        check=check,
        capture_output=True,
        text=True,
        timeout=40,
    )


def mysql(sql, *, root=False):
    if root:
        command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -Nse "$1"'
    else:
        command = (
            'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u "$MYSQL_USER" '
            '"$MYSQL_DATABASE" -Nse "$1"'
        )
    return compose("exec", "-T", "mysql", "sh", "-ec", command, "mysql", sql).stdout.strip()


def kafka(script, *args, check=True):
    return compose(
        "exec", "-T", "kafka", f"{KAFKA_BIN}/{script}",
        "--bootstrap-server", "kafka:29092", *args, check=check,
    )


def wait_for_canal():
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        sessions = mysql(
            "SELECT COUNT(*) FROM information_schema.PROCESSLIST "
            "WHERE USER = 'canal' AND COMMAND LIKE 'Binlog Dump%';",
            root=True,
        )
        if sessions != "0":
            return
        time.sleep(1)
    raise RuntimeError("Canal has no active MySQL binlog connection.")


def main():
    wait_for_canal()
    mysql(
        f"CREATE TABLE IF NOT EXISTS {TABLE} "
        "(id CHAR(32) PRIMARY KEY, value INT NOT NULL) ENGINE=InnoDB;"
    )
    offsets = kafka("kafka-get-offsets.sh", "--topic", TOPIC, "--time", "latest")
    offset = next(
        line.split(":")[2] for line in offsets.stdout.splitlines()
        if line.startswith(f"{TOPIC}:0:")
    )
    probe_id = uuid4().hex
    try:
        mysql(
            f"INSERT INTO {TABLE} VALUES ('{probe_id}', 1);"
            f"UPDATE {TABLE} SET value = 2 WHERE id = '{probe_id}';"
            f"DELETE FROM {TABLE} WHERE id = '{probe_id}';"
        )
    except subprocess.CalledProcessError:
        mysql(f"DELETE FROM {TABLE} WHERE id = '{probe_id}';")
        raise

    messages = kafka(
        "kafka-console-consumer.sh", "--topic", TOPIC, "--partition", "0",
        "--offset", offset, "--timeout-ms", "8000", check=False,
    )
    observed = []
    for line in messages.stdout.splitlines():
        if not line.startswith("{"):
            continue
        event = json.loads(line)
        if event.get("database") != "creatorhub" or event.get("table") != TABLE:
            continue
        for row in event.get("data") or []:
            if row.get("id") == probe_id:
                observed.append((event["type"], str(row.get("value"))))

    expected = [("INSERT", "1"), ("UPDATE", "2"), ("DELETE", "2")]
    if observed != expected:
        raise RuntimeError(
            f"Expected {expected}, received {observed}.\n{messages.stderr.strip()}"
        )
    print(f"CDC verified for {probe_id}: INSERT → UPDATE → DELETE")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, StopIteration, ValueError, subprocess.SubprocessError) as error:
        detail = getattr(error, "stderr", None) or str(error)
        sys.exit(f"CDC smoke check failed: {detail or 'Kafka partition offset unavailable'}")
