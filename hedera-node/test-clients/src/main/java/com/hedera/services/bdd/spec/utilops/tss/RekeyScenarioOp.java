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

package com.hedera.services.bdd.spec.utilops.tss;

import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

/**
 * A convenience operation to test TSS roster rekeying scenarios in repeatable embedded mode. A scenario is built
 *  by specifying,
 *  <ol>
 *      <li>The distribution of HBAR stakes (and hence the number of shares of the embedded node).</li>
 *      <li>The DAB edits that should be performed (if any) before the staking period change.</li>
 *      <li>The simulated message-submission behavior of the non-embedded nodes after the staking period change.</li>
 *  </ol>
 */
public class RekeyScenarioOp extends UtilOp {
    /**
     * Enumerates the possible behaviors of non-embedded nodes after a staking period change.
     */
    public enum TssMessageSim {
        /**
         * The non-embedded node skips submitting TSS messages for its private shares.
         */
        SKIP,
        /**
         * The non-embedded node submits valid TSS messages for its private shares.
         */
        VALID,
        /**
         * The non-embedded node submits invalid TSS messages for its private shares.
         */
        INVALID
    }

    private final LongUnaryOperator nodeStakes;
    private final List<SpecOperation> dabEdits;
    private final LongFunction<TssMessageSim> tssMessageSims;

    /**
     * Constructs a {@link RekeyScenarioOp} with the given stake distribution, DAB edits, and TSS message submission
     * behaviors.
     * @param nodeStakes the stake distribution
     * @param dabEdits the DAB edits
     * @param tssMessageSims the TSS message submission behaviors
     */
    public RekeyScenarioOp(
            @NonNull final LongUnaryOperator nodeStakes,
            @NonNull final List<SpecOperation> dabEdits,
            @NonNull final LongFunction<TssMessageSim> tssMessageSims) {
        this.nodeStakes = requireNonNull(nodeStakes);
        this.dabEdits = requireNonNull(dabEdits);
        this.tssMessageSims = requireNonNull(tssMessageSims);
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        requireNonNull(spec);
        return false;
    }
}
