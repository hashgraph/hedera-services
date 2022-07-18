/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

import com.swirlds.common.system.Round;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessLogicTest {
    private static final Instant then = Instant.ofEpochSecond(1_234_567, 890);

    @Mock private Round round;
    @Mock private ProcessLogic subject;

    private List<ConsensusTransaction> mockTxns = new ArrayList<>();

    @BeforeEach
    void setUp() {
        doCallRealMethod().when(subject).incorporateConsensus(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void incorporatesFullRound() {
        final var inOrder = Mockito.inOrder(subject);

        final var roundMetadata =
                List.of(
                        Pair.of(then.plusNanos(1000), 0L),
                        Pair.of(then.plusNanos(2000), 1L),
                        Pair.of(then.plusNanos(3000), 2L));
        givenRoundWith(roundMetadata.toArray(Pair[]::new));

        subject.incorporateConsensus(round);

        for (int i = 0, n = roundMetadata.size(); i < n; i++) {
            inOrder.verify(subject)
                    .incorporateConsensusTxn(
                            mockTxns.get(i),
                            roundMetadata.get(i).getLeft(),
                            roundMetadata.get(i).getRight());
        }
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    private void givenRoundWith(Pair<Instant, Long>... metadata) {
        Mockito.doAnswer(
                        invocationOnMock -> {
                            final var observer =
                                    (BiConsumer<ConsensusEvent, ConsensusTransaction>)
                                            invocationOnMock.getArgument(0);
                            for (int i = 0; i < metadata.length; i++) {
                                final var event = mock(ConsensusEvent.class);
                                given(event.getCreatorId()).willReturn(metadata[i].getRight());
                                final var txn = new SwirldTransaction();
                                txn.setConsensusTimestamp(metadata[i].getLeft());
                                mockTxns.add(txn);
                                observer.accept(event, txn);
                            }
                            return null;
                        })
                .when(round)
                .forEachEventTransaction(any());
    }
}
