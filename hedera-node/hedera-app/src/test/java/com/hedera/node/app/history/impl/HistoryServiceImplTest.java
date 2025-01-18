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

package com.hedera.node.app.history.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.state.history.History;
import com.hedera.hapi.node.state.history.HistoryProof;
import com.hedera.node.app.history.HistoryLibrary;
import com.hedera.node.app.history.HistoryService;
import com.hedera.node.app.spi.AppContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.metrics.api.Metrics;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoryServiceImplTest {
    private static final Metrics NO_OP_METRICS = new NoOpMetrics();

    @Mock
    private AppContext appContext;

    @Mock
    private HistoryLibrary library;

    @Mock
    private HistoryLibraryCodec codec;

    private HistoryServiceImpl subject;

    @BeforeEach
    void setUp() {
        subject = new HistoryServiceImpl(NO_OP_METRICS, ForkJoinPool.commonPool(), appContext, library, codec);
    }

    @Test
    void metadataAsExpected() {
        assertEquals(HistoryService.NAME, subject.getServiceName());
        assertEquals(HistoryService.MIGRATION_ORDER, subject.migrationOrder());
    }

    @Test
    void onlyReadyGivenProof() {
        assertFalse(subject.isReady());
        subject.accept(HistoryProof.DEFAULT);
        assertTrue(subject.isReady());
    }

    @Test
    void refusesToProveMismatchedMetadata() {
        final var oldVk = Bytes.wrap("X");
        final var currentVk = Bytes.wrap("Z");
        final var currentProof = HistoryProof.newBuilder()
                .targetHistory(History.newBuilder().metadata(currentVk))
                .build();

        subject.accept(currentProof);
        assertThrows(IllegalArgumentException.class, () -> subject.getCurrentProof(oldVk));
        assertEquals(currentProof.proof(), subject.getCurrentProof(currentVk));
    }
}
