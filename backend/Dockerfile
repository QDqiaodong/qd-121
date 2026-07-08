# ======================================
# 后端 SpringBoot 多阶段构建 Dockerfile
# 严禁使用 # syntax=docker/dockerfile:* 语法
# 仅使用 Docker 原生分层缓存机制
# ======================================

ARG DOCKER_REGISTRY=docker.io/

# ---------- 构建阶段：Maven 编译打包 ----------
FROM ${DOCKER_REGISTRY}maven:3.9.6-eclipse-temurin-17 AS builder

ARG DOCKER_REGISTRY
ENV DOCKER_REGISTRY=${DOCKER_REGISTRY}

WORKDIR /app

# 步骤1：先复制 Maven 全局配置，优化镜像源
COPY settings.xml /root/.m2/settings.xml

# 步骤2：仅复制 pom.xml，下载依赖（依赖层缓存）
# 只要 pom.xml 不变，此层就会复用，不会重新下载依赖
COPY pom.xml ./

# 下载全部依赖到本地仓库，离线模式可加速后续构建
RUN mvn compile -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true -B --no-transfer-progress

# 步骤3：复制源码，进行编译打包（源码变更时才会重新执行此层）
COPY src ./src

# 跳过测试，编译打包
RUN mvn clean package -Dmaven.test.skip=true -B --no-transfer-progress -q

# ---------- 运行阶段：JRE 运行环境 ----------
FROM ${DOCKER_REGISTRY}eclipse-temurin:17-jre-jammy

ARG DOCKER_REGISTRY
ENV DOCKER_REGISTRY=${DOCKER_REGISTRY}

WORKDIR /app

# 安装时区配置，设置为 Asia/Shanghai
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

# 从构建阶段复制编译好的 jar 包
COPY --from=builder /app/target/pad-management.jar app.jar

# 暴露容器内部端口（与 application.yml 一致）
EXPOSE 8088

# JVM 启动参数配置
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
