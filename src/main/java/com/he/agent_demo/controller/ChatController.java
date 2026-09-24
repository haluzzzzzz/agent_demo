package com.he.agent_demo.controller;

import com.he.agent_demo.app.ChatApp;
import com.he.agent_demo.dto.ChatMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    private final ChatApp chatApp;

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

}
