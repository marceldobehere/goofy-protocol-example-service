# Build
FROM maven:3.9.16-amazoncorretto-25 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src

# Build the fat jar (Spring Boot plugin usually creates it)
RUN mvn -q -DskipTests clean package


# Runtime
FROM amazoncorretto:25
RUN yum -y update && yum -y install openssl && yum clean all
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar

# Profile (override in docker-compose)
ENV SPRING_PROFILES_ACTIVE=dev

# Cert & Run
ENTRYPOINT ["/bin/sh","-c", "set -e; \
   : \"${CERT_DOMAIN:?Set CERT_DOMAIN (e.g. example.com)}\"; \
   : \"${KEYSTORE_PASSWORD:?Set KEYSTORE_PASSWORD}\"; \
   CERT_DIR=/etc/letsencrypt/live/${CERT_DOMAIN}; \
   if [ ! -f \"$CERT_DIR/fullchain.pem\" ] || [ ! -f \"$CERT_DIR/privkey.pem\" ]; then \
     echo \"Missing certs in $CERT_DIR\"; exit 1; \
   fi; \
   KEYSTORE_PATH=/app/keystore.p12; \
   openssl pkcs12 -export \
     -in \"$CERT_DIR/fullchain.pem\" \
     -inkey \"$CERT_DIR/privkey.pem\" \
     -out \"$KEYSTORE_PATH\" \
     -name \"spring\" \
     -password pass:$KEYSTORE_PASSWORD; \
   exec java -jar /app/app.jar \
     --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-dev} \
     --server.http2.enabled=true \
     --server.ssl.enabled=true \
     --server.ssl.key-store=${KEYSTORE_PATH} \
     --server.ssl.key-store-password=${KEYSTORE_PASSWORD} \
     --server.ssl.key-store-type=PKCS12"]