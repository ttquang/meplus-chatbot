package com.ttq.process;

public class ProcessNotFoundException extends RuntimeException {

    public ProcessNotFoundException(String processId) {
        super("Process not found: " + processId);
    }
}
