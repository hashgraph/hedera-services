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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.PreprocessedKeys;
import com.hedera.node.app.hints.impl.HintsConstructionController;
import com.hedera.node.app.hints.impl.HintsConstructionControllers;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HintsServiceImplTest {
    private static final Bytes A_MOCK_HASH = Bytes.wrap("A");
    private static final Bytes B_MOCK_HASH = Bytes.wrap("B");
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);
    private static final HintsConstruction FAKE_COMPLETE_CONSTRUCTION = HintsConstruction.newBuilder()
            .preprocessedKeys(PreprocessedKeys.DEFAULT)
            .build();

    @Mock
    private ReadableRosterStore rosterStore;

    @Mock
    private WritableHintsStore hintsStore;

    @Mock
    private HintsConstructionController controller;

    @Mock
    private HintsConstructionControllers controllers;

    @Mock
    private HintsServiceComponent component;

    private HintsServiceImpl subject;

    @BeforeEach
    void setUp() {
        subject = new HintsServiceImpl(component);
    }

    @Test
    void usesPreviousHashWithNoCandidateRosterHashAndAdvancesControllerForIncompleteConstruction() {
        given(rosterStore.getPreviousRosterHash()).willReturn(B_MOCK_HASH);
        given(rosterStore.getCandidateRosterHash()).willReturn(null);
        given(rosterStore.getCurrentRosterHash()).willReturn(A_MOCK_HASH);
        given(hintsStore.getConstructionFor(B_MOCK_HASH, A_MOCK_HASH)).willReturn(null);
        given(hintsStore.newConstructionFor(B_MOCK_HASH, A_MOCK_HASH, rosterStore))
                .willReturn(HintsConstruction.DEFAULT);
        given(component.controllers()).willReturn(controllers);
        given(controllers.getOrCreateControllerFor(HintsConstruction.DEFAULT, rosterStore))
                .willReturn(controller);

        subject.reconcile(CONSENSUS_NOW, rosterStore, hintsStore);

        verify(controller).advanceConstruction(CONSENSUS_NOW, hintsStore);
    }

    @Test
    void usesCandidateRosterHashIfPresentAndAdvancesControllerForIncompleteConstruction() {
        given(rosterStore.getCandidateRosterHash()).willReturn(B_MOCK_HASH);
        given(rosterStore.getCurrentRosterHash()).willReturn(A_MOCK_HASH);
        given(hintsStore.getConstructionFor(A_MOCK_HASH, B_MOCK_HASH)).willReturn(HintsConstruction.DEFAULT);
        given(component.controllers()).willReturn(controllers);
        given(controllers.getOrCreateControllerFor(HintsConstruction.DEFAULT, rosterStore))
                .willReturn(controller);

        subject.reconcile(CONSENSUS_NOW, rosterStore, hintsStore);

        verify(controller).advanceConstruction(CONSENSUS_NOW, hintsStore);
    }

    @Test
    void doesNotCreateControllerForCompleteConstruction() {
        given(rosterStore.getCandidateRosterHash()).willReturn(B_MOCK_HASH);
        given(rosterStore.getCurrentRosterHash()).willReturn(A_MOCK_HASH);
        given(hintsStore.getConstructionFor(A_MOCK_HASH, B_MOCK_HASH)).willReturn(FAKE_COMPLETE_CONSTRUCTION);

        subject.reconcile(CONSENSUS_NOW, rosterStore, hintsStore);

        verify(hintsStore, never()).newConstructionFor(any(), any(), any());
    }

    @Test
    void purgesOtherConstructionsOnCompletionWithNoCandidateRoster() {
        given(rosterStore.getCurrentRosterHash()).willReturn(A_MOCK_HASH);
        given(hintsStore.getConstructionFor(null, A_MOCK_HASH)).willReturn(FAKE_COMPLETE_CONSTRUCTION);

        subject.reconcile(CONSENSUS_NOW, rosterStore, hintsStore);

        verify(hintsStore).purgeConstructionsNotFor(A_MOCK_HASH);
    }
}
