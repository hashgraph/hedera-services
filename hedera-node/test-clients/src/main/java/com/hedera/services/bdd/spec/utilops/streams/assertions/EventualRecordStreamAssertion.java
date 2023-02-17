/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.junit.RecordStreamAccess.RECORD_STREAM_ACCESS;

import com.hedera.services.bdd.junit.RecordStreamAccess;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.stream.proto.RecordStreamItem;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Assertions;

/**
 * A {@link com.hedera.services.bdd.spec.utilops.UtilOp} that registers itself with {@link
 * RecordStreamAccess} and continually updates the {@link RecordStreamAssertion} yielded by a given
 * factory with each new {@link RecordStreamItem}.
 *
 * <p><b>Important:</b> {@code HapiSpec#exec()} recognizes {@link EventualRecordStreamAssertion}
 * operations as a special case, in two ways.
 *
 * <ol>
 *   <li>If a spec includes at least one {@link EventualRecordStreamAssertion}, and all other
 *       operations have passed, it starts running "background traffic" to ensure record stream
 *       files are being written.
 *   <li>For each {@link EventualRecordStreamAssertion}, the spec then calls ts {@link
 *       #assertHasPassed()}, method which blocks until the assertion has either passed or timed
 *       out. (The default timeout is 3 seconds, since generally we expect the assertion to apply to
 *       the contents of a single record stream file, which are created every 2 seconds given steady
 *       background traffic.)
 * </ol>
 */
public class EventualRecordStreamAssertion extends EventualAssertion {
    private static final String TEST_CONTAINER_NODE0_STREAMS = "build/network/itest/records/node_0";
    private final Function<HapiSpec, RecordStreamAssertion> assertionFactory;

    @Nullable
    private RecordStreamAssertion assertion;

    private Runnable unsubscribe;

    public EventualRecordStreamAssertion(final Function<HapiSpec, RecordStreamAssertion> assertionFactory) {
        this.assertionFactory = assertionFactory;
    }

    public EventualRecordStreamAssertion(
            final Function<HapiSpec, RecordStreamAssertion> assertionFactory, final Duration timeout) {
        super(timeout);
        this.assertionFactory = assertionFactory;
    }

    @Override
    protected boolean submitOp(final HapiSpec spec) throws Throwable {
        final var locToUse = HapiSpec.isRunningInCi()
                ? TEST_CONTAINER_NODE0_STREAMS
                : spec.setup().defaultRecordLoc();
        final var validatingListener = RECORD_STREAM_ACCESS.getValidatingListener(locToUse);
        assertion = Objects.requireNonNull(assertionFactory.apply(spec));
        unsubscribe = validatingListener.subscribe(item -> {
            if (assertion.isApplicableTo(item)) {
                try {
                    if (assertion.test(item)) {
                        result.pass();
                    }
                } catch (final AssertionError e) {
                    result.fail(e.getMessage());
                }
            }
        });
        return false;
    }

    public void assertHasPassed() {
        try {
            final var eventualResult = result.get();
            if (unsubscribe != null) {
                unsubscribe.run();
            }
            if (!eventualResult.passed()) {
                Assertions.fail(eventualResult.getErrorDetails());
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            Assertions.fail("Interrupted while waiting for " + assertion + " to pass");
        }
    }

    @Override
    public String toString() {
        return "Eventually{" + assertion + "}";
    }
}
