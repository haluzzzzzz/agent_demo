package com.he.agent_demo.app;

import cn.hutool.core.lang.UUID;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChatAppTest {

    @Resource
    private ChatApp chatApp;


    @Test
    void doChatTest() {
        // 测试用例

        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是haluzzzz";
        String answer = chatApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);

        message = "你现在叫什么名字？";
        answer = chatApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);

        message = "你还记得我吗？";
        answer = chatApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    /**
     * 调用LoveApp中【带LogAdvisor】的非流式方法
     */
    @Test
    void doChatWithLogAdvisorTest() {
        String chatId = UUID.randomUUID().toString();
        String userMsg = "介绍一下Spring AI的Advisor";

        String answer = chatApp.doChat(userMsg, chatId);
        System.out.println("LLM返回结果：" + answer);

        assertNotNull(answer);
        assertFalse(answer.isBlank());
    }

    /**
     * 调用LoveApp中【带LogAdvisor】的流式方法
     */
    @Test
    void streamChatTest() {
        String chatId = UUID.randomUUID().toString();
        String userMsg = "简单讲下什么是Agent";

        Flux<String> flux = chatApp.streamChat(userMsg, chatId);
        // 订阅流，打印分片，阻塞等待全部返回，无需StepVerifier
        flux.doOnNext(chunk -> System.out.print("流式分片：" + chunk))
                .blockLast(Duration.ofSeconds(30));
    }
}