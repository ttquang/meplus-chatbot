package com.ttq.llm;

/**
 * Produces the assistant's reply and its assessment of process progress for one turn.
 */
public interface TurnGenerator {

    TurnDecision generate(TurnContext context);
}
