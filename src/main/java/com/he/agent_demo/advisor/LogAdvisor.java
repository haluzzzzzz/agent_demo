package com.he.agent_demo.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Slf4j
public class LogAdvisor implements CallAdvisor, StreamAdvisor {
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String sessionId = (String) request.context().getOrDefault(CONVERSATION_ID, "unknown");
        log.info("[LLM非流式请求开始] 会话ID:{}，用户提问:{}", sessionId, request.prompt().getInstructions());
        long startTime = System.currentTimeMillis();

        ChatClientResponse response = chain.nextCall(request);

        long costTime = System.currentTimeMillis() - startTime;
        String fullAnswer = response.chatResponse().getResult().getOutput().getText();
        log.info("[LLM非流式请求结束] 会话ID:{}，耗时{}ms，完整回复:{}", sessionId, costTime, fullAnswer);

        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String sessionId = (String) request.context().getOrDefault(CONVERSATION_ID, "unknown");
        log.info("[LLM流式请求开始] 会话ID:{}，用户提问:{}", sessionId, request.prompt().getInstructions());
        long startTime = System.currentTimeMillis();

        Flux<ChatClientResponse> response = chain.nextStream(request);

        return response
                .doOnNext(part -> {
                    if (part.chatResponse() != null && part.chatResponse().getResult() != null) {
                        String chunk = part.chatResponse().getResult().getOutput().getText();
                        log.info("[LLM流式分片输出] 会话ID:{}，分片内容:{}", sessionId, chunk);
                    }
                })
                .doFinally(signalType -> {
                    long costTime = System.currentTimeMillis() - startTime;
                    log.info("[LLM流式请求结束] 会话ID:{}，总耗时{}ms，结束信号:{}", sessionId, costTime, signalType);
                });
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
