#!/bin/bash

# Script para build e push da imagem Docker no AWS ECR
# Uso: ./scripts/build-and-push.sh [TAG]
# Exemplo: ./scripts/build-and-push.sh v1.0.0
# Se não informar a tag, será usada "latest"

set -e

# Configurações
PROJECT_NAME="fase5-video-processing-worker"
AWS_REGION="${AWS_REGION:-us-east-1}"
IMAGE_TAG="${1:-latest}"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  Build e Push para ECR${NC}"
echo -e "${YELLOW}========================================${NC}"

# Verificar se AWS CLI está instalado
if ! command -v aws &> /dev/null; then
    echo -e "${RED}Erro: AWS CLI não está instalado.${NC}"
    exit 1
fi

# Verificar se Docker está instalado
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Erro: Docker não está instalado.${NC}"
    exit 1
fi

# Obter Account ID da AWS
echo -e "${GREEN}Obtendo Account ID da AWS...${NC}"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

if [ -z "$AWS_ACCOUNT_ID" ]; then
    echo -e "${RED}Erro: Não foi possível obter o Account ID. Verifique suas credenciais AWS.${NC}"
    exit 1
fi

# Definir o repositório ECR
ECR_REPOSITORY="${PROJECT_NAME}-ecr"
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
FULL_IMAGE_NAME="${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}"

echo -e "${GREEN}Configurações:${NC}"
echo "  - AWS Region: ${AWS_REGION}"
echo "  - AWS Account ID: ${AWS_ACCOUNT_ID}"
echo "  - ECR Repository: ${ECR_REPOSITORY}"
echo "  - Image Tag: ${IMAGE_TAG}"
echo "  - Full Image Name: ${FULL_IMAGE_NAME}"
echo ""

# Autenticar no ECR
echo -e "${GREEN}Autenticando no ECR...${NC}"
aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}

if [ $? -ne 0 ]; then
    echo -e "${RED}Erro: Falha na autenticação com o ECR.${NC}"
    exit 1
fi

echo -e "${GREEN}Autenticação realizada com sucesso!${NC}"
echo ""

# Build da imagem Docker
echo -e "${GREEN}Construindo a imagem Docker...${NC}"
docker build -t ${ECR_REPOSITORY}:${IMAGE_TAG} .

if [ $? -ne 0 ]; then
    echo -e "${RED}Erro: Falha no build da imagem Docker.${NC}"
    exit 1
fi

echo -e "${GREEN}Build realizado com sucesso!${NC}"
echo ""

# Tag da imagem para o ECR
echo -e "${GREEN}Aplicando tag para o ECR...${NC}"
docker tag ${ECR_REPOSITORY}:${IMAGE_TAG} ${FULL_IMAGE_NAME}

# Push da imagem para o ECR
echo -e "${GREEN}Enviando imagem para o ECR...${NC}"
docker push ${FULL_IMAGE_NAME}

if [ $? -ne 0 ]; then
    echo -e "${RED}Erro: Falha no push da imagem para o ECR.${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Push realizado com sucesso!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Imagem disponível em: ${YELLOW}${FULL_IMAGE_NAME}${NC}"
echo ""

# Também fazer push da tag latest se a tag atual não for latest
if [ "${IMAGE_TAG}" != "latest" ]; then
    echo -e "${GREEN}Aplicando também a tag 'latest'...${NC}"
    docker tag ${ECR_REPOSITORY}:${IMAGE_TAG} ${ECR_REGISTRY}/${ECR_REPOSITORY}:latest
    docker push ${ECR_REGISTRY}/${ECR_REPOSITORY}:latest
    echo -e "${GREEN}Tag 'latest' também atualizada!${NC}"
fi

