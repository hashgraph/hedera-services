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
package com.hedera.services.txns;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class TransitionRunnerTest {
    private final Transaction mockTxn = Transaction.getDefaultInstance();
    private final TransactionBody mockBody = TransactionBody.getDefaultInstance();

    @Mock private EntityIdSource ids;
    @Mock private TxnAccessor accessor;
    @Mock private TransitionLogic logic;
    @Mock private TransactionContext txnCtx;
    @Mock private TransitionLogicLookup lookup;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private TransitionRunner subject;

    @BeforeEach
    void setUp() {
        subject = new TransitionRunner(ids, txnCtx, lookup);
    }

    @Test
    void abortsOnMissingLogic() {
        given(accessor.getFunction()).willReturn(TokenMint);
        given(accessor.getTxn()).willReturn(mockBody);
        given(accessor.getSignedTxnWrapper()).willReturn(mockTxn);
        given(lookup.lookupFor(TokenMint, mockBody)).willReturn(Optional.empty());

        // when:
        var result = subject.tryTransition(accessor);

        // then:
        assertThat(
                logCaptor.warnLogs(),
                contains("Transaction w/o applicable transition logic at consensus ::"));
        verify(txnCtx).setStatus(FAIL_INVALID);
        assertFalse(result);
    }

    @Test
    void abortsOnFailedSemanticCheck() {
        given(accessor.getFunction()).willReturn(TokenMint);
        given(accessor.getTxn()).willReturn(mockBody);
        given(lookup.lookupFor(TokenMint, mockBody)).willReturn(Optional.of(logic));
        given(logic.validateSemantics(accessor)).willReturn(INVALID_TOKEN_ID);

        // when:
        var result = subject.tryTransition(accessor);

        // then:
        verify(txnCtx).setStatus(INVALID_TOKEN_ID);
        assertFalse(result);
    }

    @Test
    void catchesInvalidTxnExceptionAndSetsStatus() {
        given(accessor.getFunction()).willReturn(TokenMint);
        given(accessor.getTxn()).willReturn(mockBody);
        given(lookup.lookupFor(TokenMint, mockBody)).willReturn(Optional.of(logic));
        given(logic.validateSemantics(accessor)).willReturn(OK);
        willThrow(new InvalidTransactionException(INVALID_TOKEN_MINT_AMOUNT))
                .given(logic)
                .doStateTransition();

        // when:
        var result = subject.tryTransition(accessor);

        // then:
        verify(txnCtx).setStatus(INVALID_TOKEN_MINT_AMOUNT);
        verify(ids).reclaimProvisionalIds();
        assertTrue(result);
    }

    @Test
    void logsWarningIfInvalidTxnExceptionWasFailInvalid() {
        given(accessor.getFunction()).willReturn(TokenMint);
        given(accessor.getTxn()).willReturn(mockBody);
        given(accessor.getSignedTxnWrapper()).willReturn(mockTxn);
        given(lookup.lookupFor(TokenMint, mockBody)).willReturn(Optional.of(logic));
        given(logic.validateSemantics(accessor)).willReturn(OK);
        willThrow(new InvalidTransactionException("Yikes!", FAIL_INVALID))
                .given(logic)
                .doStateTransition();

        var result = subject.tryTransition(accessor);

        verify(txnCtx).setStatus(FAIL_INVALID);
        assertThat(
                logCaptor.warnLogs(),
                contains(
                        startsWith(
                                "Avoidable failure while handling"
                                    + " com.hedera.services.exceptions.InvalidTransactionException:"
                                    + " Yikes!")));
        assertTrue(result);
    }

    @Test
    void setsSuccessOnHappyPath() {
        given(accessor.getFunction()).willReturn(TokenMint);
        given(accessor.getTxn()).willReturn(mockBody);
        given(lookup.lookupFor(TokenMint, mockBody)).willReturn(Optional.of(logic));
        given(logic.validateSemantics(accessor)).willReturn(OK);

        // when:
        var result = subject.tryTransition(accessor);

        // then:
        verify(txnCtx).setStatus(SUCCESS);
        assertTrue(result);
    }

    @Test
    void reclaimsIdsOnFailedTransaction() {
        given(accessor.getFunction()).willReturn(TokenCreate);
        given(accessor.getTxn()).willReturn(mockBody);
        given(lookup.lookupFor(TokenCreate, mockBody)).willReturn(Optional.of(logic));
        given(logic.validateSemantics(accessor)).willReturn(OK);

        doThrow(new InvalidTransactionException(FAIL_INVALID)).when(logic).doStateTransition();

        subject.tryTransition(accessor);

        verify(txnCtx).setStatus(FAIL_INVALID);
        verify(ids).reclaimProvisionalIds();
    }

    @Test
    void rethrowsException() {
        // given:
        given(accessor.getFunction()).willReturn(TokenCreate);
        given(accessor.getTxn()).willReturn(mockBody);
        given(lookup.lookupFor(TokenCreate, mockBody)).willReturn(Optional.of(logic));
        given(logic.validateSemantics(accessor)).willReturn(OK);
        // and:
        doThrow(new RuntimeException()).when(logic).doStateTransition();

        // when:
        assertThrows(RuntimeException.class, () -> subject.tryTransition(accessor));

        // then:
        verify(txnCtx, never()).setStatus(FAIL_INVALID);
        verify(ids).reclaimProvisionalIds();
    }
}
