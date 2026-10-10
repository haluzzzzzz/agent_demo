package com.he.agent_demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * RAG 知识库服务：文档导入（切分 + 向量化入库）与相似度检索。
 * 支持 txt/md/pdf/docx 等格式（Tika 解析）。
 */
@Service
@Slf4j
public class RagService {

    private final SimpleVectorStore vectorStore;
    private final File persistFile;

    public RagService(SimpleVectorStore vectorStore,
                      @Value("${agent.vector-store-path:data/vectorstore.json}") String path) {
        this.vectorStore = vectorStore;
        this.persistFile = new File(path);
    }

    /**
     * 导入文档：Tika 解析 -> TokenTextSplitter 切分 -> 向量化入库 -> 持久化
     *
     * @return 切分出的文本块数量
     */
    public int ingest(MultipartFile file) throws IOException {
        TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
        List<Document> documents = reader.get();
        documents.forEach(d -> d.getMetadata().put("filename", file.getOriginalFilename()));

        List<Document> chunks = new TokenTextSplitter().apply(documents);
        vectorStore.add(chunks);

        File parent = persistFile.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        vectorStore.save(persistFile);
        log.info("文档 {} 导入完成，切分出 {} 个文本块", file.getOriginalFilename(), chunks.size());
        return chunks.size();
    }

    /**
     * 相似度检索
     */
    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
    }
}
