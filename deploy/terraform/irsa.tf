# IAM Policy Document para Trust Relationship (OIDC)
data "aws_iam_policy_document" "irsa_trust_policy" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_url}:sub"
      values   = ["system:serviceaccount:${var.namespace}:${var.service_account_name}"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

# IAM Role para o Service Account
resource "aws_iam_role" "video_processing_worker" {
  name               = "video-processing-worker-role"
  assume_role_policy = data.aws_iam_policy_document.irsa_trust_policy.json

  tags = {
    Name        = "video-processing-worker-role"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

# IAM Policy Document com permissoes necessarias
data "aws_iam_policy_document" "video_processing_worker_policy" {
  # Permissoes SQS
  statement {
    sid    = "SQSPermissions"
    effect = "Allow"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
      "sqs:ChangeMessageVisibility"
    ]
    resources = [
      "arn:aws:sqs:${var.aws_region}:${local.account_id}:${var.sqs_queue_name}"
    ]
  }

  # Permissoes S3 para leitura e escrita
  statement {
    sid    = "S3ReadWritePermissions"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:ListBucket"
    ]
    resources = [
      "arn:aws:s3:::${var.s3_bucket_name}",
      "arn:aws:s3:::${var.s3_bucket_name}/*"
    ]
  }

  # Permissoes DynamoDB
  statement {
    sid    = "DynamoDBPermissions"
    effect = "Allow"
    actions = [
      "dynamodb:PutItem",
      "dynamodb:GetItem",
      "dynamodb:UpdateItem",
      "dynamodb:Query",
      "dynamodb:Scan"
    ]
    resources = [
      "arn:aws:dynamodb:${var.aws_region}:${local.account_id}:table/${var.dynamodb_table_name}"
    ]
  }

  # Permissoes SNS (opcional, para notificacoes)
  dynamic "statement" {
    for_each = var.sns_topic_arn != "" ? [1] : []
    content {
      sid    = "SNSPermissions"
      effect = "Allow"
      actions = [
        "sns:Publish"
      ]
      resources = [var.sns_topic_arn]
    }
  }

  # Permissoes para logs do CloudWatch
  statement {
    sid    = "CloudWatchLogsPermissions"
    effect = "Allow"
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
    resources = [
      "arn:aws:logs:${var.aws_region}:${local.account_id}:log-group:/aws/eks/${var.cluster_name}/*"
    ]
  }
}

# IAM Policy
resource "aws_iam_policy" "video_processing_worker" {
  name        = "video-processing-worker-policy"
  description = "Policy para o video processing worker acessar SQS, S3, DynamoDB e SNS"
  policy      = data.aws_iam_policy_document.video_processing_worker_policy.json

  tags = {
    Name        = "video-processing-worker-policy"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

# Attachment da Policy na Role
resource "aws_iam_role_policy_attachment" "video_processing_worker" {
  role       = aws_iam_role.video_processing_worker.name
  policy_arn = aws_iam_policy.video_processing_worker.arn
}

# Kubernetes Service Account gerenciado via manifesto K8s (deploy/k8s/namespace.yml)
