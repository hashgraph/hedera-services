/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts.precompile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.hedera.node.app.service.mono.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.RedirectPrecompile;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedirectPrecompileTest {

    @Mock Precompile wrappedPrecompile;

    @Mock WorldLedgers worldLedgers;

    @Mock TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;

    @Mock MessageFrame messageFrame;

    TokenID tokenID = TokenID.newBuilder().setTokenNum(31415L).build();

    RedirectPrecompile subject;

    @BeforeEach
    void setUp() {
        subject = new RedirectPrecompile(wrappedPrecompile, worldLedgers, tokenID);
    }

    @Test
    void runDelegatesWhenTokenExists() {
        given(worldLedgers.tokens()).willReturn(tokens);
        given(tokens.exists(tokenID)).willReturn(true);

        subject.run(messageFrame);

        verify(wrappedPrecompile).run(messageFrame);
    }

    @Test
    void runThrowsWhenTokenDoesNotExist() {
        given(worldLedgers.tokens()).willReturn(tokens);
        given(tokens.exists(tokenID)).willReturn(false);

        assertThrows(InvalidTransactionException.class, () -> subject.run(messageFrame));

        verify(wrappedPrecompile, never()).run(messageFrame);
    }

    @Test
    void delegatesBody() {
        final var input = Bytes.of(11);
        final UnaryOperator<byte[]> unaryOperator = a -> a;
        subject.body(input, unaryOperator);

        verify(wrappedPrecompile).body(input, unaryOperator);
    }

    @Test
    void delegatesGetMinimumFee() {
        final Timestamp timestamp = mock(Timestamp.class);

        subject.getMinimumFeeInTinybars(timestamp);

        verify(wrappedPrecompile).getMinimumFeeInTinybars(timestamp);
    }

    @Test
    void delegatesGetGasRequirement() {
        final var blockTimestamp = 2L;

        subject.getGasRequirement(blockTimestamp);

        verify(wrappedPrecompile).getGasRequirement(blockTimestamp);
    }

    @Test
    void delegatesGetSuccessResult() {
        final var builder = new ExpirableTxnRecord.Builder();

        subject.getSuccessResultFor(builder);

        verify(wrappedPrecompile).getSuccessResultFor(builder);
    }

    @Test
    void delegatesGetFailureResult() {
        subject.getFailureResultFor(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED);

        verify(wrappedPrecompile).getFailureResultFor(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED);
    }

    @Test
    void delegatesCustomizeTrackingLedgers() {
        subject.customizeTrackingLedgers(worldLedgers);

        verify(wrappedPrecompile).customizeTrackingLedgers(worldLedgers);
    }

    @Test
    void delegatesGetCustomFees() {
        subject.getCustomFees();

        verify(wrappedPrecompile).getCustomFees();
    }

    @Test
    void delegatesAddImplicitCostsIn() {
        final var accessor = mock(TxnAccessor.class);

        subject.addImplicitCostsIn(accessor);

        verify(wrappedPrecompile).addImplicitCostsIn(accessor);
    }

    @Test
    void delegatesShouldExportTraceability() {
        subject.shouldAddTraceabilityFieldsToRecord();

        verify(wrappedPrecompile).shouldAddTraceabilityFieldsToRecord();
    }
}
