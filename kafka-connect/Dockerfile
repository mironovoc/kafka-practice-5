FROM confluentinc/cp-kafka-connect:7.7.1

USER root

# в уроке jar качали руками в confluent-hub-components.
# здесь то же самое, только на сборке образа, чтобы compose поднимался сразу.
ADD https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.15.0/jmx_prometheus_javaagent-0.15.0.jar /opt/jmx_prometheus_javaagent-0.15.0.jar
ADD https://repo1.maven.org/maven2/io/debezium/debezium-connector-postgres/2.5.4.Final/debezium-connector-postgres-2.5.4.Final-plugin.tar.gz /tmp/debezium-plugin.tar.gz

COPY kafka-connect.yml /opt/kafka-connect.yml

RUN mkdir -p /usr/share/confluent-hub-components \
    && tar -xzf /tmp/debezium-plugin.tar.gz -C /usr/share/confluent-hub-components \
    && rm /tmp/debezium-plugin.tar.gz \
    && chmod 644 /opt/jmx_prometheus_javaagent-0.15.0.jar /opt/kafka-connect.yml

USER appuser
