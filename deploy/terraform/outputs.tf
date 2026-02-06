output "service_account_name" {
  description = "Nome do Service Account criado"
  value       = kubernetes_service_account.video_processing_worker.metadata[0].name
}

output "service_account_namespace" {
  description = "Namespace do Service Account"
  value       = kubernetes_service_account.video_processing_worker.metadata[0].namespace
}

output "iam_role_arn" {
  description = "ARN da IAM Role criada para o Service Account"
  value       = aws_iam_role.video_processing_worker.arn
}

output "iam_role_name" {
  description = "Nome da IAM Role"
  value       = aws_iam_role.video_processing_worker.name
}

output "iam_policy_arn" {
  description = "ARN da IAM Policy"
  value       = aws_iam_policy.video_processing_worker.arn
}
