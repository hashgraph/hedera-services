package com.swirlds.common.test.throttle;

import static com.swirlds.logging.LogMarker.STARTUP;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.base.time.Time;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RateLimitedLogger Tests")
public class RateLimitedLoggerTests {

    private static final Logger logger = LogManager.getLogger(RateLimitedLoggerTests.class);

    @Test
    void test() throws InterruptedException {

        // TODO how do we load log4j in unit test environment?

        final RateLimitedLogger rateLimitedLogger = new RateLimitedLogger(logger, Time.getCurrent(), 3);

        while (true) {
            MILLISECONDS.sleep(100);
            System.out.println("howdy");
            rateLimitedLogger.info(STARTUP.getMarker(), "Hello, world!");
        }


    }

}
