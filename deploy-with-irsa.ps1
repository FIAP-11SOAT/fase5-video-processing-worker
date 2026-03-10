# Script para deploy com IRSA (IAM Roles for Service Accounts)
# Windows PowerShell

param(
    [Parameter(Mandatory=$true)]
    [string]$ClusterName,
    
    [Parameter(Mandatory=$true)]
    [string]$S3BucketName,
    
    [string]$Region = "us-east-1"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Deploy Video Processing Worker com IRSA" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Verificar pre-requisitos
Write-Host "[1/6] Verificando pre-requisitos..." -ForegroundColor Yellow

# Verificar AWS CLI
try {
    aws --version | Out-Null
    Write-Host "  AWS CLI instalado" -ForegroundColor Green
} catch {
    Write-Host "  AWS CLI nao encontrado. Instale em: https://aws.amazon.com/cli/" -ForegroundColor Red
    exit 1
}

# Verificar Terraform
try {
    terraform version | Out-Null
    Write-Host "  Terraform instalado" -ForegroundColor Green
} catch {
    Write-Host "  Terraform nao encontrado. Instale em: https://www.terraform.io/downloads" -ForegroundColor Red
    exit 1
}

# Verificar kubectl
try {
    kubectl version --client | Out-Null
    Write-Host "  kubectl instalado" -ForegroundColor Green
} catch {
    Write-Host "  kubectl nao encontrado" -ForegroundColor Red
    exit 1
}

# Configurar kubeconfig
Write-Host ""
Write-Host "[2/6] Configurando acesso ao cluster EKS..." -ForegroundColor Yellow
try {
    aws eks update-kubeconfig --name $ClusterName --region $Region
    Write-Host "  Kubeconfig atualizado" -ForegroundColor Green
} catch {
    Write-Host "  Erro ao configurar kubeconfig: $_" -ForegroundColor Red
    exit 1
}

# Criar namespace se nao existir
Write-Host ""
Write-Host "[3/6] Verificando namespace..." -ForegroundColor Yellow
$namespaceExists = kubectl get namespace fase5-video-processing-worker 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "  Criando namespace fase5-video-processing-worker..." -ForegroundColor Yellow
    kubectl create namespace fase5-video-processing-worker
    Write-Host "  Namespace criado" -ForegroundColor Green
} else {
    Write-Host "  Namespace ja existe" -ForegroundColor Green
}

# Criar terraform.tfvars
Write-Host ""
Write-Host "[4/6] Configurando Terraform..." -ForegroundColor Yellow
$tfvarsContent = @"
cluster_name   = "$ClusterName"
s3_bucket_name = "$S3BucketName"
aws_region     = "$Region"
"@

Set-Content -Path "deploy\terraform\terraform.tfvars" -Value $tfvarsContent
Write-Host "  terraform.tfvars criado" -ForegroundColor Green

# Aplicar Terraform
Write-Host ""
Write-Host "[5/6] Aplicando Terraform (IRSA)..." -ForegroundColor Yellow
Push-Location deploy\terraform

try {
    # Init
    Write-Host "  Inicializando Terraform..." -ForegroundColor Cyan
    terraform init
    
    if ($LASTEXITCODE -ne 0) {
        throw "Erro no terraform init"
    }
    
    # Plan
    Write-Host ""
    Write-Host "  Gerando plano de execucao..." -ForegroundColor Cyan
    terraform plan -out=tfplan
    
    if ($LASTEXITCODE -ne 0) {
        throw "Erro no terraform plan"
    }
    
    # Apply
    Write-Host ""
    Write-Host "  Aplicando mudancas..." -ForegroundColor Cyan
    terraform apply tfplan
    
    if ($LASTEXITCODE -ne 0) {
        throw "Erro no terraform apply"
    }
    
    Write-Host ""
    Write-Host "  Terraform aplicado com sucesso" -ForegroundColor Green
    
    # Outputs
    Write-Host ""
    Write-Host "  Recursos criados:" -ForegroundColor Cyan
    terraform output
    
} catch {
    Write-Host "  Erro no Terraform: $_" -ForegroundColor Red
    Pop-Location
    exit 1
}

Pop-Location

# Aguardar Service Account ser criado
Write-Host ""
Write-Host "  Aguardando Service Account ser criado..." -ForegroundColor Cyan
Start-Sleep -Seconds 5

# Deploy do worker
Write-Host ""
Write-Host "[6/6] Fazendo deploy do worker..." -ForegroundColor Yellow
try {
    kubectl apply -f deploy\k8s\d.yml
    
    if ($LASTEXITCODE -ne 0) {
        throw "Erro ao aplicar deployment"
    }
    
    Write-Host "  Deployment aplicado" -ForegroundColor Green
} catch {
    Write-Host "  Erro no deploy: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "DEPLOY CONCLUIDO COM SUCESSO!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Comandos uteis:" -ForegroundColor White
Write-Host ""
Write-Host "  # Ver pods" -ForegroundColor Cyan
Write-Host "  kubectl get pods -n fase5-video-processing-worker" -ForegroundColor Gray
Write-Host ""
Write-Host "  # Ver logs" -ForegroundColor Cyan
Write-Host "  kubectl logs -f deployment/video-processing-worker -n fase5-video-processing-worker" -ForegroundColor Gray
Write-Host ""
Write-Host "  # Ver Service Account" -ForegroundColor Cyan
Write-Host "  kubectl describe sa video-processing-worker-sa -n fase5-video-processing-worker" -ForegroundColor Gray
Write-Host ""
Write-Host "  # Ver IAM Role assumida pelo pod" -ForegroundColor Cyan
Write-Host "  kubectl exec -it deployment/video-processing-worker -n fase5-video-processing-worker -- env | grep AWS" -ForegroundColor Gray
Write-Host ""
