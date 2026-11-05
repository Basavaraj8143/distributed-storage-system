FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /data && chown -R 10001:10001 /app /data
COPY --from=build --chown=10001:10001 /workspace/target/*.jar /app/storage-node.jar
USER 10001:10001
EXPOSE 5001 5002 5003 5004 5005
ENTRYPOINT ["java", "-jar", "/app/storage-node.jar"]
