FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY core-service/pom.xml core-service/pom.xml

RUN mvn -B -pl gateway-service -am dependency:go-offline

COPY gateway-service/src gateway-service/src

RUN mvn -B -pl gateway-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --uid 10001 --home-dir /home/app --create-home app \
    && mkdir -p /app/logs \
    && chown -R app:app /app /home/app

COPY --from=build --chown=app:app \
    /workspace/gateway-service/target/gateway-service-*.jar \
    /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
