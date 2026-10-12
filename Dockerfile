FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build

# Dependências em uma camada separada para aproveitar o cache entre deploys.
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd --system app \
    && useradd --system --uid 10001 --gid app --no-create-home app \
    && mkdir -p /app/data \
    && chown -R app:app /app

COPY --from=build --chown=app:app /build/target/tia-zoca-1.0.0.jar /app/app.jar

ENV TZ=America/Sao_Paulo
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
