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

import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.ACTIVE_CONSTRUCTION_KEY;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.HINTS_KEY;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.NEXT_CONSTRUCTION_KEY;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.PREPROCESSING_VOTES_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.HintsId;
import com.hedera.hapi.node.state.hints.HintsKey;
import com.hedera.hapi.node.state.hints.HintsKeySet;
import com.hedera.hapi.node.state.hints.PreprocessVoteId;
import com.hedera.hapi.node.state.hints.PreprocessedKeys;
import com.hedera.hapi.node.state.hints.PreprocessedKeysVote;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.UnaryOperator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Default implementation of {@link WritableHintsStore}.
 */
public class WritableHintsStoreImpl extends ReadableHintsStoreImpl implements WritableHintsStore {
    private static final Logger log = LogManager.getLogger(WritableHintsStoreImpl.class);

    private final WritableKVState<HintsId, HintsKeySet> hintsKeys;
    private final WritableSingletonState<HintsConstruction> nextConstruction;
    private final WritableSingletonState<HintsConstruction> activeConstruction;
    private final WritableKVState<PreprocessVoteId, PreprocessedKeysVote> votes;

    public WritableHintsStoreImpl(@NonNull WritableStates states) {
        super(states);
        this.hintsKeys = states.get(HINTS_KEY);
        this.nextConstruction = states.getSingleton(NEXT_CONSTRUCTION_KEY);
        this.activeConstruction = states.getSingleton(ACTIVE_CONSTRUCTION_KEY);
        this.votes = states.get(PREPROCESSING_VOTES_KEY);
    }

    @Override
    public HintsConstruction newConstructionFor(
            @NonNull final Bytes sourceRosterHash,
            @NonNull final Bytes targetRosterHash,
            @NonNull final ReadableRosterStore rosterStore) {
        requireNonNull(sourceRosterHash);
        requireNonNull(targetRosterHash);
        requireNonNull(rosterStore);
        final var currentConstruction = requireNonNull(activeConstruction.get());
        final var candidateConstruction = requireNonNull(nextConstruction.get());
        final long nextConstructionId =
                Math.max(currentConstruction.constructionId(), candidateConstruction.constructionId()) + 1;
        final var construction = HintsConstruction.newBuilder()
                .constructionId(nextConstructionId)
                .sourceRosterHash(sourceRosterHash)
                .targetRosterHash(targetRosterHash)
                .build();
        if (currentConstruction.equals(HintsConstruction.DEFAULT)) {
            if (!sourceRosterHash.equals(targetRosterHash)) {
                log.warn(
                        "Setting genesis construction with different source/target roster hashes ({} to {})",
                        sourceRosterHash,
                        targetRosterHash);
            }
            if (!candidateConstruction.equals(HintsConstruction.DEFAULT)) {
                log.warn(
                        "Purging next construction {} before setting active construction ({} to {})",
                        candidateConstruction,
                        sourceRosterHash,
                        targetRosterHash);
                purgeVotes(candidateConstruction, rosterStore);
            }
            activeConstruction.put(construction);
        } else {
            if (!currentConstruction.targetRosterHash().equals(construction.sourceRosterHash())) {
                log.warn(
                        "Setting next construction ({} to {}) with with active target {}",
                        sourceRosterHash,
                        targetRosterHash,
                        currentConstruction.targetRosterHash());
            }
            nextConstruction.put(construction);
        }
        return construction;
    }

    @Override
    public boolean includeHintsKey(
            final int k,
            final long partyId,
            final long nodeId,
            @NonNull final HintsKey hintsKey,
            @NonNull final Instant now) {
        final var id = new HintsId(partyId, k);
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
    public HintsConstruction completeAggregation(final long constructionId, @NonNull final PreprocessedKeys keys) {
        requireNonNull(keys);
        return updateOrThrow(constructionId, b -> b.preprocessedKeys(keys));
    }

    @Override
    public HintsConstruction setAggregationTime(final long constructionId, @NonNull final Instant now) {
        requireNonNull(now);
        return updateOrThrow(constructionId, b -> b.aggregationTime(asTimestamp(now)));
    }

    @Override
    public HintsConstruction rescheduleAggregationCheckpoint(final long constructionId, @NonNull final Instant then) {
        requireNonNull(then);
        return updateOrThrow(constructionId, b -> b.nextAggregationCheckpoint(asTimestamp(then)));
    }

    @Override
    public void purgeConstructionsNotFor(
            @NonNull final Bytes targetRosterHash, @NonNull ReadableRosterStore rosterStore) {
        requireNonNull(targetRosterHash);
        requireNonNull(rosterStore);
        if (requireNonNull(nextConstruction.get()).targetRosterHash().equals(targetRosterHash)) {
            purgeVotes(requireNonNull(activeConstruction.get()), rosterStore);
            activeConstruction.put(nextConstruction.get());
            nextConstruction.put(HintsConstruction.DEFAULT);
        }
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
     * Purges the votes for the given construction relative to the given roster store.
     *
     * @param construction the construction
     * @param rosterStore the roster store
     */
    private void purgeVotes(
            @NonNull final HintsConstruction construction, @NonNull final ReadableRosterStore rosterStore) {
        final var sourceRoster = requireNonNull(rosterStore.get(construction.sourceRosterHash()));
        sourceRoster
                .rosterEntries()
                .forEach(entry -> votes.remove(new PreprocessVoteId(construction.constructionId(), entry.nodeId())));
    }
}
