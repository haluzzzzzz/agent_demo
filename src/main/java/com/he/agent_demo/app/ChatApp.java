package com.he.agent_demo.app;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.he.agent_demo.advisor.LogAdvisor;
import com.he.agent_demo.dto.OllamaChatChunkDto;
import com.he.agent_demo.service.ChatSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Component
@Slf4j
public class ChatApp {

    private final ChatClient chatClient;

    private final ChatSessionManager chatSessionManager;

    private static final String SYSTEM_PROMPT = "你是一个智能助手，你的任务是回答用户的问题。";

//    手搓会话管理器
    public ChatApp(OllamaChatModel ollamaChatModel, ChatSessionManager chatSessionManager) {
        this.chatSessionManager=chatSessionManager;
        chatClient=ChatClient.builder(ollamaChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new LogAdvisor())
                .build();

    }



    // 非流式对话接口，带手搓会话管理器
    public String doChat(String message, String sessionId) {
        // 从会话管理器获取会话历史记录
        List<Message> msgList = chatSessionManager.getHistory(sessionId);
        UserMessage userMessage = new UserMessage(message);
        msgList.add(userMessage);
        String answer = chatClient.prompt()
                .messages(msgList)
                .advisors(spec->spec.param(CONVERSATION_ID, sessionId))
                .call()
                .content();

        // 保存会话历史记录
        AssistantMessage assistantMessage = new AssistantMessage(answer);
        chatSessionManager.addMessage(sessionId, userMessage);
        chatSessionManager.addMessage(sessionId, assistantMessage);
        return answer;
    }

    // 流式对话接口，带手搓会话管理器
    public Flux<String> streamChat(String message, String sessionId) {
        // 读取历史消息
        List<Message> history = chatSessionManager.getHistory(sessionId);
        UserMessage userMessage = new UserMessage(message);
        history.add(userMessage);

        // 用于收集完整AI回复
        StringBuilder fullAiResponse = new StringBuilder();

        return chatClient.prompt()
                .messages(history)
                .advisors(spec->spec.param(CONVERSATION_ID, sessionId))
                .stream()
                .content()
                // 每拿到一个token，追加到字符串
                .doOnNext(token -> fullAiResponse.append(token))
                // 流式输出全部正常结束后，保存消息
                .doOnComplete(() -> {
                    AssistantMessage assistantMsg = new AssistantMessage(fullAiResponse.toString());
                    chatSessionManager.addMessage(sessionId, userMessage);
                    chatSessionManager.addMessage(sessionId, assistantMsg);
                });
    }

    private final WebClient ollamaWebClient = WebClient.builder()
            .baseUrl("http://localhost:11434")
            .build();

    public Flux<ChatChunk> streamChatWithThinkingRaw(String message, String sessionId) {
        List<Message> history = chatSessionManager.getHistory(sessionId);
        UserMessage userMessage = new UserMessage(message);
        history.add(userMessage);

        List<Map<String, String>> ollamaMessages = new ArrayList<>();
        ollamaMessages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        for (Message m : history) {
            String role = switch (m.getMessageType()) {
                case USER -> "user";
                case ASSISTANT -> "assistant";
                case SYSTEM -> "system";
                default -> "user";
            };
            ollamaMessages.add(Map.of("role", role, "content", m.getText()));
        }

        StringBuilder fullAnswer = new StringBuilder();

        return ollamaWebClient.post()
                .uri("/api/chat")
                .accept(MediaType.valueOf("application/x-ndjson")) // 关键：显式声明NDJSON，触发流式解码
                .bodyValue(Map.of(
                        "model", "qwen3.5:9b",
                        "messages", ollamaMessages,
                        "think", true,
                        "stream", true
                ))
                .retrieve()
                .bodyToFlux(OllamaChatChunkDto.class) // 直接解码成对象流，不再手动parse String
                .log("ollama-raw-stream")
                .flatMap(chunk -> {
                    if (chunk.message() == null) return Flux.empty();

                    String thinking = chunk.message().thinking();
                    String content = chunk.message().content();

                    List<ChatChunk> chunks = new ArrayList<>();
                    if (thinking != null && !thinking.isBlank()) {
                        chunks.add(new ChatChunk("thinking", thinking));
                    }
                    if (content != null && !content.isBlank()) {
                        fullAnswer.append(content);
                        chunks.add(new ChatChunk("answer", content));
                    }
                    return Flux.fromIterable(chunks);
                })
                .doOnComplete(() -> {
                    AssistantMessage assistantMsg = new AssistantMessage(fullAnswer.toString());
                    chatSessionManager.addMessage(sessionId, userMessage);
                    chatSessionManager.addMessage(sessionId, assistantMsg);
                    log.info("streamChatWithThinkingRaw完成，会话ID: {}，完整回答: {}", sessionId, fullAnswer.toString());
                });
    }

    private Flux<ChatChunk> parseBuffer(StringBuilder buffer, StringBuilder fullAnswer) {
        Flux<ChatChunk> result = Flux.empty();
        String bufStr = buffer.toString();

        String startTag = "";
        String endTag = "";
        int startTagLen = startTag.length();
        int endTagLen = endTag.length();

        while (true) {
            int startIdx = bufStr.indexOf(startTag);
            int endIdx = bufStr.indexOf(endTag);

            // 没有找到思考开始标签：所有内容都是answer，不要清空buffer！！
            if (startIdx == -1) {
                if (!bufStr.isBlank()) {
                    fullAnswer.append(bufStr);
                    result = result.concatWith(Flux.just(new ChatChunk("answer", bufStr)));
                }
                // =====【修复】不要清空buffer！保留，等待下一轮token继续追加=====
                // buffer.setLength(0);
                break;
            }

            // 有开始标签，但还没有闭合标签，等待下一个token，直接退出循环，不处理
            if (endIdx == -1) {
                break;
            }

            // 标签前面的普通文本
            String preText = bufStr.substring(0, startIdx);
            if (!preText.isBlank()) {
                fullAnswer.append(preText);
                result = result.concatWith(Flux.just(new ChatChunk("answer", preText)));
            }

            // 提取思考内容
            String thinkContent = bufStr.substring(startIdx + startTagLen, endIdx);
            result = result.concatWith(Flux.just(new ChatChunk("thinking", thinkContent)));

            // 剩下未处理文本放回buffer
            String remain = bufStr.substring(endIdx + endTagLen);
            buffer.setLength(0);
            buffer.append(remain);
            bufStr = buffer.toString();
        }
        return result;
    }


    // 流式分片实体
        public record ChatChunk(String type, String content){}
}
