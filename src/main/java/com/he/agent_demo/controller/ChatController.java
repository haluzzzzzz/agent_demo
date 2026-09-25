package com.he.agent_demo.controller;

import com.he.agent_demo.app.ChatApp;
import com.he.agent_demo.dto.ChatMessageDTO;
import com.he.agent_demo.dto.MessageVo;
import com.he.agent_demo.service.ChatSessionManager;
import com.he.agent_demo.service.impl.ChatMemoryStore;
import com.he.agent_demo.service.impl.ChatService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/chat")
public class ChatController {
    private final ChatApp chatApp;

    @Resource
    private final ChatSessionManager chatSessionManager;

    private final ChatService chatService;

    @Resource
    private final ChatMemoryStore chatMemoryStore;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatMessageDTO dto) {
        log.info("收到SSE请求 sessionId={}, content={}", dto.getSessionId(), dto.getContent());
        SseEmitter sseEmitter = new SseEmitter(30_000L);
        Flux<String> tokenFlux = chatService.streamChat(dto.getContent(), dto.getSessionId());

        tokenFlux.subscribe(
                token -> {
                    try {
                        String escapedToken = token.replace("\"", "\\\"");
                        String jsonPayload = "{\"delta\":\"" + escapedToken + "\"}";
                        log.info("SSE推送：{}", jsonPayload);
                        sseEmitter.send(SseEmitter.event().data(jsonPayload));
                    } catch (IOException e) {
                        log.error("SSE发送异常", e);
                        sseEmitter.completeWithError(e);
                    }
                },
                error -> {
                    log.error("Flux订阅异常", error);
                    sseEmitter.completeWithError(error);
                },
                () -> {
                    try {
                        log.info("推送结束标记 [DONE]");
                        sseEmitter.send(SseEmitter.event().data("[DONE]"));
                    } catch (IOException ignored) {}
                    sseEmitter.complete();
                    log.info("SSE连接关闭");
                }
        );
        return sseEmitter;
    }

    // 获取会话历史消息
    @GetMapping("/history")
    public List<Map<String, String>> getHistory(@RequestParam String sessionId) {
        List<Message> messages = chatSessionManager.getHistory(sessionId);
        return messages.stream().map(msg -> {
            Map<String, String> map = new HashMap<>();
            map.put("role", msg.getMessageType().name());
            map.put("content", msg.getText());
            return map;
        }).toList();
    }

    // 删除整个会话，清空sessionId对应的全部历史消息
    @DeleteMapping("/history")
    public void deleteChatHistory(@RequestParam String sessionId) {
        chatSessionManager.deleteSession(sessionId);
    }

    // 获取全部会话ID列表
    @GetMapping("/sessions")
    public List<String> getSessions() {
        return chatSessionManager.getAllSessionId();

    }

    // 创建空会话
    @PostMapping("/createSession")
    public ResponseEntity<Void> createSession(@RequestBody Map<String,String> body){
        String sid = body.get("sessionId");
        chatMemoryStore.createSession(sid);
        return ResponseEntity.ok().build();
    }

}
