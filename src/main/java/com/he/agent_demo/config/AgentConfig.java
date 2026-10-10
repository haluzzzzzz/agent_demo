package com.he.agent_demo.config;

import com.he.agent_demo.tools.AgentTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * RAG 向量库 / 检索问答 Advisor / MCP 工具暴露配置
 */
@Configuration
@Slf4j
public class AgentConfig {

    /**
     * 基于内存 + 文件持久化的简单向量库，用 Ollama 做向量化。
     * 生产环境可替换为 Redis/PgVector/Milvus 等向量库。
     */
    @Bean
    public SimpleVectorStore simpleVectorStore(OllamaEmbeddingModel embeddingModel,
                                               @Value("${agent.vector-store-path:data/vectorstore.json}") String path) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File file = new File(path);
        if (file.exists()) {
            store.load(file);
            log.info("已从 {} 加载向量库", file.getAbsolutePath());
        }
        return store;
    }

    /**
     * RAG 检索问答 Advisor：对话前自动从向量库检索 topK 条相关内容拼进 Prompt。
     */
    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(SimpleVectorStore simpleVectorStore) {
        return QuestionAnswerAdvisor.builder(simpleVectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(4)
                        .similarityThreshold(0.5)
                        .build())
                .build();
    }

    /**
     * 把本地 @Tool 工具暴露给 MCP Server，供外部 MCP 客户端调用。
     */
    @Bean
    public MethodToolCallbackProvider methodToolCallbackProvider(AgentTools agentTools) {
        return MethodToolCallbackProvider.builder().toolObjects(agentTools).build();
    }
}
