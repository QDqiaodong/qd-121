#!/bin/bash

# ============================================================
# 冲压车间垫板货架层位绑定管理系统 - 启动脚本
# ============================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 加载 .env 环境变量
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

# 端口列表
FRONTEND_PORT=${FRONTEND_PORT:-3008}
BACKEND_PORT=${BACKEND_PORT:-8088}
MYSQL_PORT=${MYSQL_PORT:-3309}
REDIS_PORT=${REDIS_PORT:-6380}

# 项目名称
PROJECT_NAME=${PROJECT_NAME:-pad-stamping-management}

# ============================================================
# 函数：检查端口占用
# ============================================================
check_port() {
    local port=$1
    local service=$2
    local pid=""

    if command -v lsof >/dev/null 2>&1; then
        pid=$(lsof -nP -iTCP:${port} -sTCP:LISTEN 2>/dev/null | awk 'NR>1 {print $2}' | head -1)
    elif command -v netstat >/dev/null 2>&1; then
        pid=$(netstat -tlnp 2>/dev/null | grep ":${port} " | awk '{print $7}' | cut -d'/' -f1 | head -1)
    elif command -v ss >/dev/null 2>&1; then
        pid=$(ss -tlnp 2>/dev/null | grep ":${port} " | awk '{print $7}' | cut -d',' -f2 | cut -d'=' -f2 | head -1)
    fi

    if [ -n "$pid" ] && [ "$pid" != "" ]; then
        echo -e "${RED}[ERROR]${NC} 端口 ${port} 已被占用 (PID: ${pid})"
        if command -v ps >/dev/null 2>&1; then
            local process_name=$(ps -p ${pid} -o comm= 2>/dev/null || echo "未知进程")
            echo -e "${RED}[ERROR]${NC} 占用进程: ${process_name}"
        fi
        echo -e "${YELLOW}[INFO]${NC} 请先停止占用端口的进程，或修改 .env 文件中的端口配置"
        exit 1
    fi
}

# ============================================================
# 函数：验证服务可访问性
# ============================================================
verify_service() {
    local url=$1
    local expected_title_keyword=$2
    local max_attempts=30
    local sleep_seconds=2

    echo -e "${BLUE}[INFO]${NC} 正在验证服务: ${url}"

    for ((i=1; i<=max_attempts; i++)); do
        local response=""
        local http_code=""

        response=$(curl -sS -o /dev/null -w "%{http_code}" --connect-timeout 5 "${url}" 2>/dev/null || true)

        if [ "${response}" = "200" ]; then
            local content=""
            content=$(curl -sS "${url}" 2>/dev/null | head -50 || true)

            # 检查是否包含期望的关键词
            if echo "${content}" | grep -qi "${expected_title_keyword}"; then
                echo -e "${GREEN}[SUCCESS]${NC} 服务验证通过: ${url}"
                return 0
            else
                echo -e "${YELLOW}[WARN]${NC} HTTP 200 但内容不匹配，等待中... (${i}/${max_attempts})"
            fi
        else
            echo -e "${YELLOW}[WARN]${NC} 服务未就绪 (HTTP ${response})，等待中... (${i}/${max_attempts})"
        fi

        if [ $i -lt ${max_attempts} ]; then
            sleep ${sleep_seconds}
        fi
    done

    echo -e "${RED}[ERROR]${NC} 服务验证超时: ${url}"
    return 1
}

# ============================================================
# 主流程
# ============================================================

echo ""
echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  冲压车间垫板货架层位绑定管理系统 - 启动脚本${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

# 步骤1：检查端口占用
echo -e "${YELLOW}[STEP 1/5]${NC} 检查端口占用..."
check_port ${FRONTEND_PORT} "前端"
check_port ${BACKEND_PORT} "后端"
check_port ${MYSQL_PORT} "MySQL"
check_port ${REDIS_PORT} "Redis"
echo -e "${GREEN}[OK]${NC} 所有端口可用"
echo ""

# 步骤2：创建必要目录
echo -e "${YELLOW}[STEP 2/5]${NC} 创建必要目录..."
mkdir -p mysql/data
mkdir -p redis/data
mkdir -p backend/uploads
echo -e "${GREEN}[OK]${NC} 目录创建完成"
echo ""

# 步骤3：Docker Compose 构建和启动
echo -e "${YELLOW}[STEP 3/5]${NC} 启动 Docker Compose (构建+启动)..."
docker compose up -d --build
echo -e "${GREEN}[OK]${NC} Docker Compose 启动完成"
echo ""

# 步骤4：等待服务启动
echo -e "${YELLOW}[STEP 4/5]${NC} 等待服务完全启动..."
echo -e "${BLUE}[INFO]${NC} 预计等待 60-120 秒（首次构建需要下载镜像和依赖）"
sleep 30

# 等待 MySQL 健康检查
echo -e "${BLUE}[INFO]${NC} 等待 MySQL 就绪..."
for ((i=1; i<=20; i++)); do
    if docker compose exec -T mysql mysqladmin ping -h localhost -u${MYSQL_USER:-pad_user} -p${MYSQL_PASSWORD:-pad123456} >/dev/null 2>&1; then
        echo -e "${GREEN}[OK]${NC} MySQL 已就绪"
        break
    fi
    echo -n "."
    sleep 3
done
echo ""

# 等待后端就绪
echo -e "${BLUE}[INFO]${NC} 等待后端服务就绪..."
for ((i=1; i<=40; i++)); do
    if curl -sSf http://127.0.0.1:${BACKEND_PORT}/api/pad/list >/dev/null 2>&1 || \
       curl -sSf http://127.0.0.1:${BACKEND_PORT}/api/shelf-layer/list >/dev/null 2>&1; then
        echo -e "${GREEN}[OK]${NC} 后端服务已就绪"
        break
    fi
    echo -n "."
    sleep 3
done
echo ""

# 步骤5：验证服务可访问性
echo -e "${YELLOW}[STEP 5/5]${NC} 验证服务可访问性..."
echo ""

# 验证后端
echo -e "${BLUE}[INFO]${NC} 验证后端服务..."
verify_service "http://127.0.0.1:${BACKEND_PORT}/api/shelf-layer/list" "code"
verify_service "http://localhost:${BACKEND_PORT}/api/shelf-layer/list" "code"
echo ""

# 验证前端
echo -e "${BLUE}[INFO]${NC} 验证前端服务..."
verify_service "http://127.0.0.1:${FRONTEND_PORT}" "pad"
verify_service "http://localhost:${FRONTEND_PORT}" "pad"
echo ""

# 对比 127.0.0.1 和 localhost 响应
echo -e "${BLUE}[INFO]${NC} 验证 127.0.0.1 和 localhost 一致性..."
RESPONSE_127=$(curl -sS "http://127.0.0.1:${FRONTEND_PORT}" | md5sum | awk '{print $1}')
RESPONSE_LOCALHOST=$(curl -sS "http://localhost:${FRONTEND_PORT}" | md5sum | awk '{print $1}')

if [ "${RESPONSE_127}" = "${RESPONSE_LOCALHOST}" ]; then
    echo -e "${GREEN}[OK]${NC} 127.0.0.1 和 localhost 响应一致"
else
    echo -e "${RED}[ERROR]${NC} 127.0.0.1 和 localhost 响应不一致！"
    echo "127.0.0.1: ${RESPONSE_127}"
    echo "localhost: ${RESPONSE_LOCALHOST}"
    exit 1
fi
echo ""

# ============================================================
# 输出访问信息
# ============================================================
echo ""
echo -e "${GREEN}============================================================${NC}"
echo -e "${GREEN}  ✅ 系统启动成功！${NC}"
echo -e "${GREEN}============================================================${NC}"
echo ""
echo -e "${BLUE}📋 服务访问地址：${NC}"
echo -e "   前端页面:       ${YELLOW}http://localhost:${FRONTEND_PORT}${NC}"
echo -e "   前端页面:       ${YELLOW}http://127.0.0.1:${FRONTEND_PORT}${NC}"
echo -e "   后端 API:       ${YELLOW}http://localhost:${BACKEND_PORT}/api${NC}"
echo -e "   MySQL:          ${YELLOW}127.0.0.1:${MYSQL_PORT}${NC}"
echo -e "   Redis:          ${YELLOW}127.0.0.1:${REDIS_PORT}${NC}"
echo ""
echo -e "${BLUE}📋 数据库信息：${NC}"
echo -e "   数据库名:       ${YELLOW}${MYSQL_DATABASE:-pad_stamping}${NC}"
echo -e "   用户名:         ${YELLOW}${MYSQL_USER:-pad_user}${NC}"
echo -e "   密码:           ${YELLOW}${MYSQL_PASSWORD:-pad123456}${NC}"
echo ""
echo -e "${BLUE}📋 容器名称：${NC}"
echo -e "   MySQL:          ${YELLOW}${PROJECT_NAME}-mysql${NC}"
echo -e "   Redis:          ${YELLOW}${PROJECT_NAME}-redis${NC}"
echo -e "   Backend:        ${YELLOW}${PROJECT_NAME}-backend${NC}"
echo -e "   Frontend:       ${YELLOW}${PROJECT_NAME}-frontend${NC}"
echo ""
echo -e "${BLUE}💡 常用命令：${NC}"
echo -e "   查看日志:       ${YELLOW}docker compose logs -f [服务名]${NC}"
echo -e "   停止服务:       ${YELLOW}docker compose down${NC}"
echo -e "   重启服务:       ${YELLOW}docker compose restart${NC}"
echo -e "   重新构建:       ${YELLOW}docker compose up -d --build${NC}"
echo -e "   查看端口:       ${YELLOW}lsof -nP -iTCP:${FRONTEND_PORT} -sTCP:LISTEN${NC}"
echo ""
echo -e "${GREEN}============================================================${NC}"
echo ""

exit 0
