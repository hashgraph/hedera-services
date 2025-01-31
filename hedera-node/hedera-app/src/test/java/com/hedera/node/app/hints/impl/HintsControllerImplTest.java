/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.hints.HintsService.partySizeForRosterNodeCount;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.HintsScheme;
import com.hedera.hapi.node.state.hints.PreprocessedKeys;
import com.hedera.node.app.hints.HintsLibrary;
import com.hedera.node.app.hints.ReadableHintsStore.HintsKeyPublication;
import com.hedera.node.app.hints.WritableHintsStore;
import com.hedera.node.app.roster.RosterTransitionWeights;
import com.hedera.node.app.tss.TssKeyPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HintsControllerImplTest {
    private static final int TARGET_ROSTER_SIZE = 13;
    private static final int EXPECTED_PARTY_SIZE = partySizeForRosterNodeCount(TARGET_ROSTER_SIZE);
    private static final long SELF_ID = 42L;
    private static final long CONSTRUCTION_ID = 123L;
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);
    private static final Instant PREPROCESSING_START_TIME = Instant.ofEpochSecond(1_111_111L, 222);
    private static final Bytes ENCODED_PREPROCESSED_KEYS = Bytes.wrap("EPK");
    private static final PreprocessedKeys PREPROCESSED_KEYS = new PreprocessedKeys(Bytes.wrap("AK"), Bytes.wrap("VK"));
    private static final TssKeyPair BLS_KEY_PAIR = new TssKeyPair(Bytes.EMPTY, Bytes.EMPTY);
    private static final HintsKeyPublication KEY_PUBLICATION =
            new HintsKeyPublication(1L, Bytes.EMPTY, 2, CONSENSUS_NOW);
    private static final HintsConstruction UNFINISHED_CONSTRUCTION = HintsConstruction.newBuilder()
            .constructionId(CONSTRUCTION_ID)
            .gracePeriodEndTime(asTimestamp(CONSENSUS_NOW))
            .build();
    private static final HintsConstruction CONSTRUCTION_WITH_START_TIME = HintsConstruction.newBuilder()
            .constructionId(CONSTRUCTION_ID)
            .preprocessingStartTime(asTimestamp(PREPROCESSING_START_TIME))
            .build();
    private static final HintsConstruction FINISHED_CONSTRUCTION = HintsConstruction.newBuilder()
            .constructionId(CONSTRUCTION_ID)
            .hintsScheme(HintsScheme.DEFAULT)
            .build();
    private static final HintsKeyPublication EXPECTED_NODE_ONE_PUBLICATION =
            new HintsKeyPublication(1L, Bytes.wrap("ONE"), 0, PREPROCESSING_START_TIME.minusSeconds(1));
    private static final HintsKeyPublication UNEXPECTED_NODE_ONE_PUBLICATION =
            new HintsKeyPublication(1L, Bytes.wrap("ONE"), 15, PREPROCESSING_START_TIME.minusSeconds(1));
    private static final HintsKeyPublication TARDY_NODE_TWO_PUBLICATION =
            new HintsKeyPublication(2L, Bytes.wrap("TWO"), 1, PREPROCESSING_START_TIME.plusSeconds(1));
    private static final Map<Long, Long> TARGET_NODE_WEIGHTS = Map.of(1L, 8L, 2L, 2L);

    @Mock
    private HintsLibrary library;

    @Mock
    private HintsLibraryCodec codec;

    @Mock
    private HintsSubmissions submissions;

    @Mock
    private HintsContext context;

    @Mock
    private RosterTransitionWeights weights;

    @Mock
    private WritableHintsStore store;

    private final Deque<Runnable> scheduledTasks = new ArrayDeque<>();

    private HintsControllerImpl subject;

    @Test
    void returnsConstructionIdForUnfinished() {
        setupWith(UNFINISHED_CONSTRUCTION);

        assertEquals(UNFINISHED_CONSTRUCTION.constructionId(), subject.constructionId());
        assertTrue(subject.isStillInProgress());
    }

    @Test
    void finishedIsNotInProgressAndDoesNothing() {
        setupWith(FINISHED_CONSTRUCTION);

        assertFalse(subject.isStillInProgress());

        subject.advanceConstruction(CONSENSUS_NOW, store);

        assertTrue(scheduledTasks.isEmpty());
    }

    @Test
    void onlyMatchesExpectedNumParties() {
        setupWith(UNFINISHED_CONSTRUCTION);

        assertFalse(subject.hasNumParties(EXPECTED_PARTY_SIZE - 1));
        assertTrue(subject.hasNumParties(EXPECTED_PARTY_SIZE));
    }

    @Test
    void ignoresKeyPublicationIfNotInGracePeriod() {
        setupWith(FINISHED_CONSTRUCTION);

        subject.addHintsKeyPublication(EXPECTED_NODE_ONE_PUBLICATION);

        verifyNoMoreInteractions(weights);
    }

    @Test
    void ignoresKeyPublicationGivenWrongPartyId() {
        setupWith(UNFINISHED_CONSTRUCTION);
        given(weights.targetNodeWeights()).willReturn(TARGET_NODE_WEIGHTS);

        subject.addHintsKeyPublication(UNEXPECTED_NODE_ONE_PUBLICATION);

        verifyNoMoreInteractions(weights);
    }

    @Test
    void setsNodeIdsAndSchedulesVerificationForExpectedPartyId() {
        setupWith(UNFINISHED_CONSTRUCTION);
        given(weights.targetNodeWeights()).willReturn(TARGET_NODE_WEIGHTS);

        subject.addHintsKeyPublication(EXPECTED_NODE_ONE_PUBLICATION);

        final var task = scheduledTasks.poll();
        assertNotNull(task);
        task.run();
        verify(library)
                .validateHintsKey(
                        EXPECTED_NODE_ONE_PUBLICATION.hintsKey(),
                        EXPECTED_NODE_ONE_PUBLICATION.partyId(),
                        EXPECTED_PARTY_SIZE);
        assertEquals(OptionalInt.empty(), subject.partyIdOf(1L));
        given(weights.targetIncludes(1L)).willReturn(true);
        assertEquals(OptionalInt.of(0), subject.partyIdOf(1L));
        given(weights.targetIncludes(2L)).willReturn(true);
        assertEquals(OptionalInt.of(1), subject.partyIdOf(2L));
    }

    @Test
    void schedulesPreprocessingWithQualifiedHintsKeysIfProcessingStartTimeIsSetButDoesNotScheduleTwice() {
        given(weights.targetNodeWeights()).willReturn(TARGET_NODE_WEIGHTS);
        setupWith(CONSTRUCTION_WITH_START_TIME, List.of(EXPECTED_NODE_ONE_PUBLICATION, TARDY_NODE_TWO_PUBLICATION));
        given(library.validateHintsKey(any(), anyInt(), anyInt())).willReturn(true);
        runScheduledTasks();

        given(library.preprocess(
                        Map.of(0, EXPECTED_NODE_ONE_PUBLICATION.hintsKey()),
                        Map.of(0, TARGET_NODE_WEIGHTS.get(1L)),
                        EXPECTED_PARTY_SIZE))
                .willReturn(ENCODED_PREPROCESSED_KEYS);
        given(codec.decodePreprocessedKeys(ENCODED_PREPROCESSED_KEYS)).willReturn(PREPROCESSED_KEYS);
        given(submissions.submitHintsVote(CONSTRUCTION_ID, PREPROCESSED_KEYS))
                .willReturn(CompletableFuture.completedFuture(null));

        subject.advanceConstruction(CONSENSUS_NOW, store);

        final var task = scheduledTasks.poll();
        assertNotNull(task);
        given(weights.targetWeightOf(1L)).willReturn(TARGET_NODE_WEIGHTS.get(1L));
        task.run();

        verify(submissions).submitHintsVote(CONSTRUCTION_ID, PREPROCESSED_KEYS);

        subject.advanceConstruction(CONSENSUS_NOW, store);
        assertTrue(scheduledTasks.isEmpty());
    }

    private void setupWith(@NonNull final HintsConstruction construction) {
        setupWith(construction, List.of());
    }

    private void setupWith(
            @NonNull final HintsConstruction construction, @NonNull final List<HintsKeyPublication> publications) {
        given(weights.targetRosterSize()).willReturn(TARGET_ROSTER_SIZE);
        subject = new HintsControllerImpl(
                SELF_ID,
                BLS_KEY_PAIR,
                construction,
                weights,
                scheduledTasks::offer,
                library,
                codec,
                Map.of(),
                publications,
                submissions,
                context);
    }

    private void runScheduledTasks() {
        Runnable task;
        while ((task = scheduledTasks.poll()) != null) {
            task.run();
        }
    }
}
