# ======================================
# 前端 Vue3 多阶段构建 Dockerfile
# 严禁使用 # syntax=docker/dockerfile:* 语法
# 仅使用 Docker 原生分层缓存机制
# ======================================

ARG DOCKER_REGISTRY=docker.io/

# ---------- 构建阶段：Node.js 编译打包 ----------
FROM ${DOCKER_REGISTRY}node:20-alpine AS builder

ARG DOCKER_REGISTRY
ENV DOCKER_REGISTRY=${DOCKER_REGISTRY}

WORKDIR /app

# 配置 npm 淘宝镜像源（全局配置，避免 .npmrc 缺失问题）
RUN npm config set registry https://registry.npmmirror.com

# 步骤1：仅复制 package.json 和锁文件，安装依赖（依赖层缓存）
# 只要 package.json 和 lock 文件不变，此层就会复用，不会重新安装依赖
COPY package.json ./
COPY .npmrc ./

# 如果有 package-lock.json 也复制过来
# 注意：npm ci 需要 package-lock.json 存在且完整
COPY package-lock.json* ./

# 使用 npm ci 精确安装依赖（比 npm install 更快更稳定）
# 只有 package.json/package-lock.json 变更时才会重新执行
RUN if [ -f package-lock.json ]; then \
        npm ci --legacy-peer-deps --no-audit --no-fund; \
    else \
        npm install --legacy-peer-deps --no-audit --no-fund; \
    fi

# 步骤2：复制源码，执行构建（源码变更时才会重新执行此层）
COPY . .

# 执行生产环境构建
RUN npm run build

# ---------- 运行阶段：Nginx 静态资源托管 ----------
FROM ${DOCKER_REGISTRY}nginx:alpine

ARG DOCKER_REGISTRY
ENV DOCKER_REGISTRY=${DOCKER_REGISTRY}

WORKDIR /usr/share/nginx/html

# 安装时区配置
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && apk del tzdata

# 复制 Nginx 配置文件
COPY nginx.conf /etc/nginx/nginx.conf

# 从构建阶段复制打包好的静态资源
COPY --from=builder /app/dist /usr/share/nginx/html

# 暴露容器内部端口
EXPOSE 80

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]
