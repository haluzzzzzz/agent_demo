package com.he.agent_demo.app;

import com.he.agent_demo.service.ChatSessionManager;
import com.he.agent_demo.tools.AgentTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * 高级对话编排：工具调用（本地 + MCP）、RAG 知识库问答、多模态图片理解。
 * 会话历史与 ChatApp 共用同一个 ChatSessionManager（MySQL）。
 */
@Component
@Slf4j
public class AgentApp {

    private static final String SYSTEM_PROMPT =
            "你是一个智能助手，可以调用工具和检索知识库来回答用户的问题，请使用中文回答。";

    private final ChatClient chatClient;
    private final OllamaChatModel ollamaChatModel;
    private final ChatSessionManager chatSessionManager;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;

    private final ToolCallback[] localTools;
    private final ObjectProvider<ToolCallbackProvider> mcpToolProviders;
    private final String visionModel;

    public AgentApp(OllamaChatModel ollamaChatModel,
                    ChatSessionManager chatSessionManager,
                    QuestionAnswerAdvisor questionAnswerAdvisor,
                    AgentTools agentTools,
                    ObjectProvider<ToolCallbackProvider> mcpToolProviders,
                    @Value("${agent.vision-model:qwen2.5vl:7b}") String visionModel) {
        this.ollamaChatModel = ollamaChatModel;
        this.chatSessionManager = chatSessionManager;
        this.questionAnswerAdvisor = questionAnswerAdvisor;
        this.mcpToolProviders = mcpToolProviders;
        this.visionModel = visionModel;
        this.localTools = MethodToolCallbackProvider.builder()
                .toolObjects(agentTools)
                .build()
                .getToolCallbacks();
        this.chatClient = ChatClient.builder(ollamaChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    /**
     * 汇总可用工具：本地工具 + MCP 客户端接入的外部工具
     */
    private List<ToolCallback> resolveTools() {
        List<ToolCallback> tools = new ArrayList<>(List.of(localTools));
        ToolCallbackProvider provider = mcpToolProviders.getIfAvailable();
        if (provider != null) {
            tools.addAll(List.of(provider.getToolCallbacks()));
            log.info("已接入 {} 个 MCP 外部工具", provider.getToolCallbacks().length);
        }
        return tools;
    }

    /**
     * 工具调用对话（流式）
     */
    public Flux<String> chatWithTools(String message, String sessionId) {
        List<Message> history = new ArrayList<>(chatSessionManager.getHistory(sessionId));
        UserMessage userMessage = new UserMessage(message);
        history.add(userMessage);

        StringBuilder fullAnswer = new StringBuilder();
        return chatClient.prompt()
                .messages(history)
                .toolCallbacks(resolveTools())
                .stream()
                .content()
                .doOnNext(fullAnswer::append)
                .doOnComplete(() -> saveHistory(sessionId, userMessage, fullAnswer.toString()));
    }

    /**
     * RAG 知识库问答（流式）：QuestionAnswerAdvisor 自动检索向量库并拼入 Prompt
     */
    public Flux<String> chatWithRag(String message, String sessionId) {
        List<Message> history = new ArrayList<>(chatSessionManager.getHistory(sessionId));
        UserMessage userMessage = new UserMessage(message);
        history.add(userMessage);

        StringBuilder fullAnswer = new StringBuilder();
        return chatClient.prompt()
                .messages(history)
                .advisors(questionAnswerAdvisor)
                .advisors(spec -> spec.param(CONVERSATION_ID, sessionId))
                .stream()
                .content()
                .doOnNext(fullAnswer::append)
                .doOnComplete(() -> saveHistory(sessionId, userMessage, fullAnswer.toString()));
    }

    /**
     * 全家桶：RAG 检索 + 工具调用 + MCP 工具（流式）
     */
    public Flux<String> chatWithRagAndTools(String message, String sessionId) {
        List<Message> history = new ArrayList<>(chatSessionManager.getHistory(sessionId));
        UserMessage userMessage = new UserMessage(message);
        history.add(userMessage);

        StringBuilder fullAnswer = new StringBuilder();
        return chatClient.prompt()
                .messages(history)
                .advisors(questionAnswerAdvisor)
                .toolCallbacks(resolveTools())
                .stream()
                .content()
                .doOnNext(fullAnswer::append)
                .doOnComplete(() -> saveHistory(sessionId, userMessage, fullAnswer.toString()));
    }

    /**
     * 多模态图片理解：用视觉模型分析上传的图片
     */
    public String analyzeImage(MultipartFile image, String prompt) throws IOException {
        String contentType = image.getContentType();
        MimeType mimeType = StringUtils.hasText(contentType)
                ? MimeTypeUtils.parseMimeType(contentType)
                : MimeTypeUtils.IMAGE_PNG;
        Resource imageResource = image.getResource();

        Media media = Media.builder()
                .mimeType(mimeType)
                .data(imageResource)
                .build();
        UserMessage userMessage = UserMessage.builder()
                .text(prompt)
                .media(media)
                .build();

        // 指定视觉模型覆盖默认的文本对话模型
        Prompt visionPrompt = new Prompt(userMessage,
                OllamaChatOptions.builder().model(visionModel).build());
        String answer = ollamaChatModel.call(visionPrompt)
                .getResult()
                .getOutput()
                .getText();
        log.info("图片理解完成，模型: {}，回答长度: {}", visionModel, answer == null ? 0 : answer.length());
        return answer;
    }

    private void saveHistory(String sessionId, UserMessage userMessage, String answer) {
        chatSessionManager.addMessage(sessionId, userMessage);
        chatSessionManager.addMessage(sessionId, new AssistantMessage(answer));
    }
}
