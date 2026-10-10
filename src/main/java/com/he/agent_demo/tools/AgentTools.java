package com.he.agent_demo.tools;

import com.he.agent_demo.mapper.ChatMessageMapper;
import com.he.agent_demo.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 本地工具集：既供 Tool Calling 对话使用，
 * 也通过 MethodToolCallbackProvider 暴露为 MCP Server 的工具。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AgentTools {

    private final ExpressionParser parser = new SpelExpressionParser();

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    @Tool(description = "获取当前的日期时间，格式 yyyy-MM-dd HH:mm:ss")
    public String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Tool(description = "计算一个四则运算表达式并返回结果，支持加减乘除、取余和括号")
    public double calculate(@ToolParam(description = "四则运算表达式，例如 (3+2)*4/2") String expression) {
        log.info("[工具调用] calculate: {}", expression);
        return parser.parseExpression(expression).getValue(Double.class);
    }

    @Tool(description = "查询指定城市的实时天气")
    public String getWeather(@ToolParam(description = "城市名称，例如：北京") String city) {
        log.info("[工具调用] getWeather: {}", city);
        // 模拟数据，实际项目可替换为高德/和风天气等真实 API
        return city + "：晴，气温 26°C，东南风 3 级（模拟数据）";
    }

    @Tool(description = "统计当前聊天系统的会话总数和消息总数")
    public String getChatStats() {
        long sessions = chatSessionMapper.selectCount(null);
        long messages = chatMessageMapper.selectCount(null);
        log.info("[工具调用] getChatStats: {} sessions, {} messages", sessions, messages);
        return "当前共有 " + sessions + " 个会话，" + messages + " 条消息";
    }
}
