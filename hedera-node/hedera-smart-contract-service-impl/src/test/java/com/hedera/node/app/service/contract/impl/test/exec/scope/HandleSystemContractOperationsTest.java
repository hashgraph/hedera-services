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

package com.hedera.node.app.service.contract.impl.test.exec.scope;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.AN_ED25519_KEY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleSystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy.Decision;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.ResultStatus;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleSystemContractOperationsTest {

    @Mock
    private HandleContext context;

    @Mock
    private ContractCallRecordBuilder recordBuilder;

    @Mock
    private ExchangeRateInfo exchangeRateInfo;

    @Mock
    private VerificationStrategy strategy;

    @Mock
    private SignatureVerification passed;

    @Mock
    private SignatureVerification failed;

    private HandleSystemContractOperations subject;

    @BeforeEach
    void setUp() {
        subject = new HandleSystemContractOperations(context);
    }

    @Test
    void getNftNotImplementedYet() {
        assertThrows(
                AssertionError.class,
                () -> subject.getNftAndExternalizeResult(NftID.DEFAULT, 1L, entity -> Bytes.EMPTY));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchesRespectingGivenStrategy() {
        final var captor = ArgumentCaptor.forClass(Predicate.class);
        given(strategy.decideFor(TestHelpers.A_CONTRACT_KEY)).willReturn(Decision.VALID);
        given(strategy.decideFor(AN_ED25519_KEY)).willReturn(Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION);
        given(strategy.decideFor(TestHelpers.B_SECP256K1_KEY))
                .willReturn(Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION);
        given(strategy.decideFor(TestHelpers.A_SECP256K1_KEY)).willReturn(Decision.INVALID);
        given(passed.passed()).willReturn(true);
        given(context.verificationFor(AN_ED25519_KEY)).willReturn(passed);
        given(context.verificationFor(TestHelpers.B_SECP256K1_KEY)).willReturn(failed);
        doCallRealMethod().when(strategy).asSignatureTestIn(context);

        subject.dispatch(TransactionBody.DEFAULT, strategy, A_NEW_ACCOUNT_ID, CryptoTransferRecordBuilder.class);

        verify(context)
                .dispatchChildTransaction(
                        eq(TransactionBody.DEFAULT),
                        eq(CryptoTransferRecordBuilder.class),
                        captor.capture(),
                        eq(A_NEW_ACCOUNT_ID));
        final var test = captor.getValue();
        assertTrue(test.test(TestHelpers.A_CONTRACT_KEY));
        assertTrue(test.test(AN_ED25519_KEY));
        assertFalse(test.test(TestHelpers.A_SECP256K1_KEY));
        assertFalse(test.test(TestHelpers.B_SECP256K1_KEY));
    }

    @Test
    void getTokenNotImplementedYet() {
        assertThrows(AssertionError.class, () -> subject.getTokenAndExternalizeResult(1L, 2L, entity -> Bytes.EMPTY));
    }

    @Test
    void getAccountNotImplementedYet() {
        assertThrows(AssertionError.class, () -> subject.getAccountAndExternalizeResult(1L, 2L, entity -> Bytes.EMPTY));
    }

    @Test
    void externalizeSuccessfulResultTest() {
        var contractFunctionResult = SystemContractUtils.contractFunctionResultSuccessFor(
                0, org.apache.tuweni.bytes.Bytes.EMPTY, ContractID.DEFAULT);

        // given
        given(context.addChildRecordBuilder(ContractCallRecordBuilder.class)).willReturn(recordBuilder);
        given(recordBuilder.transaction(Transaction.DEFAULT)).willReturn(recordBuilder);
        given(recordBuilder.status(ResponseCodeEnum.SUCCESS)).willReturn(recordBuilder);
        given(recordBuilder.contractID(ContractID.DEFAULT)).willReturn(recordBuilder);

        // when
        subject.externalizeResult(contractFunctionResult, ResultStatus.IS_SUCCESS);

        // then
        verify(recordBuilder).contractID(ContractID.DEFAULT);
        verify(recordBuilder).transaction(Transaction.DEFAULT);
        verify(recordBuilder).status(ResponseCodeEnum.SUCCESS);
        verify(recordBuilder).contractCallResult(contractFunctionResult);
    }

    @Test
    void externalizeFailedResultTest() {
        var contractFunctionResult = SystemContractUtils.contractFunctionResultSuccessFor(
                0, org.apache.tuweni.bytes.Bytes.EMPTY, ContractID.DEFAULT);

        // given
        given(context.addChildRecordBuilder(ContractCallRecordBuilder.class)).willReturn(recordBuilder);
        given(recordBuilder.transaction(Transaction.DEFAULT)).willReturn(recordBuilder);
        given(recordBuilder.status(ResponseCodeEnum.FAIL_INVALID)).willReturn(recordBuilder);
        given(recordBuilder.contractID(ContractID.DEFAULT)).willReturn(recordBuilder);

        // when
        subject.externalizeResult(contractFunctionResult, ResultStatus.IS_ERROR);

        // then
        verify(recordBuilder).contractID(ContractID.DEFAULT);
        verify(recordBuilder).transaction(Transaction.DEFAULT);
        verify(recordBuilder).status(ResponseCodeEnum.FAIL_INVALID);
        verify(recordBuilder).contractCallResult(contractFunctionResult);
    }

    @Test
    void currentExchangeRateTest() {
        given(context.exchangeRateInfo()).willReturn(exchangeRateInfo);
        subject.currentExchangeRate();
        verify(context).exchangeRateInfo();
        verify(exchangeRateInfo).activeRate(any());
    }
}
