# Guia de Deploy com IRSA (IAM Roles for Service Accounts)

Este guia explica como configurar o acesso seguro do worker ao AWS (SQS, S3, DynamoDB) usando IRSA.

## Pre-requisitos

1. **Cluster EKS com OIDC Provider habilitado**
   ```bash
   # Verificar se OIDC provider existe
   aws eks describe-cluster --name seu-cluster --query "cluster.identity.oidc.issuer" --output text
   
   # Se nao existir, criar OIDC provider
   eksctl utils associate-iam-oidc-provider --cluster seu-cluster --approve
   ```

2. **Terraform instalado**
   - Download: https://www.terraform.io/downloads

3. **kubectl configurado com acesso ao cluster**
   ```bash
   aws eks update-kubeconfig --name seu-cluster --region us-east-1
   ```

4. **AWS CLI configurado com permissoes administrativas**

## Passo 1: Configurar variaveis do Terraform

Crie o arquivo `terraform.tfvars`:

```bash
cd deploy/terraform
cp terraform.tfvars.example terraform.tfvars
```

Edite `terraform.tfvars` com seus valores:

```hcl
cluster_name   = "seu-cluster-eks"
s3_bucket_name = "fase5-video-processing-bucket"
```

## Passo 2: Inicializar e aplicar Terraform

```bash
cd deploy/terraform

# Inicializar Terraform
terraform init

# Ver o plano de execucao
terraform plan

# Aplicar mudancas
terraform apply
```

Isso vai criar:
- ✅ IAM Role com trust policy OIDC
- ✅ IAM Policy com permissoes para SQS, S3, DynamoDB
- ✅ Service Account no Kubernetes com annotation da Role
- ✅ Attachment da policy na role

## Passo 3: Verificar recursos criados

```bash
# Ver outputs do Terraform
terraform output

# Verificar Service Account criado
kubectl get serviceaccount -n fase5-video-processing-worker

# Ver detalhes do Service Account (deve ter annotation eks.amazonaws.com/role-arn)
kubectl describe serviceaccount video-processing-worker-sa -n fase5-video-processing-worker
```

## Passo 4: Fazer deploy do worker

O deployment ja esta configurado para usar o Service Account. Aplique:

```bash
kubectl apply -f ../k8s/d.yml
```

## Passo 5: Verificar pods

```bash
# Ver pods
kubectl get pods -n fase5-video-processing-worker

# Ver logs
kubectl logs -f deployment/video-processing-worker -n fase5-video-processing-worker
```

## Como funciona o IRSA

1. **Service Account** no Kubernetes tem annotation com ARN da IAM Role
2. **Pod** usa o Service Account
3. **AWS SDK** automaticamente usa credenciais temporarias via OIDC
4. **EKS** troca o token do Service Account por credenciais AWS temporarias
5. **Worker** acessa SQS, S3, DynamoDB sem precisar de credenciais estaticas

## Permissoes configuradas

A IAM Policy criada permite:

### SQS
- ReceiveMessage
- DeleteMessage
- GetQueueAttributes
- GetQueueUrl
- ChangeMessageVisibility

### S3
- GetObject (baixar videos)
- PutObject (upload de frames/resultados)
- DeleteObject
- ListBucket

### DynamoDB
- PutItem
- GetItem
- UpdateItem
- Query
- Scan

### CloudWatch Logs
- CreateLogGroup
- CreateLogStream
- PutLogEvents

## Troubleshooting

### Erro: OIDC provider nao encontrado
```bash
eksctl utils associate-iam-oidc-provider --cluster seu-cluster --approve
```

### Erro: Service Account sem annotation
```bash
kubectl describe sa video-processing-worker-sa -n fase5-video-processing-worker
# Deve ter: eks.amazonaws.com/role-arn
```

### Erro: AccessDenied
Verifique se a trust policy esta correta:
```bash
aws iam get-role --role-name video-processing-worker-role --query 'Role.AssumeRolePolicyDocument'
```

### Pod nao consegue assumir role
Verifique os logs do pod:
```bash
kubectl logs <pod-name> -n fase5-video-processing-worker
```

## Limpeza

Para remover todos os recursos:

```bash
cd deploy/terraform
terraform destroy
```

## Boas praticas

✅ **Use IRSA** ao inves de credenciais estaticas
✅ **Principio do menor privilegio** - a policy so tem permissoes necessarias
✅ **Credenciais temporarias** - renovadas automaticamente
✅ **Sem secrets** no codigo ou repositorio
✅ **Auditavel** - todas as chamadas AWS registradas com a role

## Proximos passos

Depois de aplicar o Terraform e fazer deploy:

1. Enviar mensagens para a fila SQS
2. Monitorar logs do worker
3. Verificar frames processados no S3
4. Checar status no DynamoDB

## Links uteis

- [EKS IRSA Documentation](https://docs.aws.amazon.com/eks/latest/userguide/iam-roles-for-service-accounts.html)
- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [Terraform Kubernetes Provider](https://registry.terraform.io/providers/hashicorp/kubernetes/latest/docs)
