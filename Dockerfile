FROM adoptopenjdk/openjdk16:alpine-slim
ARG JAR_FILE=target/*.jar
CMD mkdir /opencookbook
WORKDIR /opencookbook
COPY scripts/startApplication.sh .
COPY ${JAR_FILE} app.jar
EXPOSE 8080

ENTRYPOINT ["./startApplication.sh"]
