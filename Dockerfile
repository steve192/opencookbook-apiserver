FROM openjdk:11-jdk-slim
#FROM maven:3-jdk-11
ARG JAR_FILE=target/*.jar
CMD mkdir /opencookbook
WORKDIR /opencookbook
COPY ${JAR_FILE} app.jar
EXPOSE 8080

# Secret used to generate session tokens
ENV JWT_SECRET=default-jwt-secret

ENTRYPOINT ["java","-jar","app.jar", "--opencookbook.jwtSecret=$JWT_SECRET"]
