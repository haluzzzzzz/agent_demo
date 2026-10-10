package com.he.agent_demo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class ThinkingMetadataTest {

    @Autowired
    private OllamaChatModel ollamaChatModel;

    /**
     * 验证【非流式】场景下，Spring AI能否从metadata中拿到thinking字段
     */
    @Test
    public void testNonStreamingThinking() {
        ChatClient chatClient = ChatClient.builder(ollamaChatModel).build();

        ChatResponse response = chatClient.prompt()
                .user("17乘23等于多少")
                .options(OllamaChatOptions.builder()
                        .model("qwen3.5:9b")
                        .enableThinking()
                        .build())
                .call()
                .chatResponse();

        // 打印所有metadata的key，方便确认字段名到底叫什么
        System.out.println("========== 非流式测试 ==========");
        System.out.println("Metadata keys: " + response.getResult().getMetadata().keySet());

        String thinking = response.getResult().getMetadata().get("thinking");
        String content = response.getResult().getOutput().getText();

        System.out.println("thinking内容: " + thinking);
        System.out.println("answer内容: " + content);
        System.out.println("================================");

        assertThat(thinking).isNotNull();
        assertThat(thinking).isNotBlank();
    }

    /**
     * 验证【流式】场景下，Spring AI能否从每个chunk的metadata中拿到thinking字段
     * 这是用来定位官方那个已知issue（streaming丢失thinking）是否在你这个版本上存在
     */
    @Test
    public void testStreamingThinking() {
        ChatClient chatClient = ChatClient.builder(ollamaChatModel).build();

        StringBuilder thinkingCollected = new StringBuilder();
        StringBuilder answerCollected = new StringBuilder();

        System.out.println("========== 流式测试 ==========");

        chatClient.prompt()
                .user("17乘23等于多少")
                .options(OllamaChatOptions.builder()
                        .model("qwen3.5:9b")
                        .enableThinking()
                        .build())
                .stream()
                .chatResponse()
                .doOnNext(chatResponse -> {
                    if (chatResponse.getResult() == null) return;

                    String thinking = chatResponse.getResult().getMetadata().get("thinking");
                    String content = chatResponse.getResult().getOutput().getText();

                    // 每个chunk都打出来，方便看清楚哪些chunk有thinking，哪些没有
                    System.out.println("chunk -> metadata keys=" + chatResponse.getResult().getMetadata().keySet()
                            + ", thinking=[" + thinking + "], content=[" + content + "]");

                    if (thinking != null) thinkingCollected.append(thinking);
                    if (content != null) answerCollected.append(content);
                })
                // 阻塞等待流跑完，方便在单元测试里同步断言（生产代码不要这么用）
                .blockLast();

        System.out.println("流式最终收集到的thinking长度: " + thinkingCollected.length());
        System.out.println("流式最终收集到的answer长度: " + answerCollected.length());
        System.out.println("================================");
        assertThat(thinkingCollected.toString()).isNotBlank();
    }
}