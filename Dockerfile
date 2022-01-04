FROM openjdk:11.0.13-jdk-slim
#FROM maven:3-jdk-11
ARG JAR_FILE=target/*.jar
CMD mkdir /opencookbook
WORKDIR /opencookbook
COPY ${JAR_FILE} app.jar
EXPOSE 8080



ENTRYPOINT ["java","-jar","app.jar"]
