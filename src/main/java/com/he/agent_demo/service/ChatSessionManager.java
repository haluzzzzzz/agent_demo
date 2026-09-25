package com.he.agent_demo.service;

import org.springframework.ai.chat.messages.Message;
import java.util.List;

public interface ChatSessionManager {
    List<Message> getHistory(String sessionId);
    void addMessage(String sessionId, Message message);
    void deleteSession(String sessionId);
    List<String> getAllSessionId();
}
