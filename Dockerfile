FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x ./gradlew

COPY src src
RUN ./gradlew clean bootJar -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser \
    && mkdir -p /app/uploads \
    && chown -R appuser:appgroup /app

COPY --from=build /app/build/libs/*.jar app.jar
RUN chown appuser:appgroup /app/app.jar

USER appuser

ENV PORT=8080
ENV UPLOAD_DIR=/app/uploads

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
