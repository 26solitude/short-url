# main.tf

provider "aws" {
  region = "ap-northeast-2" # 서울 리전
}

# 1. 내 컴퓨터 IP 자동 확인 (보안을 위해 내 IP에서만 접속 허용)
data "http" "myip" {
  url = "https://ipv4.icanhazip.com"
}

# 2. 기본 네트워크 정보 가져오기 (AWS 기본 VPC 사용)
data "aws_vpc" "default" {
  default = true
}

# -----------------------------------------------------------
# 3. 보안 그룹 (방화벽) 설정
# -----------------------------------------------------------

# A. 웹 서버(EC2)용 보안 그룹
resource "aws_security_group" "web_sg" {
  name        = "shorturl-web-sg-final"
  description = "Allow HTTP/SSH from My IP"
  vpc_id      = data.aws_vpc.default.id

  # SSH 접속 허용
  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["${chomp(data.http.myip.response_body)}/32"]
  }
  # 웹 접속 허용
  ingress {
    description = "HTTP App"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  # 밖으로 나가는 건 모두 허용
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# B. 데이터베이스(RDS)용 보안 그룹
resource "aws_security_group" "db_sg" {
  name        = "shorturl-rds-sg-final"
  description = "Allow MySQL from Web Server only"
  vpc_id      = data.aws_vpc.default.id

  # 오직 '웹 서버(web_sg)'에서 오는 신호만 받음 (보안 핵심!)
  ingress {
    description     = "MySQL from EC2"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.web_sg.id]
  }
}

# -----------------------------------------------------------
# 4. 리소스 생성 (RDS & EC2)
# -----------------------------------------------------------

# A. RDS 인스턴스 (MySQL)
resource "aws_db_instance" "default" {
  identifier           = "shorturl-db"
  allocated_storage    = 20             # 프리티어 최대 용량
  engine               = "mysql"
  engine_version       = "8.0"
  instance_class       = "db.t3.micro"  # 프리티어 대상 인스턴스
  username             = "admin"
  password             = "ShortUrlPass123!" # 실습용 비밀번호 (복잡하게 설정 필요)
  parameter_group_name = "default.mysql8.0"
  skip_final_snapshot  = true           # 삭제 시 스냅샷 생성 안 함 (빠른 삭제)
  publicly_accessible  = false          # 외부 접속 차단 (EC2 통해서만 접속)

  vpc_security_group_ids = [aws_security_group.db_sg.id]
}

# B. EC2 인스턴스 (Spring Boot 서버)
resource "aws_instance" "app_server" {
  ami           = "ami-0c9c942bd7bf113a2" # Ubuntu 22.04 LTS (서울 리전 기준)
  instance_type = "t3.micro"

  # ★ 비용 폭탄 방지: CPU 크레딧 모드를 'Standard'로 고정
  credit_specification {
    cpu_credits = "standard"
  }

  vpc_security_group_ids = [aws_security_group.web_sg.id]

  # ★ 서버가 켜질 때 실행할 스크립트 연결
  # RDS 접속 정보를 user_data.sh 파일에 변수로 넘겨줍니다.
  user_data = templatefile("${path.module}/user_data.sh", {
    rds_endpoint = aws_db_instance.default.address
    db_username  = aws_db_instance.default.username
    db_password  = aws_db_instance.default.password
  })

  # 스크립트 변경 시 인스턴스 재생성하도록 설정 (선택사항)
  user_data_replace_on_change = true

  tags = {
    Name = "ShortUrl-App-Server"
  }

  # DB가 먼저 만들어져야 서버를 띄움
  depends_on = [aws_db_instance.default]
}

# 5. 결과 출력 (접속 주소)
output "website_url" {
  value = "http://${aws_instance.app_server.public_ip}:8080"
}