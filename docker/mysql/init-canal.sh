# The MySQL entrypoint sources this file after creating the application database.
case "$CANAL_DB_PASSWORD" in
    ''|*[!A-Za-z0-9_-]*)
        echo "CANAL_DB_PASSWORD must use letters, digits, underscores or hyphens." >&2
        exit 1
        ;;
esac

docker_process_sql <<SQL
CREATE USER 'canal'@'%' IDENTIFIED BY '${CANAL_DB_PASSWORD}';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%';
GRANT SELECT ON creatorhub.* TO 'canal'@'%';
SQL
