FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src/main ./src/main

RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --uid 10001 --user-group --no-create-home app \
    && mkdir -p /app/config \
    && chown app:app /app /app/config

COPY --from=build --chown=app:app /workspace/build/libs/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=local

EXPOSE 8080

USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
