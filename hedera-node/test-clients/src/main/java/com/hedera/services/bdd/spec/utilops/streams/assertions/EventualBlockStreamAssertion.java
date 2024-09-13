/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.junit.hedera.ExternalPath.BLOCK_STREAMS_DIR;
import static com.hedera.services.bdd.junit.support.StreamFileAccess.STREAM_FILE_ACCESS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.Block;
import com.hedera.services.bdd.junit.support.StreamDataListener;
import com.hedera.services.bdd.spec.HapiSpec;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.function.Function;

public class EventualBlockStreamAssertion extends AbstractEventualStreamAssertion {
    /**
     * The factory for the assertion to be tested.
     */
    private final Function<HapiSpec, BlockStreamAssertion> assertionFactory;
    /**
     * Once this op is submitted, the assertion to be tested.
     */
    @Nullable
    private BlockStreamAssertion assertion;

    /**
     * Returns an {@link EventualBlockStreamAssertion} that will pass as long as the given assertion does not
     * throw an {@link AssertionError} before its timeout.
     * @param assertionFactory the assertion factory
     * @return the eventual block stream assertion that must not fail
     */
    public static EventualBlockStreamAssertion eventuallyAssertingNoFailures(
            @NonNull final Function<HapiSpec, BlockStreamAssertion> assertionFactory) {
        return new EventualBlockStreamAssertion(assertionFactory, true);
    }

    /**
     * Returns an {@link EventualBlockStreamAssertion} that will pass only if the given assertion explicitly
     * passes within the default timeout.
     * @param assertionFactory the assertion factory
     * @return the eventual block stream assertion that must pass
     */
    public static EventualBlockStreamAssertion eventuallyAssertingExplicitPass(
            @NonNull final Function<HapiSpec, BlockStreamAssertion> assertionFactory) {
        return new EventualBlockStreamAssertion(assertionFactory, false);
    }

    private EventualBlockStreamAssertion(
            @NonNull final Function<HapiSpec, BlockStreamAssertion> assertionFactory,
            final boolean hasPassedIfNothingFailed) {
        super(hasPassedIfNothingFailed);
        this.assertionFactory = requireNonNull(assertionFactory);
    }

    @Override
    public boolean needsBackgroundTraffic() {
        return false;
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        requireNonNull(spec);
        assertion = requireNonNull(assertionFactory.apply(spec));
        unsubscribe = STREAM_FILE_ACCESS.subscribe(blockStreamLocFor(spec), new StreamDataListener() {
            @Override
            public void onNewBlock(@NonNull final Block block) {
                requireNonNull(block);
                try {
                    if (assertion.test(block)) {
                        result.pass();
                    }
                } catch (final AssertionError e) {
                    result.fail(e.getMessage());
                }
            }

            @Override
            public String name() {
                return assertion.toString();
            }
        });
        return false;
    }

    /**
     * Returns the block stream location for the first listed node in the network targeted
     * by the given spec.
     *
     * @param spec the spec
     * @return a record stream location for the first listed node in the network
     */
    private static Path blockStreamLocFor(@NonNull final HapiSpec spec) {
        return spec.targetNetworkOrThrow().nodes().getFirst().getExternalPath(BLOCK_STREAMS_DIR);
    }
}
