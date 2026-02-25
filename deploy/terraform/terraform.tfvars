# Configuracao do Terraform para Video Processing Worker

# Nome do cluster EKS
cluster_name = "fase5-video-processing-infra-eks-cluster"

# Regiao AWS
aws_region = "us-east-1"

# Bucket S3 de entrada (videos originais)
s3_bucket_name = "fase5-videos-to-process"

# Bucket S3 de saida (frames processados)
s3_output_bucket_name = "fase5-processed-frames"

# Nome da fila SQS
sqs_queue_name = "fase5-video-processing-queue"

# Nome da tabela DynamoDB
dynamodb_table_name = "fase5-video-processing"

# Namespace Kubernetes
namespace = "fase5-video-processing-worker"

# Nome do Service Account
service_account_name = "video-processing-worker-sa"

# ARN do topico SNS para notificacoes (opcional)
# Descomente e preencha se usar SNS
# sns_topic_arn = "arn:aws:sns:us-east-1:814147156565:fase5-notifications"
