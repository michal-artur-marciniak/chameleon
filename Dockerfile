FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache sqlite sqlite-dev

WORKDIR /app

COPY app/build/libs/chameleon.jar app.jar

VOLUME ["/app/workspace", "/app/config", "/app/extensions", "/app/data"]

EXPOSE 18789

ENTRYPOINT ["java", "-jar", "app.jar"]
