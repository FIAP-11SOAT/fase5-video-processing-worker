provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Team      = "mfa"
      Project   = var.project_name
      Terraform = "true"
    }
  }
}
