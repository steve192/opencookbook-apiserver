FROM eclipse-temurin:19-jre-alpine
ARG JAR_FILE=target/*.jar
CMD mkdir /opencookbook
WORKDIR /opencookbook
COPY scripts/startApplication.sh .
RUN chmod +x startApplication.sh

COPY ${JAR_FILE} app.jar
EXPOSE 8080

ENTRYPOINT ["/opencookbook/startApplication.sh"]
