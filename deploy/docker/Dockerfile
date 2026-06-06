ARG JAVA_BASE_IMAGE=eclipse-temurin:17-jre-jammy
FROM ${JAVA_BASE_IMAGE}

ARG SERVICE_NAME

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY ${SERVICE_NAME}/target/${SERVICE_NAME}-*.jar /app/app.jar

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="" \
    SERVER_PORT=8080

EXPOSE 8080 8100 8101 8102 8103 8104 8105 8106 8107 8108 8109 8800

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
