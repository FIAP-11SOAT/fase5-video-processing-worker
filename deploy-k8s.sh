#!/bin/bash

# Script para aplicar todos os manifests Kubernetes
# Uso: ./deploy-k8s.sh [apply|delete|status]
# Exemplo: ./deploy-k8s.sh apply

set -e

# Diretório dos manifests K8s
K8S_DIR="deploy/k8s"
NAMESPACE="fase5-video-processing-worker"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Ordem de aplicação dos manifests (dependências primeiro)
MANIFESTS=(
    "namespace.yaml"
    "service-account.yaml"
    "deployment.yaml"
)

# Função para exibir ajuda
show_help() {
    echo -e "${YELLOW}Uso: ./deploy-k8s.sh [comando]${NC}"
    echo ""
    echo "Comandos disponíveis:"
    echo "  apply   - Aplica todos os manifests no cluster"
    echo "  delete  - Remove todos os recursos do cluster"
    echo "  status  - Mostra o status dos recursos no namespace"
    echo "  help    - Exibe esta mensagem de ajuda"
    echo ""
}

# Função para verificar se kubectl está instalado
check_kubectl() {
    if ! command -v kubectl &> /dev/null; then
        echo -e "${RED}Erro: kubectl não está instalado.${NC}"
        exit 1
    fi
}

# Função para verificar conexão com o cluster
check_cluster() {
    echo -e "${BLUE}Verificando conexão com o cluster...${NC}"
    if ! kubectl cluster-info &> /dev/null; then
        echo -e "${RED}Erro: Não foi possível conectar ao cluster Kubernetes.${NC}"
        echo -e "${YELLOW}Verifique se você está conectado ao cluster correto.${NC}"
        exit 1
    fi
    echo -e "${GREEN}Conectado ao cluster com sucesso!${NC}"
    echo ""
}

# Função para aplicar os manifests
apply_manifests() {
    echo -e "${YELLOW}========================================${NC}"
    echo -e "${YELLOW}  Aplicando Manifests Kubernetes${NC}"
    echo -e "${YELLOW}========================================${NC}"
    echo ""

    for manifest in "${MANIFESTS[@]}"; do
        manifest_path="${K8S_DIR}/${manifest}"
        if [ -f "$manifest_path" ]; then
            echo -e "${GREEN}Aplicando: ${manifest}${NC}"
            kubectl apply -f "$manifest_path"
        else
            echo -e "${YELLOW}Aviso: ${manifest} não encontrado, pulando...${NC}"
        fi
    done

    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  Deploy concluído com sucesso!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""

    show_status
}

# Função para deletar os recursos
delete_manifests() {
    echo -e "${YELLOW}========================================${NC}"
    echo -e "${YELLOW}  Removendo Recursos Kubernetes${NC}"
    echo -e "${YELLOW}========================================${NC}"
    echo ""

    # Deletar na ordem inversa (dependentes primeiro)
    for (( i=${#MANIFESTS[@]}-1; i>=0; i-- )); do
        manifest="${MANIFESTS[$i]}"
        manifest_path="${K8S_DIR}/${manifest}"
        if [ -f "$manifest_path" ]; then
            echo -e "${RED}Removendo: ${manifest}${NC}"
            kubectl delete -f "$manifest_path" --ignore-not-found=true
        fi
    done

    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  Recursos removidos com sucesso!${NC}"
    echo -e "${GREEN}========================================${NC}"
}

# Função para mostrar status dos recursos
show_status() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  Status dos Recursos${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""

    echo -e "${YELLOW}Namespace:${NC}"
    kubectl get namespace ${NAMESPACE} 2>/dev/null || echo "Namespace não encontrado"
    echo ""

    echo -e "${YELLOW}Pods:${NC}"
    kubectl get pods -n ${NAMESPACE} 2>/dev/null || echo "Nenhum pod encontrado"
    echo ""

    echo -e "${YELLOW}Services:${NC}"
    kubectl get services -n ${NAMESPACE} 2>/dev/null || echo "Nenhum service encontrado"
    echo ""

    echo -e "${YELLOW}Deployments:${NC}"
    kubectl get deployments -n ${NAMESPACE} 2>/dev/null || echo "Nenhum deployment encontrado"
    echo ""

    echo -e "${YELLOW}Ingress:${NC}"
    kubectl get ingress -n ${NAMESPACE} 2>/dev/null || echo "Nenhum ingress encontrado"
    echo ""
}

# Verificar pré-requisitos
check_kubectl

# Processar comando
COMMAND="${1:-apply}"

case "$COMMAND" in
    apply)
        check_cluster
        apply_manifests
        ;;
    delete)
        check_cluster
        delete_manifests
        ;;
    status)
        check_cluster
        show_status
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        echo -e "${RED}Comando inválido: ${COMMAND}${NC}"
        echo ""
        show_help
        exit 1
        ;;
esac

