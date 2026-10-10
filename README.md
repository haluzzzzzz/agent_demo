# agent_demo — 基于 Spring AI + Ollama 的 AI 对话应用

一个集成了 **RAG 知识库问答、Tool Calling 工具调用、多模态图片理解、MCP 服务接入** 的 AI Agent 学习型项目。

后端基于 Spring Boot 3.5 与 Spring AI 1.1.0，模型使用本地 Ollama，开箱即可在本地跑通完整链路。

## 功能特性

- **多轮对话**：SSE 流式 / WebSocket 双通道，支持会话历史持久化（MySQL）
- **思考过程透传**：直连 Ollama 原生 NDJSON 接口，区分 `thinking` / `answer` 分片推送
- **RAG 知识库**：上传 txt / md / pdf / docx 文档，自动切分 + 向量化入库，检索增强问答
- **Tool Calling**：模型可自主调用本地工具（时间 / 计算器 / 天气 / 系统统计）
- **多模态**：调用视觉模型分析上传图片
- **MCP 双向接入**：
  - 服务端：本地工具通过 SSE 暴露给 Cherry Studio / Claude 等任意 MCP 客户端
  - 客户端：可配置接入外部 MCP 服务，外部工具自动并入对话

## 技术栈

| 分类 | 技术 |
|---|---|
| 框架 | Java 21、Spring Boot 3.5、Spring AI 1.1.0 |
| 模型 | Ollama（qwen3.5:9b 对话 / nomic-embed-text 向量 / qwen2.5vl:7b 视觉） |
| 持久化 | MySQL + MyBatis-Plus 3.5.7 |
| 文档解析 | Apache Tika |
| 接口文档 | Knife4j (OpenAPI 3) |
| 工具库 | Lombok、Hutool |

## 项目结构

```
src/main/java/com/he/agent_demo
├── app/                # 对话编排
│   ├── ChatApp         #   基础对话：非流式 / 流式 / 思考过程透传
│   └── AgentApp        #   高级对话：工具调用 / RAG / 多模态
├── advisor/            # Spring AI Advisor（日志切面）
├── config/             # WebSocket、向量库、QA Advisor、MCP 工具暴露
├── controller/
│   ├── ChatController      # /chat  基础对话 + 会话管理
│   ├── AgentController     # /agent 工具调用 / RAG / 图片理解
│   └── KnowledgeController # /knowledge 知识库上传与检索
├── service/
│   ├── ChatSessionManager  # 会话记忆接口（MySQL 实现）
│   └── RagService          # 文档导入与向量检索
├── tools/AgentTools        # @Tool 工具集（同时暴露为 MCP 工具）
├── handler/                # WebSocket 处理器
└── entity / mapper / dto   # MyBatis-Plus 实体与数据传输对象
```

## 环境准备

1. **JDK 21**、**MySQL 8**、**Ollama**
2. 拉取模型：

```bash
ollama pull qwen3.5:9b        # 对话模型（需支持工具调用）
ollama pull nomic-embed-text  # RAG 向量模型
ollama pull qwen2.5vl:7b      # 视觉模型（测多模态前拉取）
```

3. 建库建表：

```sql
CREATE DATABASE chat_agent DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE chat_session (
    id          VARCHAR(64) PRIMARY KEY,
    title       VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME,
    del_flag    TINYINT DEFAULT 0
);

CREATE TABLE chat_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  VARCHAR(64) NOT NULL,
    role        VARCHAR(16) NOT NULL,
    content     TEXT,
    create_time DATETIME,
    KEY idx_session (session_id)
);
```

4. 按需修改 [application.yml](src/main/resources/application.yml) 中的 MySQL 连接与模型名。

## 启动

IDEA 中直接运行 `AgentDemoApplication`；终端方式需先切到 JDK 21：

```bash
# Windows PowerShell
$env:JAVA_HOME="C:\Users\<你>\.jdks\ms-21.0.11"
.\mvnw.cmd spring-boot:run
```

启动后访问接口文档：`http://localhost:8080/api/doc.html`

## 接口一览

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/chat/stream` | POST | 基础对话（SSE，区分 thinking/answer） |
| `/api/chat/history` | GET | 会话历史消息 |
| `/api/chat/sessions` | GET | 会话列表 |
| `/api/agent/chat/tools` | POST | 工具调用对话（SSE） |
| `/api/agent/chat/rag` | POST | RAG 知识库问答（SSE） |
| `/api/agent/chat/full` | POST | RAG + 工具 + MCP 全开（SSE） |
| `/api/agent/chat/image` | POST | 多模态图片理解 |
| `/api/agent/knowledge/upload` | POST | 上传文档建知识库 |
| `/api/agent/knowledge/search` | GET | 向量检索命中文本块 |
| `/ws/ai/chat` | WebSocket | WebSocket 对话通道 |

SSE 接口请求体：`{"sessionId": "s1", "content": "你的问题"}`，事件名 `message` / `thinking` / `answer` / `end`。

## MCP 接入

**作为 MCP 服务端**（默认开启）：在 Cherry Studio / Claude Desktop 等客户端添加 SSE 服务：

```
http://localhost:8080/api/sse
```

可用工具：`getCurrentTime`、`calculate`、`getWeather`、`getChatStats`。

**作为 MCP 客户端**（默认关闭）：将 [application.yml](src/main/resources/application.yml) 中 `spring.ai.mcp.client.enabled` 改为 `true`，取消 `sse.connections` 注释并指向目标 MCP 服务地址，外部工具会自动并入 `/agent/chat/tools` 与 `/agent/chat/full`。

## 快速验证

```bash
# 上传知识库文档
curl -F "file=@你的文档.pdf" http://localhost:8080/api/agent/knowledge/upload

# RAG 问答
curl -N -H "Content-Type: application/json" \
  -d '{"sessionId":"s1","content":"文档里讲了什么？"}' \
  http://localhost:8080/api/agent/chat/rag

# 工具调用
curl -N -H "Content-Type: application/json" \
  -d '{"sessionId":"s1","content":"现在几点？(128+72)*3 等于多少？"}' \
  http://localhost:8080/api/agent/chat/tools

# 图片理解
curl -F "file=@图片.jpg" -F "prompt=描述这张图" \
  http://localhost:8080/api/agent/chat/image
```
