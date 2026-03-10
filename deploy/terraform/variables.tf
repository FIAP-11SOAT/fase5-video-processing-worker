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

variable "aws_region" {
  description = "The AWS region to deploy resources in"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "The name of the project"
  type        = string
  default     = "fase5-video-processing-worker"
}