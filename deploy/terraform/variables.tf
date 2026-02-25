variable "aws_region" {
  description = "AWS Region"
  type        = string
  default     = "us-east-1"
}

variable "cluster_name" {
  description = "Nome do cluster EKS"
  type        = string
}

variable "namespace" {
  description = "Namespace do Kubernetes"
  type        = string
  default     = "fase5-video-processing-worker"
}

variable "service_account_name" {
  description = "Nome do Service Account"
  type        = string
  default     = "video-processing-worker-sa"
}

variable "sqs_queue_name" {
  description = "Nome da fila SQS"
  type        = string
  default     = "fase5-video-processing-queue"
}

variable "s3_bucket_name" {
  description = "Nome do bucket S3 de entrada (videos originais)"
  type        = string
}

variable "s3_output_bucket_name" {
  description = "Nome do bucket S3 de saida (frames processados)"
  type        = string
  default     = "fase5-processed-frames"
}

variable "dynamodb_table_name" {
  description = "Nome da tabela DynamoDB"
  type        = string
  default     = "fase5-video-status"
}

variable "sns_topic_arn" {
  description = "ARN do topico SNS para notificacoes (opcional)"
  type        = string
  default     = ""
}


data "aws_region" "current" {}
data "aws_caller_identity" "current" {}
data "aws_ecr_authorization_token" "ecr_auth" {}

# Fetching master secrets
data "aws_secretsmanager_secret" "master_secrets" {
  name = "terraform-master-credentials"
}
data "aws_secretsmanager_secret_version" "master_secrets" {
  secret_id = data.aws_secretsmanager_secret.master_secrets.id
}

# Fetching infrastructure-specific secrets
data "aws_secretsmanager_secret" "infra_secrets" {
  name = "fase5-video-processing-infra-secrets"
}
data "aws_secretsmanager_secret_version" "infra_secrets" {
  secret_id = data.aws_secretsmanager_secret.infra_secrets.id
}

# Decoding secrets and processing ECR endpoint
locals {
  aws_infra_secrets           = jsondecode(data.aws_secretsmanager_secret_version.infra_secrets.secret_string)
  aws_master_secrets          = jsondecode(data.aws_secretsmanager_secret_version.master_secrets.secret_string)
  aws_ecr_auth_proxy_endpoint = replace(data.aws_ecr_authorization_token.ecr_auth.proxy_endpoint, "https://", "")
}

variable "project_name" {
  description = "The name of the project"
  type        = string
  default     = "fase5-video-processing-worker"
}