# 冲压车间垫板货架层位绑定管理系统

## 项目简介

独立五金冲压车间垫板与库房货架分层绑定管理系统，维护垫板档案、货架层位、一对一绑定关系和调整记录，并提供垫板领用归还闭环管理（领用离架、归还上架、逾期与占用监控）。

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

## 垫板领用归还闭环

- 领用：从垫板档案中选择在架垫板，登记领用人、产线/工位、用途、领用时间与预计归还时间；领用成功后垫板自动从原层位离架（`shelf_layer_code`/`bind_time` 置空），层位占用数同步减少，并写入 `CHECKOUT` 调整记录。
- 归还：必须选择当前未被占用（空闲）的层位并记录归还时间；归还后自动恢复垫板与层位的绑定（含绑定时间），并写入 `RETURN` 调整记录。
- 闭环约束：同一块垫板存在未归还记录时禁止重复领用；已归还记录禁止重复归还；禁止归还到已占用层位；领用中的垫板禁止层位绑定/解绑/编辑/删除。
- 台账：`/borrow` 页面支持按状态（领用中/已逾期/已归还）、垫板编号、领用人、领用日期区间筛选，展示逾期标记与当前占用（领用人、产线/工位），顶部统计卡片支持点击快速筛选。

## 垫板保养台账

- 登记：管理员在 `/maintenance` 页面为垫板登记保养类型、处理人、保养时间、保养结果（正常/已修复/异常待处理/报废建议）和备注，并将垫板标记为**可用（AVAILABLE）/待检（PENDING）/停用（DISABLED）**。
- 闭环约束：待检、停用垫板不可被领用（领用弹窗禁用并提示原因），归还时也不能作为归还目标（领用台账“归还”按钮禁用，后端二次校验）；恢复为可用后领用/归还闭环正常。
- 筛选：支持按垫板当前状态、垫板编号、保养日期区间筛选，顶部可用/待检/停用统计卡片支持点击快速筛选。
- 详情：展示垫板档案、最近一次保养、完整状态变更时间线与全部保养记录。
- 一致性：状态落库在 `pad_info.maintenance_status`，刷新后垫板档案、领用归还页与保养台账三处状态保持一致；档案页支持按保养状态筛选并提供“保养”快捷入口。

## 货架层位容量配额

- 配额维护：每层在 `shelf_layer.capacity` 维护容量配额（新建分层默认 10），层位编辑弹窗可调整；**下调配额不得低于当前在架数**，后端二次校验。
- 上架约束：层位绑定、换绑（重分配）、新建档案带层位、归还上架、批量导入均不得让该层超过配额；校验在事务内对层位行加锁（`SELECT ... FOR UPDATE`），防止并发超配额。
- 归还目标：已满层位（占用数 ≥ 配额，含配额为 0 的层位）不能作为归还目标，归还弹窗禁用并标注，后端二次校验。
- 展示：层位页卡片与数据概览按 `在架数/配额` 展示占用进度（空闲/有余量/将满/已满），档案绑定与归还层位下拉同步显示占用并禁用已满层位。
- 一致性：层位占用数始终由 `pad_info.shelf_layer_code` 实时统计，刷新后档案绑定、层位占用与调整记录保持一致。

## 数据库迁移

全新部署由 `mysql/init/init.sql` 自动建表。已有部署升级到领用归还功能时，对存量库执行一次幂等迁移：

```bash
mysql -h127.0.0.1 -P3421 -u pad_user -p pad_stamping < mysql/migration/V2__pad_borrow_record.sql
```

升级到垫板保养台账功能时，再执行一次幂等迁移（新增 `pad_info.maintenance_status` 列与 `pad_maintenance_record` 表）：

```bash
mysql -h127.0.0.1 -P3421 -u pad_user -p pad_stamping < mysql/migration/V3__pad_maintenance.sql
```

升级到层位容量配额功能时，再执行一次幂等迁移（新增 `shelf_layer.capacity` 列，存量层位默认配额 10）：

```bash
mysql -h127.0.0.1 -P3421 -u pad_user -p pad_stamping < mysql/migration/V4__shelf_layer_capacity.sql
```

## 常见问题

- 依赖下载失败：检查 Maven、npm、Docker 镜像源和网络。
- 端口占用：修改 `.env` 中 `FRONTEND_PORT`、`BACKEND_PORT`、`MYSQL_PORT`、`REDIS_PORT`。
- 页面接口失败：先确认 `npm run build` 已通过，再检查 Docker 中 backend 是否启动、Nginx `/api` 代理是否可达。
