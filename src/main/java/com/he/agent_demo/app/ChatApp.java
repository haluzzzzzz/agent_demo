package com.he.agent_demo.app;

import com.he.agent_demo.advisor.LogAdvisor;
import com.he.agent_demo.service.ChatSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Component
@Slf4j
public class ChatApp {

    private final ChatClient chatClient;

    private final ChatSessionManager chatSessionManager;

    private static final String SYSTEM_PROMPT = "你是一个智能助手，你的任务是回答用户的问题。";
//    Spring Ai 会话管理器
//    public ChatApp(OllamaChatModel ollamaChatModel) {
//        ChatMemory chatMemory= MessageWindowChatMemory.builder().build();
//        chatClient = ChatClient.builder(ollamaChatModel)
//                .defaultSystem(SYSTEM_PROMPT)
//                .defaultAdvisors(
//                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
//                        new LogAdvisor()
//                )
//                .build();
//    }

//    手搓会话管理器
    public ChatApp(OllamaChatModel ollamaChatModel, ChatSessionManager chatSessionManager) {
        this.chatSessionManager=chatSessionManager;
        chatClient=ChatClient.builder(ollamaChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new LogAdvisor())
                .build();

    }


//    /**
//     * 带LogAdvisor增强的对话接口（非流式）
//     * @param message 用户提问文本
//     * @param chatId 会话ID
//     * @return LLM返回回答
//     */
//    public String doChat(String message, String chatId) {
//        ChatResponse chatResponse = chatClient.prompt()
//                .user(message)
//                .advisors(spec -> spec
//                        .advisors(new LogAdvisor())       // 注入日志Advisor，本次请求生效
//                        .param(CONVERSATION_ID, chatId)  // 把会话ID放到advisor上下文
//                )
//                .call()
//                .chatResponse();
//
//        String content = chatResponse.getResult().getOutput().getText();
//        // LogAdvisor内部会打印请求、响应、耗时，这里不要再重复打content日志
//        return content;
//    }
//
//    /**
//     * 带LogAdvisor增强的流式对话接口
//     * @param message 用户提问文本
//     * @param chatId 会话ID
//     * @return 流式文本Flux
//     */
//    public Flux<String> streamChat(String message, String chatId) {
//        return chatClient.prompt()
//                .user(message)
//                .advisors(spec -> spec
//                        .param(CONVERSATION_ID, chatId)
//                )
//                .stream()
//                .content();
//    }

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






}
