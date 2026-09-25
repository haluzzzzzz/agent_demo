package com.he.agent_demo.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private final OllamaChatModel chatModel;

    // ChatService注入ChatMemoryStore
    private final ChatMemoryStore memoryStore;

    public Flux<String> streamChat(String userPrompt, String sessionId) {
        // 保存用户提问
        memoryStore.addMessage(sessionId, "USER", userPrompt);

        Prompt prompt = new Prompt(new UserMessage(userPrompt));
        Flux<ChatResponse> responseFlux = chatModel.stream(prompt);

        StringBuilder sb = new StringBuilder();

        return responseFlux
                .map(resp -> resp.getResult().getOutput().getText())
                .filter(text -> text != null && !text.isBlank())
                .doOnNext(token -> sb.append(token))
                .doOnComplete(() -> {
                    // AI回答全部生成完毕，保存完整回答
                    memoryStore.addMessage(sessionId, "ASSISTANT", sb.toString());
                });
    }
}
