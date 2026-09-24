package com.he.agent_demo.dto;

import lombok.Data;

@Data
public class ChatMessageDTO {
    // 会话唯一标识
    private String sessionId;
    // 用户提问内容
    private String content;

}
