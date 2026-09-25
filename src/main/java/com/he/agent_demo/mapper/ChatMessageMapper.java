package com.he.agent_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.he.agent_demo.entity.ChatMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} ORDER BY create_time ASC")
    List<ChatMessage> selectBySessionId(String sessionId);

    @Delete("DELETE FROM chat_message WHERE session_id = #{sessionId}")
    void deleteBySessionId(String sessionId);
}
