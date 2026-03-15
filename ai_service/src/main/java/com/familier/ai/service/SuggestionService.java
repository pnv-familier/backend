package com.familier.ai.service;

import com.familier.ai.repository.SuggestionRepository;

public class SuggestionService {
    private final SuggestionRepository suggestionRepository;

    public SuggestionService(SuggestionRepository suggestionRepository) {
        this.suggestionRepository = suggestionRepository;
    }

}
