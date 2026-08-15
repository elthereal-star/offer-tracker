FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /workspace
COPY pom.xml ./
COPY web/package.json web/package-lock.json ./web/
COPY web/index.html web/vite.config.js ./web/
COPY web/src ./web/src
COPY src ./src

RUN mvn -B package

FROM eclipse-temurin:21-jre

RUN useradd --system --uid 10001 --create-home --home-dir /app offertracker
WORKDIR /app
COPY --from=builder --chown=offertracker:offertracker /workspace/target/offer-tracker-0.1.0.jar app.jar
RUN mkdir -p /app/data && chown offertracker:offertracker /app/data

USER offertracker
VOLUME ["/app/data"]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
