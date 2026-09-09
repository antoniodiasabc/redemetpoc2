FROM eclipse-temurin:21-jdk

RUN apt-get update && apt-get install -y \
    libopencv-dev curl \
    && rm -rf /var/lib/apt/lists/*

# Importar certificado do satelite.cptec.inpe.br no truststore da JVM
COPY cptec_chain.pem /tmp/cptec_chain.pem
RUN keytool -import -noprompt -trustcacerts -alias cptec \
    -file /tmp/cptec_chain.pem \
    -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit 2>/dev/null || true
COPY satelite_cptec.pem /tmp/satelite_cptec.pem
RUN keytool -import -noprompt -trustcacerts -alias satelite_cptec \
    -file /tmp/satelite_cptec.pem \
    -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit 2>/dev/null || true

WORKDIR /app
COPY target/pocsigmet-spring-final-1.0.0.jar app.jar
COPY src/main/resources/static ./src/main/resources/static

RUN mkdir -p /app/data /app/logs

ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+UseStringDeduplication -XX:MaxDirectMemorySize=64m"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8082/health || exit 1

EXPOSE 8082

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.library.path=libs -jar app.jar"]
