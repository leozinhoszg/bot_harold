# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cacheia dependencias antes de copiar o codigo (melhor cache de camadas).
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Usuario nao-root.
RUN useradd -r -u 1001 -m appuser

# Diretorio de dados do SQLite (montar volume para persistir entre reinicios).
RUN mkdir -p /app/data && chown -R appuser:appuser /app
VOLUME ["/app/data"]

COPY --from=build /build/target/api-bridge-bot-*.jar /app/app.jar

USER appuser

# Segredos e destino vem do ambiente (nunca embutidos na imagem):
#   TELEGRAM_BOT_TOKEN, TELEGRAM_DEFAULT_CHAT, REDIS_HOST, REDIS_PORT
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
