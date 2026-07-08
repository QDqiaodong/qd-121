# 冲压车间垫板货架层位绑定管理系统

## 项目简介

独立五金冲压车间垫板与库房货架分层绑定管理系统，维护垫板档案、货架层位、一对一绑定关系和调整记录。

## 技术栈

- 前端：Vue 3、Vite 5、Element Plus、Pinia、Axios
- 后端：Spring Boot 3、JDK 17、MyBatis-Plus、Maven
- 数据：MySQL 8、Redis 7
- 部署：Docker Compose、Nginx

## 端口说明

| 服务 | 地址 |
| --- | --- |
| 前端 | http://localhost:3121 或 http://127.0.0.1:3121 |
| 后端 API | http://127.0.0.1:8121/api |
| MySQL | 127.0.0.1:3421 |
| Redis | 127.0.0.1:6421 |

端口来自根目录 `.env`，Docker 端口只绑定 `127.0.0.1`。

## 启动方式

```bash
cd qd-121
docker compose up -d --build
```

本地拆分验证：

```bash
cd backend
mvn compile -q

cd ../frontend
npm ci
npm run build
```

## Docker 构建说明

前端 Dockerfile 使用 `npm ci` 按 `package-lock.json` 安装依赖并执行 `npm run build`；后端 Dockerfile 使用 Maven 构建 Spring Boot 应用。整体交付使用：

```bash
docker compose up -d --build
docker compose ps
```

## 常见问题

- 依赖下载失败：检查 Maven、npm、Docker 镜像源和网络。
- 端口占用：修改 `.env` 中 `FRONTEND_PORT`、`BACKEND_PORT`、`MYSQL_PORT`、`REDIS_PORT`。
- 页面接口失败：先确认 `npm run build` 已通过，再检查 Docker 中 backend 是否启动、Nginx `/api` 代理是否可达。
