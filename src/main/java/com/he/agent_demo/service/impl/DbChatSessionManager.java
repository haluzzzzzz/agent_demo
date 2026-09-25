package com.he.agent_demo.service.impl;

import com.he.agent_demo.entity.ChatMessage;
import com.he.agent_demo.entity.ChatSession;
import com.he.agent_demo.mapper.ChatMessageMapper;
import com.he.agent_demo.mapper.ChatSessionMapper;
import com.he.agent_demo.service.ChatSessionManager;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class DbChatSessionManager implements ChatSessionManager {

    @Resource
    private ChatSessionMapper chatSessionMapper;
    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Override
    public List<Message> getHistory(String sessionId) {
        List<ChatMessage> dbMsgList = chatMessageMapper.selectBySessionId(sessionId);
        List<Message> msgList = new ArrayList<>();
        for(ChatMessage dbMsg : dbMsgList){
            if("USER".equals(dbMsg.getRole())){
                msgList.add(new UserMessage(dbMsg.getContent()));
            }else if("ASSISTANT".equals(dbMsg.getRole())){
                msgList.add(new AssistantMessage(dbMsg.getContent()));
            }
        }
        return msgList;
    }

    @Override
    @Transactional
    public void addMessage(String sessionId, Message message) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if(session == null){
            ChatSession newSession = new ChatSession();
            newSession.setId(sessionId);
            newSession.setTitle("对话");
            newSession.setDelFlag(0);
            chatSessionMapper.insert(newSession);
        }
        ChatMessage dbMsg = new ChatMessage();
        dbMsg.setSessionId(sessionId);
        dbMsg.setRole(message.getMessageType().name());
        dbMsg.setContent(message.getText());
        chatMessageMapper.insert(dbMsg);
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId) {
        chatMessageMapper.deleteBySessionId(sessionId);
        chatSessionMapper.deleteById(sessionId);
    }

    @Override
    public List<String> getAllSessionId() {
        return chatSessionMapper.selectAllSessionId();
    }
}
