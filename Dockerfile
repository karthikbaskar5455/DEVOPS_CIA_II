# --- builder stage ---
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# package (unit tests run earlier in CI; skip here to keep image builds fast)
RUN mvn -B -q -DskipTests=true package

# --- runtime stage ---
FROM eclipse-temurin:17-jre
RUN useradd -r -u 10001 -g root appuser
WORKDIR /app
COPY --from=builder /app/target/java-cicd-app.jar ./app.jar
USER appuser
EXPOSE 8080
ENV APP_VERSION=local-dev
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
