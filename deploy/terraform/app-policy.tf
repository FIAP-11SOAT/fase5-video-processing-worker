data "aws_iam_policy_document" "app_policy" {
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

  # Permissoes S3 - bucket de entrada (leitura dos videos originais)
  statement {
    sid    = "S3InputBucketPermissions"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:ListBucket"
    ]
    resources = [
      "arn:aws:s3:::${var.s3_bucket_name}",
      "arn:aws:s3:::${var.s3_bucket_name}/*"
    ]
  }

  # Permissoes S3 - bucket de saida (escrita dos frames processados)
  statement {
    sid    = "S3OutputBucketPermissions"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:ListBucket"
    ]
    resources = [
      "arn:aws:s3:::${var.s3_output_bucket_name}",
      "arn:aws:s3:::${var.s3_output_bucket_name}/*"
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

resource "aws_iam_role_policy" "app_policy" {
  name   = "${var.project_name}-policy"
  role   = aws_iam_role.app_role.id
  policy = data.aws_iam_policy_document.app_policy.json
}

