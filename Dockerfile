FROM adoptopenjdk/openjdk16:alpine-slim
ARG JAR_FILE=target/*.jar
CMD mkdir /opencookbook
WORKDIR /opencookbook
COPY --chmod=755 scripts/startApplication.sh .
COPY ${JAR_FILE} app.jar
EXPOSE 8080

ENTRYPOINT ["/opencookbook/startApplication.sh"]
