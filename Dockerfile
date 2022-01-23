FROM adoptopenjdk/openjdk16:alpine-slim
ARG JAR_FILE=target/*.jar
CMD mkdir /opencookbook
WORKDIR /opencookbook
COPY ${JAR_FILE} app.jar
EXPOSE 8080



ENTRYPOINT ["java","-XX:MaxRAM=150m", "-Xss512k","-XX:+UseSerialGC","-jar","app.jar"]
