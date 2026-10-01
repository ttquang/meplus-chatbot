package com.ttq.web;

import com.ttq.embedding.EmbeddingException;
import com.ttq.embedding.ReindexInProgressException;
import com.ttq.engine.ConcurrentTurnException;
import com.ttq.engine.ConversationClosedException;
import com.ttq.engine.ConversationNotFoundException;
import com.ttq.llm.LlmException;
import com.ttq.process.ProcessNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({ConversationNotFoundException.class, ProcessNotFoundException.class})
    ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConversationClosedException.class)
    ProblemDetail closed(ConversationClosedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({ConcurrentTurnException.class, ObjectOptimisticLockingFailureException.class})
    ProblemDetail concurrent(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The conversation was updated by another request; please retry");
    }

    @ExceptionHandler(ReindexInProgressException.class)
    ProblemDetail reindexInProgress(ReindexInProgressException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(EmbeddingException.class)
    ProblemDetail embedding(EmbeddingException e) {
        log.error("Embedding failure", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The embedding server is unavailable; products embedded so far are kept, retry to continue");
    }

    @ExceptionHandler(LlmException.class)
    ProblemDetail llm(LlmException e) {
        log.error("LLM failure", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The assistant is temporarily unavailable; please retry");
    }
}
