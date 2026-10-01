package com.ttq;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param actionFailureMessage shown instead of the model's reply when a state's action fails,
 *                             so the assistant never claims something that did not happen
 * @param defaultProcess       process a conversation starts in when none is chosen, typically one
 *                             that works out what the user wants and switches to the right process
 * @param maxChoices           most answers a question may offer as buttons; a field with more
 *                             values than this is asked for in the reply alone, since a long list
 *                             of buttons reads worse than the text it came from
 */
@ConfigurationProperties("chatbot")
public record ChatbotProperties(
        @DefaultValue("classpath*:processes/*.y*ml") String processesLocation,
        @DefaultValue("classpath*:apis/*.y*ml") String apisLocation,
        @DefaultValue("30") int historyWindow,
        @DefaultValue("Sorry, I couldn't submit your request just now. Shall I try again?")
        String actionFailureMessage,
        String defaultProcess,
        @DefaultValue("6") int maxChoices) {
}
