data "aws_eks_cluster" "cluster" {
  name = local.aws_infra_secrets["EKS_CLUSTER_NAME"]
}

data "aws_eks_cluster_auth" "cluster" {
  name = local.aws_infra_secrets["EKS_CLUSTER_NAME"]
}

data "aws_iam_openid_connect_provider" "eks" {
  url = data.aws_eks_cluster.cluster.identity[0].oidc[0].issuer
}

locals {
  oidc_provider_arn = data.aws_iam_openid_connect_provider.eks.arn
  oidc_provider_url = replace(data.aws_eks_cluster.cluster.identity[0].oidc[0].issuer, "https://", "")
  account_id        = data.aws_caller_identity.current.account_id
}

