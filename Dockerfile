# syntax=docker/dockerfile:1

FROM eclipse-temurin:26-jdk-noble AS build
WORKDIR /src
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
COPY src src
RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:26-jre-noble
WORKDIR /opt/myredis

RUN groupadd --system myredis && useradd --system --gid myredis --home-dir /opt/myredis myredis \
    && mkdir -p /data && chown myredis:myredis /data

COPY --from=build /src/target/my-redis-0.1.0-SNAPSHOT.jar /opt/myredis/my-redis.jar

ENV MYREDIS_HOST=0.0.0.0 \
    MYREDIS_PORT=6379 \
    MYREDIS_PERSISTENCE_DIRECTORY=/data

EXPOSE 6379

USER myredis
VOLUME ["/data"]

ENTRYPOINT ["java", "-jar", "/opt/myredis/my-redis.jar"]
