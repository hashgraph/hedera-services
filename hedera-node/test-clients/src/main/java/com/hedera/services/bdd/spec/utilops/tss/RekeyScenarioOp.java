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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doWithStartupConfig;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextStakingPeriod;
import static com.hedera.services.bdd.suites.hip869.NodeCreateTest.generateX509Certificates;
import static com.hedera.services.bdd.suites.utils.validation.ValidationScenarios.TINYBARS_PER_HBAR;
import static java.lang.Long.parseLong;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.tss.api.TssMessage;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeTssLibrary;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.Stream;

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
    private static final byte[] GOSSIP_CERTIFICATE;

    static {
        try {
            GOSSIP_CERTIFICATE = generateX509Certificates(1).getFirst().getEncoded();
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

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

    /**
     * A record that encapsulates the DAB edits to be performed before the staking period change.
     * @param nodesToDelete the set of node IDs to delete
     * @param numNodesToCreate the number of nodes to create
     */
    public record DabEdits(@NonNull Set<Long> nodesToDelete, int numNodesToCreate) {
        public DabEdits {
            requireNonNull(nodesToDelete);
        }
    }

    /**
     * A default stake distribution where all nodes have equal stakes.
     */
    public static final Function<List<HederaNode>, List<Long>> EQUAL_NODE_STAKES =
            nodes -> Collections.nCopies(nodes.size(), 50_000_000_000L * TINYBARS_PER_HBAR / nodes.size());

    /**
     * Factory for TSS message simulations that returns the given behaviors for each non-embedded node.
     */
    public static final Function<List<TssMessageSim>, LongFunction<TssMessageSim>> TSS_MESSAGE_SIMS =
            sims -> nodeId -> sims.get((int) ((nodeId - 1) % sims.size()));

    private final int embeddedShares;
    private final DabEdits dabEdits;
    private final LongFunction<TssMessageSim> tssMessageSims;
    private final Function<List<HederaNode>, List<Long>> nodeStakes;

    /**
     * Constructs a {@link RekeyScenarioOp} with the given stake distribution, DAB edits, and TSS message submission
     * behaviors.
     * @param embeddedShares the current number of shares of the embedded node
     * @param dabEdits the DAB edits
     * @param nodeStakes the stake distribution
     * @param tssMessageSims the TSS message submission behaviors
     */
    public RekeyScenarioOp(
            final int embeddedShares,
            @NonNull final DabEdits dabEdits,
            @NonNull final LongFunction<TssMessageSim> tssMessageSims,
            @NonNull final Function<List<HederaNode>, List<Long>> nodeStakes) {
        this.embeddedShares = embeddedShares;
        this.dabEdits = requireNonNull(dabEdits);
        this.nodeStakes = requireNonNull(nodeStakes);
        this.tssMessageSims = requireNonNull(tssMessageSims);
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        requireNonNull(spec);
        allRunFor(
                spec,
                Stream.of(enableTss(), submitDabOps(), setupScenarioMocks(), crossStakePeriodBoundary())
                        .flatMap(Function.identity())
                        .toList());
        return false;
    }

    /**
     * Returns a stream of operations that enable TSS feature flags.
     */
    private Stream<SpecOperation> enableTss() {
        return Stream.of(overriding("tss.keyCandidateRoster", "true"));
    }

    /**
     * Returns a stream of operations that perform the requested DAB edits before the stake period boundary is crossed.
     */
    private Stream<SpecOperation> submitDabOps() {
        final List<SpecOperation> ops = new ArrayList<>();
        for (int i = 0, n = dabEdits.numNodesToCreate(); i < n; i++) {
            ops.add(newKeyNamed("adminKey" + i));
            ops.add(nodeCreate("newNode" + i).adminKey("adminKey" + i).gossipCaCertificate(GOSSIP_CERTIFICATE));
        }
        dabEdits.nodesToDelete.forEach(nodeId -> ops.add(nodeDelete("" + nodeId)));
        return ops.stream();
    }

    /**
     * Returns a stream of operations that performs the embedded state mutations and mock expectations to
     * enact the desired stake period boundary crossing scenario. In particular, this requires:
     * <ol>
     *     <li>Setting the node hbar stakes in state to the desired distribution (which determines the weights
     *     used in the participant directory derived from the candidate roster).</li>
     *     <li>Setting a well-known collection of TSS messages in state for the active roster hash.</li>
     *     <li>Preparing the {@link FakeTssLibrary} to certify these messages as valid.</li>
     *     <li>Preparing the {@link FakeTssLibrary} to decrypt the desired number of private shares
     *     for the embedded node, when it receives exactly the well-known {@link TssMessage}s.</li>
     *     <li>Preparing the {@link FakeTssLibrary} to return a well-known {@link TssMessage} for each
     *     mock private share, <i>assuming</i> the candidate participant directory has the expected
     *     node weights implied by the stake distribution at the start of the new period.</li>
     * </ol>
     */
    private Stream<SpecOperation> setupScenarioMocks() {
        return Stream.of();
    }

    /**
     * Returns a stream of operations that cross the stake period boundary, triggering the rekeying process.
     */
    private Stream<SpecOperation> crossStakePeriodBoundary() {
        return Stream.of(
                doWithStartupConfig(
                        "staking.periodMins",
                        stakePeriodMins -> waitUntilStartOfNextStakingPeriod(parseLong(stakePeriodMins))),
                // This transaction is now first in a new staking period and should trigger the TSS rekeying process,
                // in particular a successful TssMessage from the embedded node (and potentially a TssVote if the
                // non-embedded nodes submit sufficient TssMessages).
                cryptoCreate("rekeyingTransaction"));
    }

    /**
     * Returns a stream of operations that simulate the desired TSS message submission behavior of the
     * non-embedded nodes.
     */
    private Stream<SpecOperation> simulateOtherNodeTssMessages() {
        return Stream.of();
    }
}
