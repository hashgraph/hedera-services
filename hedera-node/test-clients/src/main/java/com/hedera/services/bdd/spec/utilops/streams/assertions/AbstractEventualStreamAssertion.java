/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.bdd.spec.utilops.streams.assertions;

import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hedera.services.bdd.spec.utilops.streams.EventualAssertionResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import org.junit.jupiter.api.Assertions;

/**
 * <b>Important:</b> {@code HapiSpec#exec()} recognizes {@link AbstractEventualStreamAssertion}
 * operations as a special case, in two ways.
 * <ol>
 *   <li>If a spec includes at least one {@link AbstractEventualStreamAssertion}, and all other
 *       operations have passed, it starts running "background traffic" to ensure record stream
 *       files are being written.
 *   <li>For each {@link AbstractEventualStreamAssertion}, the spec then calls its
 *       {@link #assertHasPassed()}, method which blocks until the assertion has either passed or timed
 *       out. (The default timeout is 5 seconds, since generally we expect the assertion to apply to
 *       the contents of a single record stream file, which are created every 2 seconds given steady
 *       background traffic.)
 * </ol>
 */
public abstract class AbstractEventualStreamAssertion extends UtilOp {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5L);

    protected final EventualAssertionResult result;

    /**
     * Once this op is submitted, the function to unsubscribe from the stream.
     */
    @Nullable
    protected Runnable unsubscribe;

    protected AbstractEventualStreamAssertion(final boolean hasPassedIfNothingFailed) {
        result = new EventualAssertionResult(hasPassedIfNothingFailed, DEFAULT_TIMEOUT);
    }

    protected AbstractEventualStreamAssertion(final boolean hasPassedIfNothingFailed, @NonNull final Duration timeout) {
        result = new EventualAssertionResult(hasPassedIfNothingFailed, timeout);
    }

    /**
     * Returns true if this assertion needs background traffic to be running in order to pass.
     * @return true if this assertion needs background traffic
     */
    public boolean needsBackgroundTraffic() {
        return true;
    }

    /**
     * If this assertion has subscribed to a stream, this method unsubscribes from it.
     */
    public void unsubscribe() {
        if (unsubscribe != null) {
            unsubscribe.run();
        }
    }

    /**
     * Blocks until the assertion has passed, fail, or timed out.
     * @throws AssertionError if the assertion has failed
     */
    public void assertHasPassed() {
        try {
            final var eventualResult = result.get();
            unsubscribe();
            if (!eventualResult.passed()) {
                Assertions.fail(assertionDescription() + " ended with result: " + eventualResult.getErrorDetails());
            }
        } catch (final InterruptedException e) {
            unsubscribe();
            Thread.currentThread().interrupt();
            Assertions.fail("Interrupted while waiting for " + this + " to pass");
        }
    }

    /**
     * Returns a description of the assertion.
     * @return a description of the assertion
     */
    protected abstract String assertionDescription();
}
