/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.logic;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class InvariantChecksTest {
    private final long now = 1_234_567L;
    private final long submittingMember = 1L;
    private final Instant lastConsensusTime = Instant.ofEpochSecond(now);
    private final Transaction mockTxn =
            Transaction.newBuilder()
                    .setBodyBytes(
                            TransactionBody.newBuilder()
                                    .setTransactionID(
                                            TransactionID.newBuilder()
                                                    .setAccountID(IdUtils.asAccount("0.0.2")))
                                    .build()
                                    .toByteString())
                    .build();
    private PlatformTxnAccessor accessor;

    @Mock private NodeInfo nodeInfo;
    @Mock private MerkleNetworkContext networkCtx;

    @LoggingTarget private LogCaptor logCaptor;

    @LoggingSubject private InvariantChecks subject;

    @BeforeEach
    void setUp() throws InvalidProtocolBufferException {
        final var swirldsTxn = new SwirldTransaction(mockTxn.toByteArray());
        accessor =
                PlatformTxnAccessor.from(
                        SignedTxnAccessor.from(swirldsTxn.getContents()), swirldsTxn);
        subject = new InvariantChecks(nodeInfo, () -> networkCtx);
    }

    @Test
    void rejectsNonIncreasing() {
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(lastConsensusTime);

        // when:
        final var result =
                subject.holdFor(accessor, lastConsensusTime.minusNanos(1L), submittingMember);

        // then:
        assertFalse(result);
        assertThat(logCaptor.errorLogs(), contains(Matchers.startsWith("Invariant failure!")));
    }

    @Test
    void okIfNeverHandledBefore() {
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(null);

        // when:
        final var result =
                subject.holdFor(accessor, lastConsensusTime.minusNanos(1L), submittingMember);

        // then:
        assertTrue(result);
    }

    @Test
    void okIfAfter() {
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(lastConsensusTime);

        // when:
        final var result =
                subject.holdFor(accessor, lastConsensusTime.plusNanos(1_000L), submittingMember);

        // then:
        assertTrue(result);
    }

    @Test
    void rejectsZeroStake() {
        given(nodeInfo.isZeroStake(submittingMember)).willReturn(true);

        // when:
        final var result =
                subject.holdFor(accessor, lastConsensusTime.plusNanos(1_000L), submittingMember);

        // then:
        assertFalse(result);
        assertThat(logCaptor.warnLogs(), contains(Matchers.startsWith("Invariant failure!")));
    }
}
