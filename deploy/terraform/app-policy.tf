data "aws_sqs_queue" "video_notification_queue" {
  name = "fase5-video-notification-queue"
}

data "aws_sqs_queue" "video_processing_queue" {
  name = "fase5-video-processing-queue"
}

data "aws_s3_bucket" "videos_to_process" {
  bucket = "fase5-videos-to-process"
}

data "aws_s3_bucket" "processed_frames" {
  bucket = "fase5-processed-frames"
}

data "aws_dynamodb_table" "video_processing_table" {
  name = "fase5-video-processing"
}

data "aws_iam_policy_document" "app_policy" {

  statement {
    effect = "Allow"
    actions = [
      "sqs:SendMessage",
      "sqs:GetQueueUrl",
      "sqs:GetQueueAttributes"
    ]
    resources = [
      data.aws_sqs_queue.video_notification_queue.arn
    ]
  }

  statement {
    sid    = "SQSPermissions"
    effect = "Allow"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:ChangeMessageVisibility",
      "sqs:GetQueueUrl",
      "sqs:GetQueueAttributes"
    ]
    resources = [
      data.aws_sqs_queue.video_processing_queue.arn
    ]
  }

  statement {
    sid    = "S3InputBucketPermissions"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:ListBucket"
    ]
    resources = [
      data.aws_s3_bucket.videos_to_process.arn,
      "${data.aws_s3_bucket.videos_to_process.arn}/*"
    ]
  }

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
      data.aws_s3_bucket.processed_frames.arn,
      "${data.aws_s3_bucket.processed_frames.arn}/*"
    ]
  }

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
      data.aws_dynamodb_table.video_processing_table.arn,
      "${data.aws_dynamodb_table.video_processing_table.arn}/index/*"
    ]
  }
}

resource "aws_iam_role_policy" "app_policy" {
  name   = "${var.project_name}-policy"
  role   = aws_iam_role.app_role.id
  policy = data.aws_iam_policy_document.app_policy.json
}
