package com.familier.ai.service;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class FakeGeminiService {

    public Flux<ServerSentEvent<String>> streamGenerateContent(String prompt, String message) {

        String fullText = """
                Hello user, this is a much longer streaming response from a fake AI system.
                We are simulating how real large language models generate text token by token.
                This helps test performance on mobile devices, especially rendering and streaming behavior.
                The response should feel natural, slightly fragmented, and continuous.
                Sometimes the chunks are very small, sometimes a bit larger.

                You might notice that this response keeps going for quite a while.
                That is intentional, to simulate real-world usage where AI responses can be long.

                Also, we will inject some structured metadata and suggestions in between the stream.
                Keep reading to see how your frontend handles it.
                """;

        List<ServerSentEvent<String>> events = new ArrayList<>();
        Random random = new Random();

        // 🔹 split text into word chunks (simulate tokens)
        String[] words = fullText.trim().split("\\s+");
        int wordIndex = 0;

        while (wordIndex < words.length) {
            int chunkSize = 1 + random.nextInt(3); // 1–3 words per chunk
            int end = Math.min(wordIndex + chunkSize, words.length);

            StringBuilder chunkBuilder = new StringBuilder();
            for (int i = wordIndex; i < end; i++) {
                chunkBuilder.append(words[i]);
                if (i < end - 1) {
                    chunkBuilder.append(" ");
                }
            }
            
            String chunk = chunkBuilder.toString();
            if (end < words.length) {
                chunk += " ";
            }
            
            events.add(chunk(chunk));
            wordIndex = end;
        }

        // 🔹 Inject metadata (split giống real bug)
        events.add(chunk("<suggestion_meta"));
        events.add(chunk("data>{\"priority\":\"high\",\"type\":\"task\",\"confidence\":0.92}</suggestion_metadata>"));

        // 🔹 More content after metadata
        String moreText = """
                Based on your recent activity, it might be a good idea to slow down a bit.
                Taking breaks is important for both productivity and mental health.
                """;

        String[] moreWords = moreText.trim().split("\\s+");
        wordIndex = 0;
        while (wordIndex < moreWords.length) {
            int chunkSize = 1 + random.nextInt(2); // 1–2 words per chunk
            int end = Math.min(wordIndex + chunkSize, moreWords.length);

            StringBuilder chunkBuilder = new StringBuilder();
            for (int i = wordIndex; i < end; i++) {
                chunkBuilder.append(moreWords[i]);
                if (i < end - 1) {
                    chunkBuilder.append(" ");
                }
            }
            
            String moreChunk = chunkBuilder.toString();
            if (end < moreWords.length) {
                moreChunk += " ";
            }
            
            events.add(chunk(moreChunk));
            wordIndex = end;
        }

        // 🔹 Inject suggestions (also split)
        events.add(chunk("<suggestions>[\"Go outside\","));
        events.add(chunk("\"Drink water\",\"Take a nap\",\"Stretch a bit\"]</suggestions>"));

        // 🔹 done event
        events.add(done());

        // 🔥 random delay để giống real AI hơn
        return Flux.fromIterable(events)
                .delayElements(Duration.ofMillis(50 + new Random().nextInt(100)));
    }

    private ServerSentEvent<String> chunk(String data) {
        return ServerSentEvent.<String>builder()
                .event("message")
                .data(data)
                .build();
    }

    private ServerSentEvent<String> done() {
        return ServerSentEvent.<String>builder()
                .event("done")
                .data("[DONE]")
                .build();
    }
}