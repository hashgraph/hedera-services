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

package com.hedera.node.app.hints.impl;

import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.hedera.node.app.hints.HintsService.partySizeForRoster;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.ACTIVE_HINT_CONSTRUCTION_KEY;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.CRS_STATE_KEY;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.HINTS_KEY_SETS_KEY;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.NEXT_HINT_CONSTRUCTION_KEY;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.PREPROCESSING_VOTES_KEY;
import static com.hedera.node.app.roster.ActiveRosters.Phase.BOOTSTRAP;
import static com.hedera.node.app.roster.ActiveRosters.Phase.HANDOFF;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.hints.CRSState;
import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.HintsKeySet;
import com.hedera.hapi.node.state.hints.HintsPartyId;
import com.hedera.hapi.node.state.hints.HintsScheme;
import com.hedera.hapi.node.state.hints.NodePartyId;
import com.hedera.hapi.node.state.hints.PreprocessedKeys;
import com.hedera.hapi.node.state.hints.PreprocessingVote;
import com.hedera.hapi.node.state.hints.PreprocessingVoteId;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.hints.WritableHintsStore;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Default implementation of {@link WritableHintsStore}.
 */
public class WritableHintsStoreImpl extends ReadableHintsStoreImpl implements WritableHintsStore {
    private static final Comparator<NodePartyId> NODE_PARTY_ID_COMPARATOR =
            Comparator.comparingLong(NodePartyId::nodeId);

    private final WritableKVState<HintsPartyId, HintsKeySet> hintsKeys;
    private final WritableSingletonState<HintsConstruction> nextConstruction;
    private final WritableSingletonState<HintsConstruction> activeConstruction;
    private final WritableKVState<PreprocessingVoteId, PreprocessingVote> votes;
    private final WritableSingletonState<CRSState> crs;

    public WritableHintsStoreImpl(@NonNull final WritableStates states) {
        super(states);
        this.hintsKeys = states.get(HINTS_KEY_SETS_KEY);
        this.nextConstruction = states.getSingleton(NEXT_HINT_CONSTRUCTION_KEY);
        this.activeConstruction = states.getSingleton(ACTIVE_HINT_CONSTRUCTION_KEY);
        this.votes = states.get(PREPROCESSING_VOTES_KEY);
        this.crs = states.getSingleton(CRS_STATE_KEY);
    }

    @NonNull
    @Override
    public HintsConstruction getOrCreateConstruction(
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
                    ? tssConfig.bootstrapHintsKeyGracePeriod()
                    : tssConfig.transitionHintsKeyGracePeriod();
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
    public boolean setHintsKey(
            final long nodeId,
            final int partyId,
            final int numParties,
            @NonNull final Bytes hintsKey,
            @NonNull final Instant now) {
        final var id = new HintsPartyId(partyId, numParties);
        var keySet = hintsKeys.get(id);
        boolean inUse = false;
        if (keySet == null) {
            inUse = true;
            keySet = HintsKeySet.newBuilder()
                    .key(hintsKey)
                    .nodeId(nodeId)
                    .adoptionTime(asTimestamp(now))
                    .build();
        } else {
            keySet = keySet.copyBuilder().nodeId(nodeId).nextKey(hintsKey).build();
        }
        hintsKeys.put(id, keySet);
        return inUse;
    }

    @Override
    public void addPreprocessingVote(
            final long nodeId, final long constructionId, @NonNull final PreprocessingVote vote) {
        votes.put(new PreprocessingVoteId(constructionId, nodeId), vote);
    }

    @Override
    public HintsConstruction setHintsScheme(
            final long constructionId,
            @NonNull final PreprocessedKeys keys,
            @NonNull final Map<Long, Integer> nodePartyIds) {
        requireNonNull(keys);
        requireNonNull(nodePartyIds);
        return updateOrThrow(constructionId, b -> b.hintsScheme(new HintsScheme(keys, asList(nodePartyIds))));
    }

    @Override
    public HintsConstruction setPreprocessingStartTime(final long constructionId, @NonNull final Instant now) {
        requireNonNull(now);
        return updateOrThrow(constructionId, b -> b.preprocessingStartTime(asTimestamp(now)));
    }

    @Override
    public boolean purgeStateAfterHandoff(@NonNull final ActiveRosters activeRosters) {
        if (activeRosters.phase() != HANDOFF) {
            throw new IllegalArgumentException("Not in handoff phase");
        }
        if (requireNonNull(nextConstruction.get()).targetRosterHash().equals(activeRosters.currentRosterHash())) {
            // The next construction is becoming the active one; so purge obsolete votes now
            purgeVotes(requireNonNull(activeConstruction.get()), activeRosters::findRelatedRoster);
            // If the active construction's party size was different than the current roster's, purge its hinTS keys
            final int newActiveSize = partySizeForRoster(activeRosters.currentRoster());
            purgeHintsKeysIfNotForPartySize(
                    newActiveSize, requireNonNull(activeConstruction.get()), activeRosters::findRelatedRoster);
            activeConstruction.put(nextConstruction.get());
            nextConstruction.put(HintsConstruction.DEFAULT);
            return true;
        }
        return false;
    }

    public void setCrsState(@NonNull final CRSState state) {
        crs.put(state);
    }

    /**
     * Updates the construction with the given ID using the given spec.
     *
     * @param constructionId the construction ID
     * @param spec the spec
     * @return the updated construction
     */
    private HintsConstruction updateOrThrow(
            final long constructionId, @NonNull final UnaryOperator<HintsConstruction.Builder> spec) {
        HintsConstruction construction;
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
    private HintsConstruction updateForNewConstruction(
            @NonNull final Bytes sourceRosterHash,
            @NonNull final Bytes targetRosterHash,
            @NonNull final Function<Bytes, Roster> lookup,
            @NonNull final Instant now,
            @NonNull final Duration gracePeriod) {
        final var construction = HintsConstruction.newBuilder()
                .constructionId(newConstructionId())
                .sourceRosterHash(sourceRosterHash)
                .targetRosterHash(targetRosterHash)
                .gracePeriodEndTime(asTimestamp(now.plus(gracePeriod)))
                .build();
        if (requireNonNull(activeConstruction.get()).equals(HintsConstruction.DEFAULT)) {
            activeConstruction.put(construction);
        } else {
            if (!requireNonNull(nextConstruction.get()).equals(HintsConstruction.DEFAULT)) {
                // Before replacing the next construction, purge its votes
                purgeVotes(requireNonNull(nextConstruction.get()), lookup);
            }
            nextConstruction.put(construction);
        }
        // Rotate any hint keys requested to be used in the next construction
        final var targetRoster = requireNonNull(lookup.apply(targetRosterHash));
        final int numParties = partySizeForRoster(targetRoster);
        final var adoptionTime = asTimestamp(now);
        for (int partyId = 0; partyId < numParties; partyId++) {
            final var hintsId = new HintsPartyId(partyId, numParties);
            final var keySet = hintsKeys.get(hintsId);
            if (keySet != null && keySet.nextKey().length() > 0) {
                final var rotatedKeySet = keySet.copyBuilder()
                        .key(keySet.nextKey())
                        .adoptionTime(adoptionTime)
                        .nextKey(Bytes.EMPTY)
                        .build();
                hintsKeys.put(hintsId, rotatedKeySet);
            }
        }
        return construction;
    }

    /**
     * Purges the votes for the given construction relative to the given roster lookup.
     *
     * @param construction the construction
     * @param lookup the roster lookup
     */
    private void purgeVotes(
            @NonNull final HintsConstruction construction, @NonNull final Function<Bytes, Roster> lookup) {
        final var sourceRoster = requireNonNull(lookup.apply(construction.sourceRosterHash()));
        sourceRoster
                .rosterEntries()
                .forEach(entry -> votes.remove(new PreprocessingVoteId(construction.constructionId(), entry.nodeId())));
    }

    /**
     * Purges any hinTS keys for the given construction if it was not for the given party size.
     * @param m the party size
     * @param construction the construction
     * @param lookup the roster lookup
     */
    private void purgeHintsKeysIfNotForPartySize(
            final int m, @NonNull final HintsConstruction construction, @NonNull final Function<Bytes, Roster> lookup) {
        final var targetRoster = requireNonNull(lookup.apply(construction.targetRosterHash()));
        final int n = partySizeForRoster(targetRoster);
        if (n != m) {
            for (int partyId = 0; partyId < n; partyId++) {
                final var hintsId = new HintsPartyId(partyId, n);
                hintsKeys.remove(hintsId);
            }
        }
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

    /**
     * Internal helper to convert a map of node IDs to party IDs to a list of node party IDs.
     * @param nodePartyIds the map
     * @return the list
     */
    private List<NodePartyId> asList(@NonNull final Map<Long, Integer> nodePartyIds) {
        return nodePartyIds.entrySet().stream()
                .map(entry -> new NodePartyId(entry.getKey(), entry.getValue()))
                .sorted(NODE_PARTY_ID_COMPARATOR)
                .toList();
    }
}
