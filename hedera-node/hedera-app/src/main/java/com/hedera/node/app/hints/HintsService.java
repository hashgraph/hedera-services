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

package com.hedera.node.app.hints;

import com.hedera.cryptography.bls.GroupAssignment;
import com.hedera.cryptography.bls.SignatureSchema;
import com.hedera.cryptography.pairings.api.Curve;
import com.hedera.node.app.blocks.BlockHashSigner;
import com.hedera.node.app.hints.handlers.HintsHandlers;
import com.hedera.node.app.hints.impl.HintsConstructionController;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.lifecycle.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Map;

/**
 * Orchestrates the hinTS algorithms for,
 * <ol>
 *     <li>Silent setup of party keys and hints.</li>
 *     <li>Preprocessing and acceptance of aggregation and verification keys.</li>
 *     <li>Aggregating partial signatures under a provably well-formed aggregate public key.</li>
 * </ol>
 * Note that only the first two of these require <i>deterministic</i> orchestration, since any
 * valid aggregation of partial signatures that exceeds the threshold works equally well. But a
 * strong minority of the current roster must reach deterministic consensus on the first two
 * steps before the third can be attempted.
 * <p>
 * All the expensive cryptographic work being orchestrated happens in threads spawned by the
 * service, and <i>not</i> in the {@code preHandle} or {@code handleTransaction} threads as
 * they dispatch to the service's {@link com.hedera.node.app.spi.workflows.TransactionHandler}s.
 * When a background thread finishes some costly cryptographic work for a node, it gossips
 * the result to the rest of the network via a {@link TransactionCategory#NODE} transaction.
 * <p>
 * All honest nodes will incorporate results deterministically, depending on the type of
 * result. If it is an individual result like a hint, then all honest nodes will adhere to
 * a deterministic policy for adopting a particular set of valid results. If it is an
 * aggregate result like a verification key, all honest nodes will wait to adopt it until
 * a strong minority of the current roster has approved that exact result.
 * <p>
 * The service only supports orchestration of one ongoing hinTS construction at a time,
 * and if requested to orchestrate a different construction, will abandon all in-progress
 * work.
 */
public interface HintsService extends Service, BlockHashSigner {
    String NAME = "HintsService";

    SignatureSchema SIGNATURE_SCHEMA = SignatureSchema.create(Curve.ALT_BN128, GroupAssignment.SHORT_SIGNATURES);

    /**
     * Since the roster service has to decide to adopt the candidate roster
     * at an upgrade boundary based on availability of hinTS preprocessed
     * keys, the hinTS service must be migrated before the roster service
     * in the node's <i>setup</i> phase. (Contrast with the reverse order of
     * dependency in the <i>runtime</i> phase; then the hinTS service depends
     * on the roster service to know how to set up preprocessing work.)
     */
    int MIGRATION_ORDER = RosterService.MIGRATION_ORDER - 1;

    /**
     * Returns the active verification key, or throws if none is active.
     */
    @NonNull
    Bytes activeVerificationKeyOrThrow();

    /**
     * Returns the handlers for the {@link HintsService}.
     */
    HintsHandlers handlers();

    /**
     * Takes any actions needed to advance the state of the {@link HintsService} toward
     * having completed the most up-to-date hinTS construction for the roster store.
     * <p>
     * The basic flow examines the roster store to determine what combination of source/target
     * roster hashes would represent the most up-to-date hinTS construction. Given these source/target
     * hashes, it takes one of three courses of action:
     * <ol>
     *     <Li> If a completed construction already exists in {@link HintsService} state with the given
     *     source/target hashes, returns as a no-op.</Li>
     *     <Li> If there is already an active {@link HintsConstructionController} driving completion
     *     for the given source/target hashes, this call will invoke its
     *     {@link HintsConstructionController#advanceConstruction(Instant, WritableHintsStore)} method.</li>
     *     <Li>If there is no active {@link HintsConstructionController} for the given source/target
     *     hashes, this will create one based on the given consensus time and {@link HintsService} states;
     *     and will record the creation event in network state if this is the first time the network ever
     *     began reconciling a hinTS construction for these source/target hashes.</Li>
     * </ol>
     * <p>
     * <b>IMPORTANT:</b> Note that whether a new {@link HintsConstructionController} object is created, or an
     * appropriate one already exists, its subsequent behavior will be a deterministic function of the given
     * consensus time and {@link HintsService} states. That is, controllers are persistent objects <i>only</i>
     * due to performance considerations, but are <i>logically</i> driven deterministically by just the current
     * network state and consensus time.
     *
     * @param now the current consensus time
     * @param rosterStore the roster store, for obtaining rosters if needed
     * @param hintsStore the hints store, for recording progress if needed
     */
    void reconcile(
            @NonNull Instant now, @NonNull ReadableRosterStore rosterStore, @NonNull WritableHintsStore hintsStore);

    /**
     * Stops the hinTS service, causing it to abandon any in-progress work.
     */
    void stop();

    @Override
    default int migrationOrder() {
        return MIGRATION_ORDER;
    }

    @Override
    default @NonNull String getServiceName() {
        return NAME;
    }

    @Override
    void registerSchemas(@NonNull SchemaRegistry registry);

    /**
     * Returns the party size {@code M=2^k} such that the given roster node count
     * will fall inside the range {@code [2*(k-1), 2^k)}.
     *
     * @param n the roster node count
     * @return the party size
     */
    static int partySizeForRosterNodeCount(int n) {
        n++;
        if ((n & (n - 1)) == 0) {
            return n;
        }
        return Integer.highestOneBit(n) << 1;
    }

    /**
     * Returns the weight that would constitute a strong minority of the network weight for a roster.
     *
     * @param weights the weights of the nodes in the roster
     * @return the weight required for a strong minority
     */
    static long strongMinorityWeightFor(@NonNull final Map<Long, Long> weights) {
        return strongMinorityWeightFor(
                weights.values().stream().mapToLong(Long::longValue).sum());
    }

    /**
     * Returns the weight that would constitute a strong minority of the network weight for a given total weight.
     * @param totalWeight the total weight of the network
     * @return the weight required for a strong minority
     */
    static long strongMinorityWeightFor(final long totalWeight) {
        // Since aBFT is unachievable with n/3 malicious weight, using the conclusion of n/3 weight
        // ensures it the conclusion overlaps with the weight held by at least one honest node
        return (totalWeight + 2) / 3;
    }
}
