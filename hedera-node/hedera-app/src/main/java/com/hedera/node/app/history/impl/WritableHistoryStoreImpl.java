// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.history.impl;

import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.hedera.node.app.history.schemas.V059HistorySchema.ACTIVE_PROOF_CONSTRUCTION_KEY;
import static com.hedera.node.app.history.schemas.V059HistorySchema.HISTORY_SIGNATURES_KEY;
import static com.hedera.node.app.history.schemas.V059HistorySchema.LEDGER_ID_KEY;
import static com.hedera.node.app.history.schemas.V059HistorySchema.NEXT_PROOF_CONSTRUCTION_KEY;
import static com.hedera.node.app.history.schemas.V059HistorySchema.PROOF_KEY_SETS_KEY;
import static com.hedera.node.app.history.schemas.V059HistorySchema.PROOF_VOTES_KEY;
import static com.hedera.node.app.roster.ActiveRosters.Phase.BOOTSTRAP;
import static com.hedera.node.app.roster.ActiveRosters.Phase.HANDOFF;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.history.ConstructionNodeId;
import com.hedera.hapi.node.state.history.HistoryProof;
import com.hedera.hapi.node.state.history.HistoryProofConstruction;
import com.hedera.hapi.node.state.history.HistoryProofVote;
import com.hedera.hapi.node.state.history.ProofKeySet;
import com.hedera.hapi.node.state.history.RecordedHistorySignature;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.platform.state.NodeId;
import com.hedera.node.app.history.WritableHistoryStore;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Default implementation of {@link WritableHistoryStore}.
 */
public class WritableHistoryStoreImpl extends ReadableHistoryStoreImpl implements WritableHistoryStore {
    private final WritableSingletonState<ProtoBytes> ledgerId;
    private final WritableSingletonState<HistoryProofConstruction> nextConstruction;
    private final WritableSingletonState<HistoryProofConstruction> activeConstruction;
    private final WritableKVState<NodeId, ProofKeySet> proofKeySets;
    private final WritableKVState<ConstructionNodeId, RecordedHistorySignature> signatures;
    private final WritableKVState<ConstructionNodeId, HistoryProofVote> votes;

    public WritableHistoryStoreImpl(@NonNull final WritableStates states) {
        super(states);
        this.ledgerId = states.getSingleton(LEDGER_ID_KEY);
        this.nextConstruction = states.getSingleton(NEXT_PROOF_CONSTRUCTION_KEY);
        this.activeConstruction = states.getSingleton(ACTIVE_PROOF_CONSTRUCTION_KEY);
        this.proofKeySets = states.get(PROOF_KEY_SETS_KEY);
        this.signatures = states.get(HISTORY_SIGNATURES_KEY);
        this.votes = states.get(PROOF_VOTES_KEY);
    }

    @Override
    public @NonNull HistoryProofConstruction getOrCreateConstruction(
            @NonNull final ActiveRosters activeRosters,
            @NonNull final Instant now,
            @NonNull final TssConfig tssConfig) {
        requireNonNull(activeRosters);
        requireNonNull(now);
        requireNonNull(tssConfig);
        final var phase = activeRosters.phase();
        if (phase == HANDOFF) {
            throw new IllegalArgumentException("Handoff phase has no construction");
        }
        var construction = getConstructionFor(activeRosters);
        if (construction == null) {
            final var gracePeriod = phase == BOOTSTRAP
                    ? tssConfig.bootstrapProofKeyGracePeriod()
                    : tssConfig.transitionProofKeyGracePeriod();
            construction = updateForNewConstruction(
                    activeRosters.sourceRosterHash(),
                    activeRosters.targetRosterHash(),
                    activeRosters::findRelatedRoster,
                    now,
                    gracePeriod);
        }
        return construction;
    }

    @Override
    public boolean setProofKey(final long nodeId, @NonNull final Bytes proofKey, @NonNull final Instant now) {
        requireNonNull(proofKey);
        requireNonNull(now);
        final var id = new NodeId(nodeId);
        var keySet = proofKeySets.get(id);
        boolean inUse = false;
        if (keySet == null) {
            inUse = true;
            keySet = ProofKeySet.newBuilder()
                    .key(proofKey)
                    .adoptionTime(asTimestamp(now))
                    .build();
        } else {
            keySet = keySet.copyBuilder().nextKey(proofKey).build();
        }
        proofKeySets.put(id, keySet);
        return inUse;
    }

    @Override
    public HistoryProofConstruction setAssemblyTime(final long constructionId, @NonNull final Instant now) {
        requireNonNull(now);
        return updateOrThrow(constructionId, b -> b.assemblyStartTime(asTimestamp(now)));
    }

    @Override
    public void addSignature(final long constructionId, @NonNull final HistorySignaturePublication publication) {
        requireNonNull(publication);
        signatures.put(
                new ConstructionNodeId(constructionId, publication.nodeId()),
                new RecordedHistorySignature(asTimestamp(publication.at()), publication.signature()));
    }

    @Override
    public void addProofVote(final long nodeId, final long constructionId, @NonNull final HistoryProofVote vote) {
        requireNonNull(vote);
        votes.put(new ConstructionNodeId(constructionId, nodeId), vote);
    }

    @Override
    public HistoryProofConstruction completeProof(final long constructionId, @NonNull final HistoryProof proof) {
        requireNonNull(proof);
        return updateOrThrow(constructionId, b -> b.targetProof(proof));
    }

    @Override
    public void setLedgerId(@NonNull final Bytes bytes) {
        requireNonNull(bytes);
        ledgerId.put(new ProtoBytes(bytes));
    }

    @Override
    public boolean purgeStateAfterHandoff(@NonNull final ActiveRosters activeRosters) {
        if (activeRosters.phase() != HANDOFF) {
            throw new IllegalArgumentException("Not in handoff phase");
        }
        if (requireNonNull(nextConstruction.get()).targetRosterHash().equals(activeRosters.currentRosterHash())) {
            // The next construction is becoming the active one; so purge obsolete votes now
            purgeVotesAndSignatures(requireNonNull(activeConstruction.get()), activeRosters::findRelatedRoster);
            // Also purge any obsolete proof keys
            activeRosters.removedNodeIds().forEach(id -> proofKeySets.remove(new NodeId(id)));
            // And finally, make the next construction the active one
            activeConstruction.put(nextConstruction.get());
            nextConstruction.put(HistoryProofConstruction.DEFAULT);
            return true;
        }
        return false;
    }

    /**
     * Updates the construction with the given ID using the given spec.
     *
     * @param constructionId the construction ID
     * @param spec the spec
     * @return the updated construction
     */
    private HistoryProofConstruction updateOrThrow(
            final long constructionId, @NonNull final UnaryOperator<HistoryProofConstruction.Builder> spec) {
        HistoryProofConstruction construction;
        if (requireNonNull(construction = activeConstruction.get()).constructionId() == constructionId) {
            activeConstruction.put(
                    construction = spec.apply(construction.copyBuilder()).build());
        } else if (requireNonNull(construction = nextConstruction.get()).constructionId() == constructionId) {
            nextConstruction.put(
                    construction = spec.apply(construction.copyBuilder()).build());
        } else {
            throw new IllegalArgumentException("No construction with id " + constructionId);
        }
        return construction;
    }

    /**
     * Updates the store for a new construction.
     * @param sourceRosterHash the source roster hash
     * @param targetRosterHash the target roster hash
     * @param lookup the roster lookup
     * @param now the current time
     * @param gracePeriod the grace period
     * @return the new construction
     */
    private HistoryProofConstruction updateForNewConstruction(
            @NonNull final Bytes sourceRosterHash,
            @NonNull final Bytes targetRosterHash,
            @NonNull final Function<Bytes, Roster> lookup,
            @NonNull final Instant now,
            @NonNull final Duration gracePeriod) {
        final var construction = HistoryProofConstruction.newBuilder()
                .constructionId(newConstructionId())
                .sourceRosterHash(sourceRosterHash)
                .targetRosterHash(targetRosterHash)
                .gracePeriodEndTime(asTimestamp(now.plus(gracePeriod)))
                .build();
        if (requireNonNull(activeConstruction.get()).equals(HistoryProofConstruction.DEFAULT)) {
            activeConstruction.put(construction);
        } else {
            if (!requireNonNull(nextConstruction.get()).equals(HistoryProofConstruction.DEFAULT)) {
                // Before replacing the next construction, purge its votes
                purgeVotesAndSignatures(requireNonNull(nextConstruction.get()), lookup);
            }
            nextConstruction.put(construction);
        }
        // Rotate any proof keys requested to be used in the next construction
        final var adoptionTime = asTimestamp(now);
        final var targetRoster = requireNonNull(lookup.apply(targetRosterHash));
        targetRoster.rosterEntries().forEach(entry -> {
            final var nodeId = new NodeId(entry.nodeId());
            final var keySet = proofKeySets.get(nodeId);
            if (keySet != null && keySet.nextKey().length() > 0) {
                final var rotatedKeySet = keySet.copyBuilder()
                        .key(keySet.nextKey())
                        .adoptionTime(adoptionTime)
                        .nextKey(Bytes.EMPTY)
                        .build();
                proofKeySets.put(nodeId, rotatedKeySet);
            }
        });
        return construction;
    }

    /**
     * Purges the votes for the given construction relative to the given roster lookup.
     *
     * @param construction the construction
     * @param lookup the roster lookup
     */
    private void purgeVotesAndSignatures(
            @NonNull final HistoryProofConstruction construction, @NonNull final Function<Bytes, Roster> lookup) {
        final var sourceRoster = requireNonNull(lookup.apply(construction.sourceRosterHash()));
        sourceRoster.rosterEntries().forEach(entry -> {
            final var key = new ConstructionNodeId(construction.constructionId(), entry.nodeId());
            votes.remove(key);
            signatures.remove(key);
        });
    }

    /**
     * Returns a new construction ID.
     */
    private long newConstructionId() {
        return Math.max(
                        requireNonNull(activeConstruction.get()).constructionId(),
                        requireNonNull(nextConstruction.get()).constructionId())
                + 1;
    }
}
