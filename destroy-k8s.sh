#!/bin/bash

# Script para remover todos os recursos Kubernetes
# Uso: ./destroy-k8s.sh

set -e

# Diretório dos manifests K8s
K8S_DIR="deploy/k8s"
NAMESPACE="fase5-video-processing-worker"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Ordem de remoção dos manifests (dependentes primeiro, depois dependências)
MANIFESTS=(
    "deployment.yaml"
    "service-account.yaml"
    "namespace.yaml"
)

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  Removendo Deploy Kubernetes${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

# Verificar se kubectl está instalado
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}Erro: kubectl não está instalado.${NC}"
    exit 1
fi

# Verificar conexão com o cluster
echo -e "${YELLOW}Verificando conexão com o cluster...${NC}"
if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}Erro: Não foi possível conectar ao cluster Kubernetes.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Conectado ao cluster${NC}"
echo ""

# Remover recursos
for manifest in "${MANIFESTS[@]}"; do
    manifest_path="${K8S_DIR}/${manifest}"
    if [ -f "$manifest_path" ]; then
        echo -e "${RED}Removendo: ${manifest}${NC}"
        kubectl delete -f "$manifest_path" --ignore-not-found=true
    else
        echo -e "${YELLOW}Aviso: ${manifest} não encontrado, pulando...${NC}"
    fi
done

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Deploy removido com sucesso!${NC}"
echo -e "${GREEN}========================================${NC}"

