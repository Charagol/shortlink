# shortlink-all

**shortlink-all** 是一个基于 Spring Boot 3 + Spring Cloud 微服务架构的 SaaS 短链接服务平台，支持高并发场景下的短链接生成、管理、跳转和访问统计等功能。

## 技术栈

### 后端
| 技术 | 版本 |
|------|------|
| Java | 17 |
| Spring Boot | 3.0.7 |
| Spring Cloud | 2022.0.3 |
| Spring Cloud Alibaba | 2022.0.0.0-RC2 |
| MyBatis-Plus | 3.5.3.1 |
| ShardingSphere JDBC | 5.3.2 |
| Redis / Redisson | 3.21.3 |
| Nacos Discovery | - |
| Sentinel | - |
| MySQL | - |

### 前端
| 技术 | 版本 |
|------|------|
| Vue 3 | 3.3.4 |
| Vite | 4.4.9 |
| Vue Router | 4.2.4 |
| Vuex | 4.0.2 |
| Element Plus | 2.3.14 |
| ECharts | 4.8 |
| Axios | 1.5.1 |

## 功能概览

```
短链接服务平台
├── 短链接管理
│   ├── 创建短链（单条创建、参数配置）
│   ├── 批量创建（Excel 导入、并发生成）
│   ├── 更新短链
│   └── 分页查询
├── 分组管理
│   ├── 新建分组
│   ├── 分组列表
│   ├── 修改分组
│   ├── 删除分组
│   └── 分组排序
├── 回收站管理
│   ├── 移入回收站
│   ├── 回收站列表
│   ├── 恢复链接
│   └── 彻底删除
├── 访问统计
│   ├── 总览统计
│   ├── 访问记录
│   ├── 分组统计
│   └── 分组访问记录
├── 用户认证
│   ├── 用户注册
│   ├── 用户登录
│   ├── 用户信息
│   └── 登出管理
└── URL 元信息
    ├── 自动抓取标题
    └── 标题缓存
```

## 系统架构

```
                        ┌─────────────┐
                        │  前端 (Vue3) │
                        └──────┬──────┘
                               │ HTTP
                        ┌──────▼──────┐
                        │  Gateway    │
                        │  (Port:8000)│
                        └──┬──────┬──┘
                           │      │
                  ┌────────▼─┐  ┌─▼────────┐
                  │  Admin   │  │  Project  │
                  │(Port:8082)│  │(Port:8081)│
                  └────┬─────┘  └─────┬────┘
                       │              │
                  ┌────▼──────────────▼────┐
                  │   Nacos / Redis / MySQL │
                  │   (ShardingSphere 分表)  │
                  └─────────────────────────┘
```

支持两种部署模式：
- **聚合模式**：Admin 和 Project 合并部署（aggregation 模块，Port:8083）
- **分布式模式**：Admin、Project 各自独立部署，Gateway 统一路由

## 环境要求

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | >= 17 | 项目使用 Java 17 特性 |
| Maven | >= 3.6 | 多模块项目构建 |
| MySQL | >= 8.0 | 数据库（ShardingSphere 分表支持） |
| Redis | >= 6.x | 缓存、分布式锁、消息队列 |
| Nacos | >= 2.x | 服务注册与发现 |
| Node.js | >= 16 | 前端构建（console-vue 模块） |
| pnpm | >= 7 | 前端包管理 |

## 快速启动

### 1. 启动基础设施

```bash
# 启动 Nacos（服务注册与发现）
# 下载并启动 Nacos Server，默认端口 8848

# 启动 Redis
redis-server
```

### 2. 创建数据库

创建数据库 `shortlink`，ShardingSphere 会自动按配置创建分表。

### 3. 修改配置

根据实际环境修改各模块的配置文件：

- **数据源配置**：各模块 `application.yaml` 中的 MySQL、Redis 连接信息
- **分片配置**：`shardingsphere-config-dev.yaml` 中的分片策略
- **Nacos 地址**：各模块 `application.yaml` 中的注册中心地址

### 4. 启动后端服务

```bash
# 构建项目
mvn clean package -DskipTests

# 启动服务（按以下顺序）
# 方式一：分布式部署
java -jar gateway/target/shortlink-gateway.jar       # Port 8000
java -jar project/target/shortlink-project.jar       # Port 8081
java -jar admin/target/shortlink-admin.jar           # Port 8082

# 方式二：聚合部署（推荐）
java -jar aggregation/target/shortlink-aggregation.jar  # Port 8083
```

### 5. 启动前端

```bash
cd console-vue
pnpm install
pnpm dev
```

前端默认运行在 `http://localhost:5173`。

## 模块说明

| 模块 | 说明 | 端口 |
|------|------|------|
| gateway | API 网关，统一入口、鉴权、路由 | 8000 |
| project | 短链接核心服务，创建、跳转、统计 | 8081 |
| admin | 管理后台服务，用户、分组、回收站 | 8082 |
| aggregation | 聚合部署模块，合并 admin + project | 8083 |
| console-vue | 前端 SPA 应用 | 5173 |

## 关键设计

### 数据库分片
使用 ShardingSphere JDBC 对核心表进行 16 库分片：
- `t_link`：按 gid（分组 ID）哈希分片
- `t_link_goto`：按 full_short_url 哈希分片
- `t_link_stats_today`：按 gid 哈希分片

### 分布式锁
使用 Redisson 实现分布式锁，确保短链接生成的并发安全性。

### 异步监控
基于 Redis Stream 实现消息队列，用于异步处理访问监控数据，支持消费者幂等和延迟队列。

### 限流熔断
集成 Sentinel 实现流量控制和熔断降级，保障系统稳定性。
