#!/bin/bash

# -------------------------------------------------------------------------
# 1. 로그 설정 (나중에 범인 찾기용)
# -------------------------------------------------------------------------
# 이 스크립트가 실행되면서 나오는 모든 화면 출력(에러 포함)을
# /var/log/user-data.log 파일에 저장합니다.
# 문제가 생기면 이 파일을 열어보면 됩니다.
exec > >(tee /var/log/user-data.log|logger -t user-data -s 2>/dev/console) 2>&1

echo "🚀 [Step 1] 배포 자동화 스크립트 시작..."

# -------------------------------------------------------------------------
# 2. 필수 프로그램 설치
# -------------------------------------------------------------------------
# 서버를 돌리는 데 필요한 '자바 21'과 코드를 받아올 'Git'을 설치합니다.
# -y 옵션은 "설치하시겠습니까?" 질문에 자동으로 Yes를 하기 위함입니다.
sudo apt-get update -y
sudo apt-get install -y openjdk-21-jdk-headless git

# -------------------------------------------------------------------------
# 3. 메모리 안전장치 (Swap 설정) - 중요!
# -------------------------------------------------------------------------
# 우리가 쓰는 t3.micro는 램이 1GB밖에 없습니다.
# 빌드할 때 램이 부족해서 서버가 멈추는 것을 막기 위해
# 하드디스크의 일부(2GB)를 비상용 메모리(Swap)로 쓰도록 설정합니다.
if [ ! -f /swapfile ]; then
    echo "💾 스왑 메모리 생성 중..."
    sudo fallocate -l 2G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
fi

# -------------------------------------------------------------------------
# 4. 소스 코드 다운로드
# -------------------------------------------------------------------------
# 깃허브에서 최신 코드를 받아옵니다.
# 폴더 이름을 헷갈리지 않게 무조건 'app'이라는 이름으로 고정해서 받습니다.
echo "📥 깃허브에서 코드 다운로드 중..."
cd /home/ubuntu
sudo rm -rf app  # 혹시 기존 폴더가 있다면 지우고 다시 받음 (충돌 방지)
git clone https://github.com/hojang-v/short-url.git app

# -------------------------------------------------------------------------
# 5. 권한 수정 (Root -> Ubuntu) - 아까 에러났던 부분 해결!
# -------------------------------------------------------------------------
# 이 스크립트는 처음에 관리자(Root) 권한으로 실행되어서, 다운받은 파일 주인도 Root가 됩니다.
# 나중에 우리가 'ubuntu' 계정으로 들어가서 수정할 수 있도록
# 파일 주인을 'ubuntu'로 싹 바꿔줍니다.
echo "🔑 파일 권한 수정 중..."
sudo chown -R ubuntu:ubuntu /home/ubuntu/app

# -------------------------------------------------------------------------
# 6. 빌드 (실행 파일 만들기)
# -------------------------------------------------------------------------
# 받아온 소스 코드를 실행 가능한 JAR 파일로 변환합니다.
# 나중에 관리를 편하게 하기 위해 'ubuntu' 유저 권한으로 실행합니다.
echo "☕️ 자바 빌드 시작 (시간이 좀 걸립니다)..."
cd /home/ubuntu/app
chmod +x gradlew
sudo -u ubuntu ./gradlew bootJar

# -------------------------------------------------------------------------
# 7. 서버 실행 (환경변수 주입)
# -------------------------------------------------------------------------
# Terraform이 알려준 RDS 주소와 비밀번호를 받아서 환경변수로 설정합니다.
echo "🚀 서버 실행 준비 중..."

export DB_URL="jdbc:mysql://${rds_endpoint}:3306/shorturl?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&createDatabaseIfNotExist=true"
export DB_USERNAME="${db_username}"
export DB_PASSWORD="${db_password}"

# 서버를 백그라운드(&)에서 실행합니다.
# 터미널이 꺼져도 서버는 계속 돌아가도록 'nohup'을 사용합니다.
echo "🔥 최종 실행!"
sudo -u ubuntu nohup java -jar \
  -Dspring.datasource.url=$DB_URL \
  -Dspring.datasource.username=$DB_USERNAME \
  -Dspring.datasource.password=$DB_PASSWORD \
  build/libs/*.jar > app.log 2>&1 &

echo "✅ 모든 배포 작업 완료!"