package com.hedera.node.app.hints;

import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.lifecycle.Service;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

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
public interface HintsService extends Service {
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
     * Starts the hinTS service in the node's setup phase.
     */
    void start();

    /**
     * Signals the service to orchestrate any activity needed at the current consensus
     * time to ensure existence of a complete hinTS construction for the given current
     * roster hash approved by the given prior roster hash. The service is allowed to
     * mutate its state as needed to track the progress of the orchestration.
     * @param now the current consensus time
     * @param writableStates the service's own states
     * @param priorRosterHash the hash of the roster that was active before the current one
     * @param currentRosterHash the hash of the current active roster
     * @param rosterStore the roster store, for obtaining rosters if needed
     */
    void orchestrateConstruction(
            @NonNull Instant now,
            @NonNull WritableStates writableStates,
            @Nullable Bytes priorRosterHash,
            @NonNull Bytes currentRosterHash,
            @NonNull ReadableRosterStore rosterStore);

    /**
     * Stops the hinTS service, causing it to abandon any in-progress work.
     */
    void stop();

    @Override
    default int migrationOrder() {
        return MIGRATION_ORDER;
    }

    @NonNull
    @Override
    default String getServiceName() {
        return NAME;
    }

    @Override
    void registerSchemas(@NonNull SchemaRegistry registry);
}
