FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

COPY . .

RUN ./gradlew :bootstrap:fatJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=build /app/bootstrap/build/libs/chameleon.jar app.jar

VOLUME ["/app/workspace", "/app/config", "/app/extensions", "/app/data"]

EXPOSE 18789

ENTRYPOINT ["java", "-jar", "app.jar"]
