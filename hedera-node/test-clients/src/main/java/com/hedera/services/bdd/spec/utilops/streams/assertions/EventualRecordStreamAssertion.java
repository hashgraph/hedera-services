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

import static com.hedera.services.bdd.junit.hedera.ExternalPath.RECORD_STREAMS_DIR;
import static com.hedera.services.bdd.junit.support.StreamFileAccess.STREAM_FILE_ACCESS;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.junit.support.StreamDataListener;
import com.hedera.services.bdd.junit.support.StreamFileAccess;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Function;

/**
 * A {@link com.hedera.services.bdd.spec.utilops.UtilOp} that registers itself with {@link
 * StreamFileAccess} and continually updates the {@link RecordStreamAssertion} yielded by a given
 * factory with each new {@link RecordStreamItem}.
 */
public class EventualRecordStreamAssertion extends AbstractEventualStreamAssertion {
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
     * Returns an {@link EventualRecordStreamAssertion} that will pass only if the given assertion explicitly
     * passes within the default timeout.
     * @param assertionFactory the assertion factory
     * @return the eventual record stream assertion that must pass
     */
    public static EventualRecordStreamAssertion eventuallyAssertingExplicitPass(
            @NonNull final Function<HapiSpec, RecordStreamAssertion> assertionFactory,
            @NonNull final Duration timeout) {
        requireNonNull(assertionFactory);
        requireNonNull(timeout);
        return new EventualRecordStreamAssertion(assertionFactory, false, timeout);
    }

    private EventualRecordStreamAssertion(
            @NonNull final Function<HapiSpec, RecordStreamAssertion> assertionFactory,
            final boolean hasPassedIfNothingFailed) {
        super(hasPassedIfNothingFailed);
        this.assertionFactory = requireNonNull(assertionFactory);
    }

    private EventualRecordStreamAssertion(
            @NonNull final Function<HapiSpec, RecordStreamAssertion> assertionFactory,
            final boolean hasPassedIfNothingFailed,
            @NonNull final Duration timeout) {
        super(hasPassedIfNothingFailed, timeout);
        this.assertionFactory = requireNonNull(assertionFactory);
    }

    @Override
    protected boolean submitOp(final HapiSpec spec) throws Throwable {
        assertion = requireNonNull(assertionFactory.apply(spec));
        unsubscribe = STREAM_FILE_ACCESS.subscribe(recordStreamLocFor(spec), new StreamDataListener() {
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

    @Override
    public String toString() {
        return "EventuallyRecordStream{" + assertion + "}";
    }

    /**
     * Returns the record stream location for the first listed node in the network targeted
     * by the given spec.
     *
     * @param spec the spec
     * @return a record stream location for the first listed node in the network
     */
    private static Path recordStreamLocFor(@NonNull final HapiSpec spec) {
        return spec.targetNetworkOrThrow().nodes().getFirst().getExternalPath(RECORD_STREAMS_DIR);
    }
}
