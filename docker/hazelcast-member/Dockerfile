FROM hazelcast/hazelcast:5.1.7

COPY config/hazelcast-member.yaml /opt/hazelcast/config_ext/hazelcast.yaml
COPY hz-demo-cache-service/target/classes/ /opt/hazelcast/ext/cache-service-classes/
COPY hz-demo-cache-service/target/member-libs/ /opt/hazelcast/ext/member-libs/

ENV HAZELCAST_CONFIG=/opt/hazelcast/config_ext/hazelcast.yaml
ENV CLASSPATH=/opt/hazelcast/ext/cache-service-classes:/opt/hazelcast/ext/member-libs/*
