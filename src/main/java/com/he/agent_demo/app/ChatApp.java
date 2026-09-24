package com.he.agent_demo.app;

import com.he.agent_demo.advisor.LogAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Component
@Slf4j
public class ChatApp {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "你是一个智能助手，你的任务是回答用户的问题。";

    public ChatApp(OllamaChatModel ollamaChatModel) {
        ChatMemory chatMemory= MessageWindowChatMemory.builder().build();
        chatClient = ChatClient.builder(ollamaChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new LogAdvisor()
                )
                .build();
    }


    /**
     * 带LogAdvisor增强的对话接口（非流式）
     * @param message 用户提问文本
     * @param chatId 会话ID
     * @return LLM返回回答
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec
                        .advisors(new LogAdvisor())       // 注入日志Advisor，本次请求生效
                        .param(CONVERSATION_ID, chatId)  // 把会话ID放到advisor上下文
                )
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        // LogAdvisor内部会打印请求、响应、耗时，这里不要再重复打content日志
        return content;
    }

    /**
     * 带LogAdvisor增强的流式对话接口
     * @param message 用户提问文本
     * @param chatId 会话ID
     * @return 流式文本Flux
     */
    public Flux<String> streamChat(String message, String chatId) {
        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec
                        .param(CONVERSATION_ID, chatId)
                )
                .stream()
                .content();
    }





}
