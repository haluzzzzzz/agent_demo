package com.he.agent_demo.controller;

import com.he.agent_demo.app.AgentApp;
import com.he.agent_demo.dto.ChatMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;

/**
 * Agent 高级对话接口：工具调用 / RAG / 多模态。
 * 会话历史与 /chat 系列接口共用，可用 /chat/history 查询。
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentController {

    private final AgentApp agentApp;

    /**
     * 工具调用对话（SSE 流式），模型可调用本地工具与 MCP 外部工具
     */
    @PostMapping(value = "/chat/tools", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatWithTools(@RequestBody ChatMessageDTO dto) {
        return toSse(agentApp.chatWithTools(dto.getContent(), dto.getSessionId()));
    }

    /**
     * RAG 知识库问答（SSE 流式），先检索向量库再回答
     */
    @PostMapping(value = "/chat/rag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatWithRag(@RequestBody ChatMessageDTO dto) {
        return toSse(agentApp.chatWithRag(dto.getContent(), dto.getSessionId()));
    }

    /**
     * RAG + 工具 + MCP 全家桶（SSE 流式）
     */
    @PostMapping(value = "/chat/full", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatWithRagAndTools(@RequestBody ChatMessageDTO dto) {
        return toSse(agentApp.chatWithRagAndTools(dto.getContent(), dto.getSessionId()));
    }

    /**
     * 多模态图片理解（非流式）
     */
    @PostMapping("/chat/image")
    public Map<String, String> analyzeImage(@RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "prompt", required = false, defaultValue = "请详细描述这张图片的内容") String prompt)
            throws IOException {
        return Map.of("answer", agentApp.analyzeImage(file, prompt));
    }

    private SseEmitter toSse(Flux<String> flux) {
        SseEmitter sseEmitter = new SseEmitter(0L);
        Disposable subscription = flux.subscribe(
                chunk -> {
                    try {
                        sseEmitter.send(SseEmitter.event().name("message").data(chunk));
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                },
                error -> {
                    log.error("agent sse error", error);
                    sseEmitter.completeWithError(error);
                },
                () -> {
                    try {
                        sseEmitter.send(SseEmitter.event().name("end").data("[DONE]"));
                    } catch (IOException ignored) {
                    }
                    sseEmitter.complete();
                }
        );
        sseEmitter.onCompletion(subscription::dispose);
        sseEmitter.onTimeout(() -> {
            subscription.dispose();
            sseEmitter.complete();
        });
        sseEmitter.onError(e -> subscription.dispose());
        return sseEmitter;
    }
}
