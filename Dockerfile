FROM adoptopenjdk/openjdk16:alpine-slim
ARG JAR_FILE=target/*.jar
CMD mkdir /opencookbook
WORKDIR /opencookbook
COPY ${JAR_FILE} app.jar
EXPOSE 8080

ENTRYPOINT ["java",
    "-XX:MaxRAM=150m",
    "-Xss512k",
    "-XX:+UseSerialGC",
    "-jar","app.jar",
    "--opencookbook.smtpHost=${SMTP_HOST}",
    "--opencookbook.smtpPort=${SMTP_PORT}",
    "--opencookbook.smtpUsername=${SMTP_USERNAME}",
    "--opencookbook.smtpPassword=${SMTP_PASSWORD}",
    "--opencookbook.smtpProtocol=${SMTP_PROTOCOL}",
    "--opencookbook.smtpStartTLS=${SMTP_STARTTLS}",
    "--opencookbook.mailFrom=${MAIL_FROM}"
    ]
