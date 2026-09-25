package com.he.agent_demo.service.impl;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ChatMemoryStore {
    // sessionId -> 消息列表
    private final ConcurrentHashMap<String, List<Map<String,String>>> store = new ConcurrentHashMap<>();

    // 创建空会话
    public void createSession(String sessionId){
        store.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    // 获取所有sessionId
    public List<String> getAllSessionIds(){
        return new ArrayList<>(store.keySet());
    }

    // 添加消息
    public void addMessage(String sessionId, String role, String content){
        List<Map<String,String>> msgList = store.get(sessionId);
        if(msgList == null) return;
        msgList.add(Map.of("role", role, "content", content));
    }

    // 获取会话消息
    public List<Map<String,String>> getMessages(String sessionId){
        return store.getOrDefault(sessionId, new ArrayList<>());
    }

    // 删除会话
    public void deleteSession(String sessionId){
        store.remove(sessionId);
    }
}
