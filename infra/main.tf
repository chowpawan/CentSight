// CentSight on AWS: App Runner (container + HTTPS URL) -> RDS Postgres, both inside a private VPC.
// App Runner is used instead of ECS + ALB because it gives a TLS URL with no load balancer to pay for.

data "aws_availability_zones" "available" {
  state = "available"
}

// ---------------------------------------------------------------- network

resource "aws_vpc" "main" {
  cidr_block           = "10.20.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = var.name }
}

// Private subnets only: the database is never reachable from the internet,
// and App Runner reaches it through a VPC connector.
resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(aws_vpc.main.cidr_block, 8, count.index)
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = { Name = "${var.name}-private-${count.index}" }
}

resource "aws_security_group" "app" {
  name        = "${var.name}-app"
  description = "App Runner VPC connector"
  vpc_id      = aws_vpc.main.id

  egress {
    description = "Outbound to Plaid, Google and the database"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "db" {
  name        = "${var.name}-db"
  description = "Postgres, reachable only from the app"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Postgres from the app only"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }
}

// ---------------------------------------------------------------- database

resource "random_password" "db" {
  length  = 32
  special = false // keeps the password safe to drop into a JDBC URL
}

resource "aws_db_subnet_group" "main" {
  name       = "${var.name}-db"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "main" {
  identifier     = "${var.name}-db"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  db_name  = "centsight"
  username = "centsight"
  password = random_password.db.result

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false

  backup_retention_period   = 7
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.name}-final-${formatdate("YYYYMMDDhhmmss", timestamp())}"
  deletion_protection       = true

  auto_minor_version_upgrade = true
  apply_immediately          = true

  lifecycle {
    ignore_changes = [final_snapshot_identifier]
  }
}

// ---------------------------------------------------------------- secrets

// Losing this key means every bank has to be reconnected, so it lives in Secrets Manager, not in code.
resource "random_id" "encryption_key" {
  byte_length = 32
}

resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "app" {
  name                    = "${var.name}/app"
  description             = "CentSight runtime secrets"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id

  secret_string = jsonencode({
    DATABASE_PASSWORD    = random_password.db.result
    JWT_SECRET           = random_password.jwt.result
    ENCRYPTION_KEY       = random_id.encryption_key.hex
    PLAID_SECRET         = var.plaid_secret
    GOOGLE_CLIENT_SECRET = var.google_client_secret
  })
}

// ---------------------------------------------------------------- image registry

resource "aws_ecr_repository" "app" {
  name                 = var.name
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 10 images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 10 }
      action       = { type = "expire" }
    }]
  })
}

// ---------------------------------------------------------------- iam

data "aws_iam_policy_document" "apprunner_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["build.apprunner.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "apprunner_ecr" {
  name               = "${var.name}-apprunner-ecr"
  assume_role_policy = data.aws_iam_policy_document.apprunner_assume.json
}

resource "aws_iam_role_policy_attachment" "apprunner_ecr" {
  role       = aws_iam_role.apprunner_ecr.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSAppRunnerServicePolicyForECRAccess"
}

data "aws_iam_policy_document" "task_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["tasks.apprunner.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "task" {
  name               = "${var.name}-task"
  assume_role_policy = data.aws_iam_policy_document.task_assume.json
}

// The running container may read exactly one secret, and nothing else.
data "aws_iam_policy_document" "read_secret" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.app.arn]
  }
}

resource "aws_iam_role_policy" "task_secrets" {
  name   = "${var.name}-read-secrets"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.read_secret.json
}

// ---------------------------------------------------------------- app runner

resource "aws_apprunner_vpc_connector" "main" {
  vpc_connector_name = var.name
  subnets            = aws_subnet.private[*].id
  security_groups    = [aws_security_group.app.id]
}

resource "aws_apprunner_service" "main" {
  service_name = var.name

  source_configuration {
    authentication_configuration {
      access_role_arn = aws_iam_role.apprunner_ecr.arn
    }

    auto_deployments_enabled = false

    image_repository {
      image_identifier      = "${aws_ecr_repository.app.repository_url}:${var.image_tag}"
      image_repository_type = "ECR"

      image_configuration {
        port = "8080"

        runtime_environment_variables = {
          DATABASE_URL     = "jdbc:postgresql://${aws_db_instance.main.endpoint}/centsight"
          DATABASE_USER    = aws_db_instance.main.username
          PLAID_CLIENT_ID  = var.plaid_client_id
          PLAID_ENV        = var.plaid_env
          GOOGLE_CLIENT_ID = var.google_client_id
          ALLOWED_EMAILS   = var.allowed_emails
          // APP_URL and CORS_ORIGINS are deliberately unset: the container serves the app and the
          // API on one origin, so the login redirect uses the request's own origin and nothing is
          // cross-origin. That also avoids a circular reference on this service's URL.
        }

        runtime_environment_secrets = {
          DATABASE_PASSWORD    = "${aws_secretsmanager_secret.app.arn}:DATABASE_PASSWORD::"
          JWT_SECRET           = "${aws_secretsmanager_secret.app.arn}:JWT_SECRET::"
          ENCRYPTION_KEY       = "${aws_secretsmanager_secret.app.arn}:ENCRYPTION_KEY::"
          PLAID_SECRET         = "${aws_secretsmanager_secret.app.arn}:PLAID_SECRET::"
          GOOGLE_CLIENT_SECRET = "${aws_secretsmanager_secret.app.arn}:GOOGLE_CLIENT_SECRET::"
        }
      }
    }
  }

  instance_configuration {
    cpu               = var.app_cpu
    memory            = var.app_memory
    instance_role_arn = aws_iam_role.task.arn
  }

  network_configuration {
    egress_configuration {
      egress_type       = "VPC"
      vpc_connector_arn = aws_apprunner_vpc_connector.main.arn
    }
  }

  health_check_configuration {
    protocol            = "HTTP"
    path                = "/actuator/health"
    interval            = 10
    timeout             = 5
    healthy_threshold   = 1
    unhealthy_threshold = 5
  }

  depends_on = [aws_secretsmanager_secret_version.app]
}
