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

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.PreprocessingVote;
import com.hedera.node.app.hints.HintsLibrary;
import com.hedera.node.app.hints.ReadableHintsStore;
import com.hedera.node.app.hints.WritableHintsStore;
import com.hedera.node.app.roster.RosterTransitionWeights;
import com.hedera.node.app.tss.TssKeyPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HintsControllerImplTest {
    private static final long SELF_ID = 42L;
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);
    private static final TssKeyPair BLS_KEY_PAIR = new TssKeyPair(Bytes.EMPTY, Bytes.EMPTY);
    private static final ReadableHintsStore.HintsKeyPublication KEY_PUBLICATION =
            new ReadableHintsStore.HintsKeyPublication(1L, Bytes.EMPTY, 2, CONSENSUS_NOW);

    @Mock
    private Executor executor;

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

    private HintsControllerImpl subject;

    @BeforeEach
    void setUp() {
        subject = new HintsControllerImpl(
                SELF_ID,
                BLS_KEY_PAIR,
                HintsConstruction.DEFAULT,
                weights,
                executor,
                library,
                codec,
                Map.of(),
                List.of(),
                submissions,
                context);
    }

    @Test
    void nothingSupportedYet() {
        assertThrows(UnsupportedOperationException.class, subject::constructionId);
        assertThrows(UnsupportedOperationException.class, subject::isStillInProgress);
        assertThrows(UnsupportedOperationException.class, subject::cancelPendingWork);
        assertThrows(UnsupportedOperationException.class, () -> subject.hasNumParties(1));
        assertThrows(UnsupportedOperationException.class, () -> subject.advanceConstruction(CONSENSUS_NOW, store));
        assertThrows(UnsupportedOperationException.class, () -> subject.partyIdOf(1L));
        assertThrows(UnsupportedOperationException.class, () -> subject.addHintsKeyPublication(KEY_PUBLICATION));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.addPreprocessingVote(1L, PreprocessingVote.DEFAULT, store));
    }
}
