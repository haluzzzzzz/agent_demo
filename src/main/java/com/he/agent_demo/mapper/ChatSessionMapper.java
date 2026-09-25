package com.he.agent_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.he.agent_demo.entity.ChatSession;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface ChatSessionMapper extends BaseMapper<ChatSession> {
    @Select("SELECT id FROM chat_session WHERE del_flag=0 ORDER BY create_time DESC")
    List<String> selectAllSessionId();
}
