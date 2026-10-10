package com.he.agent_demo.dto;

public record OllamaChatChunkDto(String model, OllamaMessageDto message, Boolean done) {}
