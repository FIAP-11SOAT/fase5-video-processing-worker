# Video Processing Worker - FIAP Fase 5

Worker para processar vídeos armazenados no AWS S3, extraindo frames usando FFmpeg e salvando o resultado de volta no S3.

## 🏗️ Arquitetura

Este projeto implementa **Arquitetura Hexagonal (Ports and Adapters)** com as seguintes camadas:

### Domain (Núcleo)
- **Entidades**: `Video`, `Frame`, `ProcessingResult`
- **Portas de Entrada**: `ProcessVideoUseCase`
- **Portas de Saída**: `VideoStoragePort`, `VideoProcessorPort`, `FileCompressionPort`
- **Exceções**: `DomainException`, `VideoProcessingException`

### Application (Casos de Uso)
- **Serviços**: `ProcessVideoService` - orquestra o processamento de vídeos

### Infrastructure (Adapters)
- **Storage**: `S3StorageAdapter` - integração com AWS S3
- **Processor**: `FFmpegVideoProcessorAdapter` - extração de frames com FFmpeg
- **Compression**: `ZipFileCompressionAdapter` - compressão de frames em ZIP
- **REST**: `VideoProcessingController` - API REST
- **Config**: Configurações do Spring Boot e AWS

## 🚀 Tecnologias

- **Java 21**
- **Spring Boot 3.2.1**
- **AWS SDK S3**
- **FFmpeg** (para extração de frames)
- **Maven**
- **Docker**

## 📋 Pré-requisitos

- Java 21
- Maven 3.6+
- FFmpeg instalado no sistema
- Credenciais AWS configuradas

## ⚙️ Configuração

### application.properties

```properties
# AWS Configuration
aws.region=us-east-1
aws.access-key-id=YOUR_ACCESS_KEY
aws.secret-access-key=YOUR_SECRET_KEY

# Video Processing Configuration
video.processing.fps=1
video.processing.temp-dir=./temp

# S3 Configuration
s3.bucket.name=fiap-videos
```

### Variáveis de Ambiente (alternativa)

```bash
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=your_access_key
export AWS_SECRET_ACCESS_KEY=your_secret_key
```

## 🔧 Instalação

### Usando Maven

```bash
# Compilar o projeto
mvn clean package

# Executar
java -jar target/video-processing-worker-1.0.0.jar
```

### Usando Docker

```bash
# Build da imagem
docker build -t video-processing-worker .

# Executar container
docker run -p 8080:8080 \
  -e AWS_REGION=us-east-1 \
  -e AWS_ACCESS_KEY_ID=your_key \
  -e AWS_SECRET_ACCESS_KEY=your_secret \
  video-processing-worker
```

## 📖 API

### Processar Vídeo

**POST** `/api/videos/process`

```json
{
  "videoId": "video-123",
  "s3Key": "videos/input/meu-video.mp4",
  "bucket": "fiap-videos"
}
```

**Response:**

```json
{
  "videoId": "video-123",
  "success": true,
  "message": "Processamento concluído! 120 frames extraídos.",
  "frameCount": 120,
  "zipS3Key": "processed/video-123/frames_video-123.zip",
  "processingTimeMs": 45000
}
```

### Health Check

**GET** `/api/videos/health`

```json
{
  "status": "UP",
  "service": "Video Processing Worker"
}
```

## 🎯 Fluxo de Processamento

1. **Download**: Baixa o vídeo do S3 para diretório temporário
2. **Extração**: Usa FFmpeg para extrair frames (1 frame por segundo por padrão)
3. **Compressão**: Comprime todos os frames em um arquivo ZIP
4. **Upload**: Faz upload do ZIP para o S3
5. **Limpeza**: Remove arquivos temporários

## 🏗️ Estrutura do Projeto

```
src/
├── main/
│   ├── java/com/fiap/videoprocessor/
│   │   ├── domain/
│   │   │   ├── model/              # Entidades de domínio
│   │   │   ├── ports/
│   │   │   │   ├── input/          # Casos de uso
│   │   │   │   └── output/         # Interfaces de saída
│   │   │   └── exception/          # Exceções de domínio
│   │   ├── application/
│   │   │   └── service/            # Implementação dos casos de uso
│   │   └── infrastructure/
│   │       ├── adapter/
│   │       │   ├── storage/        # S3 Adapter
│   │       │   ├── processor/      # FFmpeg Adapter
│   │       │   ├── compression/    # ZIP Adapter
│   │       │   └── rest/           # REST Controllers
│   │       └── config/             # Configurações
│   └── resources/
│       └── application.properties
└── test/
    └── java/com/fiap/videoprocessor/
```

## 🧪 Testes

```bash
# Executar testes
mvn test

# Executar com cobertura
mvn test jacoco:report
```

## 📝 Exemplos de Uso

### Usando cURL

```bash
curl -X POST http://localhost:8080/api/videos/process \
  -H "Content-Type: application/json" \
  -d '{
    "videoId": "video-001",
    "s3Key": "videos/input/sample.mp4",
    "bucket": "my-bucket"
  }'
```

### Usando Java SDK

```java
RestTemplate restTemplate = new RestTemplate();
ProcessVideoRequest request = new ProcessVideoRequest(
    "video-001",
    "videos/input/sample.mp4",
    "my-bucket"
);

ProcessingResponse response = restTemplate.postForObject(
    "http://localhost:8080/api/videos/process",
    request,
    ProcessingResponse.class
);
```

## 🔍 Monitoramento

- **Logs**: Disponíveis em `logs/application.log`
- **Health Check**: `GET /api/videos/health`
- **Métricas**: Integração com Spring Actuator (opcional)

## 🐳 Docker Compose

```yaml
version: '3.8'

services:
  video-processor:
    build: .
    ports:
      - "8080:8080"
    environment:
      - AWS_REGION=us-east-1
      - AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
      - AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
    volumes:
      - ./temp:/app/temp
      - ./logs:/app/logs
```

## 🛠️ Troubleshooting

### FFmpeg não encontrado
```bash
# Ubuntu/Debian
sudo apt-get install ffmpeg

# macOS
brew install ffmpeg

# Alpine (Docker)
apk add ffmpeg
```

### Erro de credenciais AWS
- Verifique se as credenciais estão configuradas corretamente
- Use `aws configure` ou defina variáveis de ambiente
- Para EC2/ECS, use IAM Roles

## 📚 Referências

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [AWS SDK for Java](https://aws.amazon.com/sdk-for-java/)
- [FFmpeg Documentation](https://ffmpeg.org/documentation.html)
- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)

## 👥 Autores

FIAP - Pós-graduação Fase 5

## 📄 Licença

Este projeto é licenciado sob a MIT License.