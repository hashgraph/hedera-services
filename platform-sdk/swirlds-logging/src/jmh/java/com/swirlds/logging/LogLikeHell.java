package com.swirlds.logging;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Logger;
import java.util.UUID;

public class LogLikeHell implements Runnable {

    public static final RuntimeException THROWABLE = new RuntimeException("Oh no!");
    private final Logger logger;

    public LogLikeHell(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void run() {
        logger.log(Level.INFO, "L0, Hello world!");
        logger.log(Level.INFO, "L1, A quick brown fox jumps over the lazy dog.");
        logger.log(Level.INFO, "L2, Hello world!", THROWABLE);
        logger.log(Level.INFO, "L3, Hello {}!", "placeholder");
        logger.log(Level.INFO, "L4, Hello {}!", THROWABLE, "placeholder");
        logger.withContext("key", "value").log(Level.INFO,"L5, Hello world!");
        logger.withMarker("marker").log(Level.INFO,"L6, Hello world!");
        logger.withContext("user-id", UUID.randomUUID().toString())
                .log(Level.INFO,"L7, Hello world!");
        logger.withContext("user-id", UUID.randomUUID().toString())
                .log(Level.INFO,"L8, Hello {}, {}, {}, {}, {}, {}, {}, {}, {}!",
                        1, 2, 3, 4, 5, 6, 7, 8, 9);
        logger.withContext("user-id", UUID.randomUUID().toString())
                .log(Level.INFO,"L9, Hello {}, {}, {}, {}, {}, {}, {}, {}, {}!", THROWABLE,
                        1, 2, 3, 4, 5, 6, 7, 8, 9);
        logger.withContext("user-id", UUID.randomUUID().toString())
                .withContext("key", "value")
                .log(Level.INFO,"L10, Hello world!");
        logger.withMarker("marker")
                .log(Level.INFO,"L11, Hello world!");
        logger.withMarker("marker1")
                .withMarker("marker2")
                .log(Level.INFO,"L12, Hello world!");
        logger.withContext("key", "value")
                .withMarker("marker1").withMarker("marker2")
                .log(Level.INFO,"L13, Hello {}, {}, {}, {}, {}, {}, {}, {}, {}!",
                        1, 2, 3, 4, 5, 6, 7, 8, 9);
    }
}
