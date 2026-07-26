package com.familier.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient configuration.
 * The Google GenAI starter auto-configures a ChatModel bean.
 * This class exposes a ChatClient.Builder for injection. Default tools and advisors are
 * added in AiFacadeService to keep configuration co-located with usage.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }
}
