FROM eclipse-temurin:20-jre-alpine

# Curl for healthchecks
RUN apk update && apk add curl jq

HEALTHCHECK --interval=5m --timeout=3s \
  CMD curl -m 5 --silent --fail --request GET http://localhost:8080/actuator/health | jq --exit-status -n 'inputs | if has("status") then .status=="UP" else false end' > /dev/null || exit 1

ARG JAR_FILE=target/*.jar
CMD mkdir /opencookbook
WORKDIR /opencookbook
COPY scripts/startApplication.sh .
RUN chmod +x startApplication.sh

COPY ${JAR_FILE} app.jar
EXPOSE 8080

ENTRYPOINT ["/opencookbook/startApplication.sh"]
