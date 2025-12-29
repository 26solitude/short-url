variable "db_password" {
  description = "RDS root password"
  type        = string
  sensitive   = true  # 터미널 로그에 비밀번호가 노출되지 않게 가려줍니다.
}

resource "aws_key_pair" "deployer" {
  key_name   = "short-url-key"
  public_key = file("${path.module}/short-url-key.pub")
}

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}



provider "aws" {
  region = "ap-northeast-2"
}

# -----------------------------------------------------------
# 1. 최신 우분투 자동 검색 (에러 방지용)
# -----------------------------------------------------------
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# -----------------------------------------------------------
# 2. 보안 그룹 (SSH/Web 전체 허용)
# -----------------------------------------------------------
resource "aws_security_group" "web_sg" {
  name        = "shorturl-web-sg-final-v2" # 이름 충돌 방지 위해 v2로 변경
  description = "Allow HTTP and SSH traffic"

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"] # 접속 편의성
  }

  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"] # 웹 테스트용
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# -----------------------------------------------------------
# 3. IAM Role (EC2 권한 - 도커/로그/SSM)
# -----------------------------------------------------------
resource "aws_iam_role" "ec2_role" {
  name = "short-url-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = { Service = "ec2.amazonaws.com" }
      }
    ]
  })
}

# 정책 연결 (ECR, CloudWatch, SSM)
resource "aws_iam_role_policy_attachment" "ecr_readonly" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}
resource "aws_iam_role_policy_attachment" "cloudwatch_agent" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}
resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ec2_profile" {
  name = "short-url-ec2-profile"
  role = aws_iam_role.ec2_role.name
}

# -----------------------------------------------------------
# 4. RDS (데이터베이스)
# -----------------------------------------------------------
resource "aws_db_instance" "default" {
  allocated_storage      = 10
  db_name                = "shorturl"
  engine                 = "mysql"
  engine_version         = "8.0"
  instance_class         = "db.t3.micro"
  username               = "admin"
  password               = var.db_password
  parameter_group_name   = "default.mysql8.0"
  skip_final_snapshot    = true
  publicly_accessible    = true
  vpc_security_group_ids = [aws_security_group.web_sg.id]
}

# -----------------------------------------------------------
# 5. ECR (도커 이미지 저장소)
# -----------------------------------------------------------
resource "aws_ecr_repository" "app_repo" {
  name         = "short-url-repo"
  force_delete = true
  image_scanning_configuration {
    scan_on_push = true
  }
}

# -----------------------------------------------------------
# 6. EC2 인스턴스 (도커 설치 스크립트 내장)
# -----------------------------------------------------------
resource "aws_instance" "app_server" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = "t3.micro"
  key_name               = "short-url-key"
  vpc_security_group_ids = [aws_security_group.web_sg.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2_profile.name

  # User Data: 도커 및 모니터링 에이전트 자동 설치
  user_data = <<-EOF
    #!/bin/bash
    exec > >(tee /var/log/user-data.log|logger -t user-data -s 2>/dev/console) 2>&1

    # 기본 패키지
    sudo apt-get update -y
    sudo apt-get install -y ca-certificates curl gnupg unzip git

    # Docker 설치
    sudo install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    sudo chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    sudo apt-get update
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    # Docker 권한 및 실행
    sudo usermod -aG docker ubuntu
    sudo systemctl start docker
    sudo systemctl enable docker

    # CloudWatch Agent 설치 (메모리 모니터링)
    wget https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb
    sudo dpkg -i amazon-cloudwatch-agent.deb

    # Config 생성
    sudo mkdir -p /opt/aws/amazon-cloudwatch-agent/etc/
    cat <<CONFIG | sudo tee /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
    {
      "agent": { "metrics_collection_interval": 60, "run_as_user": "root" },
      "metrics": {
        "append_dimensions": { "InstanceId": "$${aws:InstanceId}", "InstanceType": "$${aws:InstanceType}" },
        "metrics_collected": {
          "mem": { "measurement": ["mem_used_percent"], "metrics_collection_interval": 60 },
          "disk": { "measurement": ["used_percent"], "resources": ["/"], "metrics_collection_interval": 60 }
        }
      }
    }
CONFIG

    # Agent 실행
    sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -s -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
  EOF

  tags = {
    Name = "ShortUrl-Docker-Server"
  }
}

# -----------------------------------------------------------
# 7. CloudWatch 대시보드 (자동 생성)
# -----------------------------------------------------------
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "ShortUrl-Dashboard"
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric", x = 0, y = 0, width = 12, height = 6,
        properties = {
          view = "timeSeries", stacked = false, region = "ap-northeast-2", title = "EC2 Resources",
          metrics = [
            ["AWS/EC2", "CPUUtilization", "InstanceId", aws_instance.app_server.id],
            ["CWAgent", "mem_used_percent", "InstanceId", aws_instance.app_server.id, "InstanceType", "t3.micro"]
          ],
          yAxis = { left = { min = 0, max = 100 } }
        }
      },
      {
        type = "metric", x = 12, y = 0, width = 12, height = 6,
        properties = {
          view = "timeSeries", stacked = false, region = "ap-northeast-2", title = "RDS CPU",
          metrics = [ ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", aws_db_instance.default.identifier] ]
        }
      }
    ]
  })
}

# -----------------------------------------------------------
# 8. Outputs
# -----------------------------------------------------------
output "ecr_url" { value = aws_ecr_repository.app_repo.repository_url }
output "website_url" { value = "http://${aws_instance.app_server.public_ip}:8080" }
output "rds_endpoint" { value = aws_db_instance.default.endpoint }