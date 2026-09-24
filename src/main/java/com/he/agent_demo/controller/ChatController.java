package com.he.agent_demo.controller;

import com.he.agent_demo.app.ChatApp;
import com.he.agent_demo.dto.ChatMessageDTO;
import com.he.agent_demo.dto.MessageVo;
import com.he.agent_demo.service.ChatSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    private final ChatApp chatApp;

    private final ChatSessionManager chatSessionManager;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatMessageDTO dto) {
        SseEmitter sseEmitter = new SseEmitter(30_000L);
        Flux<String> flux = chatApp.streamChat(dto.getContent(), dto.getSessionId());

        flux.subscribe(
                token -> {
                    try {
                        sseEmitter.send(SseEmitter.event().data(token));
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                },
                error -> sseEmitter.completeWithError(error),
                sseEmitter::complete
        );
        return sseEmitter;
    }

    @GetMapping("/chat/history")
    public List<MessageVo> getChatHistory(@RequestParam String sessionId) {
        List<Message> msgList = chatSessionManager.getHistory(sessionId);
        return msgList.stream()
                .map(msg -> new MessageVo(msg.getMessageType().name(), msg.getText()))
                .toList();
    }


}
