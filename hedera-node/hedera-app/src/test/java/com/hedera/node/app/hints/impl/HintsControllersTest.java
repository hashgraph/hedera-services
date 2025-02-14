// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.hints.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.hints.CRSState;
import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.node.app.hints.HintsLibrary;
import com.hedera.node.app.hints.WritableHintsStore;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.app.roster.RosterTransitionWeights;
import com.hedera.node.app.tss.TssKeyPair;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.info.NodeInfo;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HintsControllersTest {
    private static final TssKeyPair BLS_KEY_PAIR = new TssKeyPair(Bytes.EMPTY, Bytes.EMPTY);
    private static final Instant now = Instant.ofEpochSecond(1_234_567L);

    private static final HintsConstruction ONE_CONSTRUCTION =
            HintsConstruction.newBuilder().constructionId(1L).build();

    @Mock
    private Executor executor;

    @Mock
    private HintsKeyAccessor keyAccessor;

    @Mock
    private NodeInfo selfNodeInfo;

    @Mock
    private HintsLibrary library;

    @Mock
    private HintsLibraryCodec codec;

    @Mock
    private HintsSubmissions submissions;

    @Mock
    private Supplier<NodeInfo> selfNodeInfoSupplier;

    @Mock
    private ActiveRosters activeRosters;

    @Mock
    private RosterTransitionWeights weights;

    @Mock
    private WritableHintsStore hintsStore;

    @Mock
    private HintsContext context;

    private HintsControllers subject;

    @BeforeEach
    void setUp() {
        subject = new HintsControllers(
                executor,
                keyAccessor,
                library,
                codec,
                submissions,
                context,
                selfNodeInfoSupplier,
                HederaTestConfigBuilder::createConfig);
    }

    @Test
    void getsAndCreatesInertControllersAsExpected() {
        given(activeRosters.transitionWeights()).willReturn(weights);

        final var twoConstruction =
                HintsConstruction.newBuilder().constructionId(2L).build();

        assertTrue(subject.getInProgressById(2L).isEmpty());
        final var firstController = subject.getOrCreateFor(activeRosters, ONE_CONSTRUCTION, hintsStore);
        assertTrue(subject.getInProgressById(1L).isEmpty());
        assertTrue(subject.getInProgressById(2L).isEmpty());
        assertInstanceOf(InertHintsController.class, firstController);
        final var secondController = subject.getOrCreateFor(activeRosters, twoConstruction, hintsStore);
        assertNotSame(firstController, secondController);
        assertInstanceOf(InertHintsController.class, secondController);
    }

    @Test
    void returnsActiveControllerWhenSourceNodesHaveTargetThresholdWeight() {
        given(activeRosters.transitionWeights()).willReturn(weights);
        given(weights.sourceNodesHaveTargetThreshold()).willReturn(true);
        given(keyAccessor.getOrCreateBlsKeyPair(1L)).willReturn(BLS_KEY_PAIR);
        given(selfNodeInfoSupplier.get()).willReturn(selfNodeInfo);
        given(hintsStore.getCrsState()).willReturn(CRSState.DEFAULT);

        final var controller = subject.getOrCreateFor(activeRosters, ONE_CONSTRUCTION, hintsStore);

        assertInstanceOf(HintsControllerImpl.class, controller);

        assertDoesNotThrow(() -> subject.stop());
        assertDoesNotThrow(() -> subject.stop());
    }
}
