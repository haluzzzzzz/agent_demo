package com.he.agent_demo.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.he.agent_demo.app.ChatApp;
import com.he.agent_demo.dto.ChatMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@RequiredArgsConstructor
@Component
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatApp chatApp;

    private final ObjectMapper objectMapper;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            ChatMessageDTO chatMessageDTO = objectMapper.readValue(payload, ChatMessageDTO.class);
            String sessionId = chatMessageDTO.getSessionId();
            String content = chatMessageDTO.getContent();

            chatApp.streamChat(content, sessionId)
                    .doOnNext(token -> {
                        try {
                            session.sendMessage(new TextMessage(token));
                        } catch (IOException e) {
                            log.error("websocket send token error", e);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            session.sendMessage(new TextMessage("[END]"));
                        } catch (IOException e) {
                            log.error("websocket send [END] error", e);
                        }
                    })
                    .doOnError(err -> log.error("websocket error", err))
                    .subscribe();
        } catch (JsonProcessingException e) {
            log.error("websocket json processing error", e);
        } catch (Exception e) {
            log.error("websocket message handle error", e);
        }
    }
}