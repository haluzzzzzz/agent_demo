package com.he.agent_demo.controller;

import com.he.agent_demo.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * RAG 知识库管理接口
 */
@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final RagService ragService;

    /**
     * 上传文档到知识库，支持 txt/md/pdf/docx 等，返回切分的文本块数量
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        int chunks = ragService.ingest(file);
        return Map.of(
                "filename", file.getOriginalFilename(),
                "chunks", chunks,
                "message", "导入成功，可直接通过 /agent/chat/rag 提问"
        );
    }

    /**
     * 相似度检索，直接查看知识库命中的文本块
     */
    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String q,
                                            @RequestParam(defaultValue = "4") int topK) {
        return ragService.search(q, topK).stream()
                .map(doc -> Map.of(
                        "text", doc.getText(),
                        "metadata", doc.getMetadata()))
                .toList();
    }
}
