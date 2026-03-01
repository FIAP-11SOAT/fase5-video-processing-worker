resource "aws_cloudwatch_log_group" "app_logs" {
  name              = "/aws/eks/${data.aws_eks_cluster.cluster.name}/${var.project_name}"
  retention_in_days = 7
}
