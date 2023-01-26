package com.hedera.services.bdd.spec.utilops.streams;

import com.hedera.node.app.hapi.utils.records.RecordStream;
import com.hedera.services.bdd.junit.RecordStreamValidator;
import com.hedera.services.bdd.junit.TestBase;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class RecordAssertions extends UtilOp {
    private static final Duration DEFAULT_INTER_CHECK_DELAY = Duration.ofMillis(2_000L);

    private final String recordStreamLoc;
    private final Duration timeout;
    private final List<RecordStreamValidator> validators;

    public RecordAssertions(
            final String loc,
            final Duration timeout,
            final RecordStreamValidator... validators) {
        this.recordStreamLoc = loc;
        this.timeout = timeout;
        this.validators = Arrays.asList(validators);
    }

    @Override
    protected boolean submitOp(final HapiSpec spec) throws Throwable {
        Throwable lastFailure;
        final var deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try {
                TestBase.assertValidatorsPass(recordStreamLoc, validators);
                // Propagate nothing
                return false;
            } catch (final Throwable memory) {
                lastFailure = memory;
            }
            Thread.sleep(DEFAULT_INTER_CHECK_DELAY.toMillis());
        }
        return false;
    }
}
