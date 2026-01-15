# Multi-stage build para otimização de tamanho e segurança

# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copiar arquivos do Maven primeiro para cache de dependências
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código fonte e compilar
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Instalar FFmpeg
RUN apk add --no-cache ffmpeg

# Criar usuário não-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copiar JAR da stage de build
COPY --from=builder /app/target/*.jar app.jar

# Criar diretórios necessários
RUN mkdir -p /app/temp /app/logs && \
    chown -R appuser:appgroup /app

# Trocar para usuário não-root
USER appuser

# Expor porta
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/videos/health || exit 1

# Executar aplicação
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
