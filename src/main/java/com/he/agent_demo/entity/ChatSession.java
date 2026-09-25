package com.he.agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_session")
public class ChatSession {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String title;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer delFlag;
}
