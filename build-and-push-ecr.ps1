# Script para build e push de imagem Docker para AWS ECR
# Windows PowerShell

# Configuracoes
$AWS_REGION = "us-east-1"
$AWS_ACCOUNT_ID = "814147156565"
$ECR_REPOSITORY_NAME = "fase5-video-processing-worker"
$IMAGE_TAG = "latest"

# Nome completo da imagem no ECR
$ECR_IMAGE_URI = "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/${ECR_REPOSITORY_NAME}:$IMAGE_TAG"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Build e Push para ECR - Video Processing Worker" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Verificar se o Docker esta rodando
Write-Host "[1/5] Verificando Docker..." -ForegroundColor Yellow
try {
    docker version | Out-Null
    Write-Host "Docker esta rodando" -ForegroundColor Green
} catch {
    Write-Host "Docker nao esta rodando. Inicie o Docker Desktop e tente novamente." -ForegroundColor Red
    exit 1
}

# Verificar credenciais AWS (o docker-credential-ecr-login cuida do login automaticamente no push)
Write-Host ""
Write-Host "[2/5] Verificando credenciais AWS para ECR..." -ForegroundColor Yellow
try {
    aws sts get-caller-identity --region $AWS_REGION | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Credenciais AWS invalidas ou expiradas"
    }
    Write-Host "Credenciais AWS validas - autenticacao ECR sera feita automaticamente pelo ecr-login helper" -ForegroundColor Green
} catch {
    Write-Host "Erro ao verificar credenciais AWS: $_" -ForegroundColor Red
    Write-Host "Certifique-se de que:" -ForegroundColor Yellow
    Write-Host "  - AWS CLI esta instalado e configurado (aws configure)" -ForegroundColor Yellow
    Write-Host "  - Voce tem permissoes para acessar o ECR" -ForegroundColor Yellow
    exit 1
}

# Build da imagem
Write-Host ""
Write-Host "[3/5] Construindo imagem Docker..." -ForegroundColor Yellow
Write-Host "Imagem: $ECR_IMAGE_URI" -ForegroundColor Cyan
try {
    docker build -t "${ECR_REPOSITORY_NAME}:${IMAGE_TAG}" .
    if ($LASTEXITCODE -ne 0) {
        throw "Erro ao construir imagem"
    }
    Write-Host "Imagem construida com sucesso" -ForegroundColor Green
} catch {
    Write-Host "Erro ao construir imagem: $_" -ForegroundColor Red
    exit 1
}

# Tag da imagem para ECR
Write-Host ""
Write-Host "[4/5] Criando tag para ECR..." -ForegroundColor Yellow
try {
    docker tag "${ECR_REPOSITORY_NAME}:${IMAGE_TAG}" $ECR_IMAGE_URI
    if ($LASTEXITCODE -ne 0) {
        throw "Erro ao criar tag"
    }
    Write-Host "Tag criada com sucesso" -ForegroundColor Green
} catch {
    Write-Host "Erro ao criar tag: $_" -ForegroundColor Red
    exit 1
}

# Push da imagem para ECR
Write-Host ""
Write-Host "[5/5] Enviando imagem para ECR..." -ForegroundColor Yellow
try {
    docker push $ECR_IMAGE_URI
    if ($LASTEXITCODE -ne 0) {
        throw "Erro ao fazer push"
    }
    Write-Host "Imagem enviada com sucesso para o ECR" -ForegroundColor Green
} catch {
    Write-Host "Erro ao fazer push da imagem: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "PROCESSO CONCLUIDO COM SUCESSO!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Imagem disponivel em:" -ForegroundColor White
Write-Host $ECR_IMAGE_URI -ForegroundColor Cyan
Write-Host ""
Write-Host "Para usar no Kubernetes, atualize o deployment com:" -ForegroundColor White
Write-Host "  image: $ECR_IMAGE_URI" -ForegroundColor Cyan
Write-Host ""
