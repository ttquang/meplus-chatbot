package com.ttq.llm;

import java.util.List;

/** Decides which of several product categories a customer's message is about. */
public interface CategoryChooser {

    record Candidate(String code, String name, String description) {
    }

    /**
     * @param reason one sentence on why, for the logs and the caller
     */
    record Choice(String code, String reason) {
    }

    /**
     * @param candidates the categories to choose from, best embedding match first
     * @return the code of one candidate
     * @throws LlmException if the model fails or answers with something that is not a candidate
     */
    Choice choose(String message, List<Candidate> candidates);
}
