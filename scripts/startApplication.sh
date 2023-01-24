#!/bin/sh

java \
  -XX:MaxRAM=300m \
  -Xss512k \
  -XX:+UseSerialGC \
  -jar app.jar \
  --opencookbook.smtpHost="${SMTP_HOST}" \
  --opencookbook.smtpPort="${SMTP_PORT}" \
  --opencookbook.smtpUsername="${SMTP_USERNAME}" \
  --opencookbook.smtpPassword="${SMTP_PASSWORD}" \
  --opencookbook.smtpProtocol="${SMTP_PROTOCOL}" \
  --opencookbook.smtpStartTLS="${SMTP_STARTTLS}" \
  --opencookbook.mailFrom="${MAIL_FROM}" \
  --opencookbook.instanceURL="${INSTANCE_URL}" \
  --opencookbook.termsOfServiceFileLocation="${TERMS_OF_SERVICE_FILE_LOCATION}" \
  --spring.datasource.password="${DB_PASSWORD}" \
  --spring.datasource.username="${DB_USERNAME}" \
  --spring.datasource.uri="${DB_URI}"
