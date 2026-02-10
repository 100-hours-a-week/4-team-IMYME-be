#!/bin/bash

echo "🚀 IMYME 로컬 모니터링 환경 설정 시작..."

# 로컬 IP 자동 감지
LOCAL_IP=$(ipconfig getifaddr en0 || ipconfig getifaddr en1)

if [ -z "$LOCAL_IP" ]; then
    echo "❌ 로컬 IP를 감지할 수 없습니다."
    echo "💡 수동으로 IP를 입력하세요 (예: 192.168.0.100):"
    read -r LOCAL_IP
fi

echo "📡 감지된 로컬 IP: $LOCAL_IP"

# prometheus.yml 백업
if [ -f "prometheus.yml.template" ]; then
    cp prometheus.yml.template prometheus.yml
else
    # 첫 실행시 템플릿 생성
    if [ -f "prometheus.yml" ]; then
        cp prometheus.yml prometheus.yml.template
    fi
fi

# prometheus.yml에 로컬 IP 적용
cat > prometheus.yml <<EOF
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  # Spring Boot Actuator 메트릭 수집
  - job_name: 'imyme-spring-boot'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['${LOCAL_IP}:8080']
        labels:
          application: 'imyme-backend'
          environment: 'local'
EOF

echo "✅ prometheus.yml 생성 완료 (IP: ${LOCAL_IP}:8080)"

# Docker 확인
if ! command -v docker &> /dev/null; then
    echo "❌ Docker가 설치되어 있지 않습니다."
    echo "💡 Docker Desktop을 설치하세요: https://www.docker.com/products/docker-desktop"
    exit 1
fi

# Docker 실행 확인
if ! docker info &> /dev/null; then
    echo "⚠️  Docker가 실행되고 있지 않습니다."
    echo "🔄 Docker Desktop을 실행 중..."
    open -a Docker
    echo "⏳ Docker가 시작될 때까지 15초 대기..."
    sleep 15
fi

echo "✅ Docker 확인 완료"

# k6 설치 확인
if ! command -v k6 &> /dev/null; then
    echo "⚠️  k6가 설치되어 있지 않습니다."
    echo "💡 k6를 설치하시겠습니까? (y/n)"
    read -r install_k6
    if [ "$install_k6" = "y" ]; then
        if command -v brew &> /dev/null; then
            brew install k6
        else
            echo "❌ Homebrew가 설치되어 있지 않습니다."
            echo "💡 k6 설치 방법: https://k6.io/docs/get-started/installation/"
        fi
    fi
fi

# 모니터링 스택 시작
echo ""
echo "🐳 모니터링 스택 시작 중..."
docker-compose -f docker-compose.k6-grafana.yml down 2>/dev/null
docker-compose -f docker-compose.k6-grafana.yml up -d

echo ""
echo "⏳ 서비스 시작 대기 중..."
sleep 10

# 서비스 상태 확인
echo ""
echo "📊 서비스 상태:"
docker ps --filter "name=imyme-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""
echo "✅ 설정 완료!"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 접속 정보:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Grafana:    http://localhost:3000"
echo "              (admin / admin)"
echo ""
echo "  Prometheus: http://localhost:9092"
echo "  InfluxDB:   http://localhost:8086"
echo "              (admin / admin123456)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "🚀 다음 단계:"
echo "  1. Spring Boot 실행: ./gradlew bootRun"
echo "  2. k6 테스트 실행: cd ../k6-tests && k6 run test-health.js"
echo "  3. Grafana 대시보드 확인"
echo ""