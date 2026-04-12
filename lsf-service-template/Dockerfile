FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY . .

RUN set -eux; \
    mvn -B -ntp -pl lsf-service-template -am -DskipTests package; \
    cp "$(find lsf-service-template/target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1)" /workspace/lsf-service-template.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd --system lsf && useradd --system --gid lsf --uid 1001 --home /app lsf

COPY --from=build /workspace/lsf-service-template.jar /app/app.jar

USER lsf
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
