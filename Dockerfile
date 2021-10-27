#FROM openjdk:11-jdk-slim
FROM maven:3-jdk-11
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
COPY target/* .
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
