/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.junit.hedera.ExternalPath.STREAMS_DIR;
import static com.hedera.services.bdd.junit.support.RecordStreamAccess.RECORD_STREAM_ACCESS;

import com.hedera.services.bdd.junit.support.RecordStreamAccess;
import com.hedera.services.bdd.junit.support.StreamDataListener;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;

/**
 * A {@link com.hedera.services.bdd.spec.utilops.UtilOp} that registers itself with {@link
 * RecordStreamAccess} and continually updates the {@link RecordStreamAssertion} yielded by a given
 * factory with each new {@link RecordStreamItem}.
 *
 * <p><b>Important:</b> {@code HapiSpec#exec()} recognizes {@link EventualRecordStreamAssertion}
 * operations as a special case, in two ways.
 * <ol>
 *   <li>If a spec includes at least one {@link EventualRecordStreamAssertion}, and all other
 *       operations have passed, it starts running "background traffic" to ensure record stream
 *       files are being written.
 *   <li>For each {@link EventualRecordStreamAssertion}, the spec then calls its {@link
 *       #assertHasPassed()}, method which blocks until the assertion has either passed or timed
 *       out. (The default timeout is 3 seconds, since generally we expect the assertion to apply to
 *       the contents of a single record stream file, which are created every 2 seconds given steady
 *       background traffic.)
 * </ol>
 */
public class EventualRecordStreamAssertion extends EventualAssertion {
    /**
     * The factory for the assertion to be tested.
     */
    private final Function<HapiSpec, RecordStreamAssertion> assertionFactory;

    /**
     * Once this op is submitted, the assertion to be tested.
     */
    @Nullable
    private RecordStreamAssertion assertion;
    /**
     * Once this op is submitted, the function to unsubscribe from the record stream.
     */
    @Nullable
    private Runnable unsubscribe;

    public EventualRecordStreamAssertion(final Function<HapiSpec, RecordStreamAssertion> assertionFactory) {
        this.assertionFactory = assertionFactory;
    }

    private EventualRecordStreamAssertion(
            final Function<HapiSpec, RecordStreamAssertion> assertionFactory, final boolean hasPassedIfNothingFailed) {
        super(hasPassedIfNothingFailed);
        this.assertionFactory = assertionFactory;
    }

    /**
     * Returns an {@link EventualRecordStreamAssertion} that will pass as long as the given assertion does not
     * throw an {@link AssertionError} before its timeout.
     * @param assertionFactory the assertion factory
     * @return the eventual record stream assertion that must not fail
     */
    public static EventualRecordStreamAssertion eventuallyAssertingNoFailures(
            final Function<HapiSpec, RecordStreamAssertion> assertionFactory) {
        return new EventualRecordStreamAssertion(assertionFactory, true);
    }

    /**
     * Returns an {@link EventualRecordStreamAssertion} that will pass only if the given assertion explicitly
     * passes within the default timeout.
     * @param assertionFactory the assertion factory
     * @return the eventual record stream assertion that must pass
     */
    public static EventualRecordStreamAssertion eventuallyAssertingExplicitPass(
            final Function<HapiSpec, RecordStreamAssertion> assertionFactory) {
        return new EventualRecordStreamAssertion(assertionFactory, false);
    }

    /**
     * Returns the record stream location for the first listed node in the network targeted
     * by the given spec.
     *
     * @param spec the spec
     * @return a record stream location for the first listed node in the network
     */
    public static String recordStreamLocFor(@NonNull final HapiSpec spec) {
        Objects.requireNonNull(spec);
        return spec.targetNetworkOrThrow()
                .nodes()
                .getFirst()
                .getExternalPath(STREAMS_DIR)
                .toString();
    }

    @Override
    protected boolean submitOp(final HapiSpec spec) throws Throwable {
        final var locToUse = recordStreamLocFor(spec);
        final var validatingListener = RECORD_STREAM_ACCESS.getValidatingListener(locToUse);
        assertion = Objects.requireNonNull(assertionFactory.apply(spec));
        unsubscribe = validatingListener.subscribe(new StreamDataListener() {
            @Override
            public void onNewItem(RecordStreamItem item) {
                if (assertion.isApplicableTo(item)) {
                    try {
                        if (assertion.test(item)) {
                            result.pass();
                        }
                    } catch (final AssertionError e) {
                        result.fail(e.getMessage());
                    }
                }
            }

            @Override
            public void onNewSidecar(TransactionSidecarRecord sidecar) {
                if (assertion.isApplicableToSidecar(sidecar)) {
                    try {
                        if (assertion.testSidecar(sidecar)) {
                            result.pass();
                        }
                    } catch (final AssertionError e) {
                        result.fail(e.getMessage());
                    }
                }
            }

            @Override
            public String name() {
                return assertion.toString();
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

    public void unsubscribe() {
        if (unsubscribe != null) {
            unsubscribe.run();
        }
    }

    @Override
    public String toString() {
        return "Eventually{" + assertion + "}";
    }
}
