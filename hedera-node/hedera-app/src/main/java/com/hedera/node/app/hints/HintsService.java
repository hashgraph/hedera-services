/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import com.hedera.node.app.blocks.BlockHashSigner;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.lifecycle.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

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
     * Takes any actions needed to advance the state of the {@link HintsService} toward
     * having completed the most up-to-date hinTS construction for the given {@link ActiveRosters}.
     * <p>
     * Given active rosters with a source/target transition, this method will,
     * <ol>
     *     <Li>Do nothing if a completed construction for the transition already exists in {@link HintsService}.</Li>
     *     <Li>If there is no active controller for the transition, create one based on the given consensus time and
     *     {@link HintsService} states; and save the created construction in network state if this is the first time
     *     the network ever began reconciling a hinTS construction for the transition.</Li>
     *     <Li>Use the resolved controller do advance progress toward the transition's completion.</Li>
     * </ol>
     * <p>
     * <b>IMPORTANT:</b> Note that whether a new controller, or an appropriate one already exists, its subsequent
     * behavior will be a deterministic function of the given consensus time and {@link HintsService} states. That is,
     * controllers are persistent objects <i>only</i>due to performance considerations, but are <i>logically</i>
     * functions of just the network state and consensus time.
     *
     * @param activeRosters the active rosters
     * @param hintsStore the hints store, for recording progress if needed
     * @param now the current consensus time
     */
    void reconcile(@NonNull ActiveRosters activeRosters, @NonNull WritableHintsStore hintsStore, @NonNull Instant now);

    /**
     * Returns the current verification key for the active hinTS construction, or throws if it is incomplete.
     * @throws IllegalStateException if the active hinTS construction is incomplete
     */
    @NonNull
    Bytes currentVerificationKeyOrThrow();

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
}
