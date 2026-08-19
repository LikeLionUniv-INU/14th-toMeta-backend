package com.likelion.tometa.global.config.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.openai")
public record OpenAiProperties(
        String apiKey,
        String model
) {
}
