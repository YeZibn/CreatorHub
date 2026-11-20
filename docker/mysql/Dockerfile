FROM mysql:8.4.13

COPY --chmod=0644 cdc.cnf /etc/mysql/conf.d/creatorhub.cnf
COPY --chmod=0644 init-canal.sh /docker-entrypoint-initdb.d/10-init-canal.sh
