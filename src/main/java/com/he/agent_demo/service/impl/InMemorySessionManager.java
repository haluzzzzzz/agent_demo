//package com.he.agent_demo.service.impl;
//
//import com.he.agent_demo.service.ChatSessionManager;
//import org.springframework.ai.chat.messages.Message;
//import org.springframework.stereotype.Service;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
///***
// * 内存会话管理器
// * key：sessionId
// * value：会话历史记录列表（Spring Ai Message）
// */
//@Service("inMemorySessionManager")
//public class InMemorySessionManager implements ChatSessionManager {
//
//    private final Map<String,List<Message>> sessionStore = new ConcurrentHashMap<>();
//
//    @Override
//    public List<Message> getHistory(String sessionId) {
//        return sessionStore.computeIfAbsent(sessionId, k -> new ArrayList<>());
//    }
//
//    @Override
//    public void addMessage(String sessionId, Message message) {
//        List<Message> msgList = getHistory(sessionId);
//        msgList.add(message);
//    }
//
//    @Override
//    public void deleteMessage(String sessionId) {
//        sessionStore.remove(sessionId);
//    }
//}
