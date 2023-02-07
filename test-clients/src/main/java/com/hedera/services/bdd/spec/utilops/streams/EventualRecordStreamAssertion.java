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
package com.hedera.services.bdd.spec.utilops.streams;

import static com.hedera.services.bdd.junit.RecordStreamAccess.RECORD_STREAM_ACCESS;

import com.hedera.services.bdd.spec.HapiSpec;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Assertions;

public class EventualRecordStreamAssertion extends EventualAssertion {
    private static final String TEST_CONTAINER_NODE0_STREAMS = "build/network/itest/records/node_0";
    private final Function<HapiSpec, RecordStreamAssertion> assertionFactory;
    @Nullable private RecordStreamAssertion assertion;

    private Runnable unsubscribe;

    public EventualRecordStreamAssertion(
            final Function<HapiSpec, RecordStreamAssertion> assertionFactory) {
        this.assertionFactory = assertionFactory;
    }

    public EventualRecordStreamAssertion(
            final Function<HapiSpec, RecordStreamAssertion> assertionFactory,
            final Duration timeout) {
        super(timeout);
        this.assertionFactory = assertionFactory;
    }

    @Override
    protected boolean submitOp(final HapiSpec spec) throws Throwable {
        final var locToUse =
                HapiSpec.isRunningInCi()
                        ? TEST_CONTAINER_NODE0_STREAMS
                        : spec.setup().defaultRecordLoc();
        final var validatingListener = RECORD_STREAM_ACCESS.getValidatingListener(locToUse);
        assertion = Objects.requireNonNull(assertionFactory.apply(spec));
        unsubscribe =
                validatingListener.subscribe(
                        item -> {
                            if (assertion.isApplicableTo(item)) {
                                try {
                                    if (assertion.updateAndTest(item)) {
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
