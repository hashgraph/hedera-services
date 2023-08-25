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

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.MOCK_VERIFICATION_STRATEGY;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleSystemContractOperations;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.ResultStatus;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleSystemContractOperationsTest {
    @Mock
    private HandleContext context;

    @Mock
    private ContractCallRecordBuilder recordBuilder;

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
    void getTokenNotImplementedYet() {
        assertThrows(AssertionError.class, () -> subject.getTokenAndExternalizeResult(1L, 2L, entity -> Bytes.EMPTY));
    }

    @Test
    void getAccountNotImplementedYet() {
        assertThrows(AssertionError.class, () -> subject.getAccountAndExternalizeResult(1L, 2L, entity -> Bytes.EMPTY));
    }

    @Test
    void getRelationshipNotImplementedYet() {
        assertThrows(
                AssertionError.class,
                () -> subject.getRelationshipAndExternalizeResult(1L, 2L, 3L, entity -> Bytes.EMPTY));
    }

    @Test
    void dispatchNotImplementedYet() {
        assertThrows(AssertionError.class, () -> subject.dispatch(TransactionBody.DEFAULT, MOCK_VERIFICATION_STRATEGY));
    }

    @Test
    void externalizeSuccessfulResultTest() {
        var contractFunctionResult = SystemContractUtils.contractFunctionResultSuccessFor(
                0, org.apache.tuweni.bytes.Bytes.EMPTY, ContractID.DEFAULT);

        // given
        given(context.addChildRecordBuilder(ContractCallRecordBuilder.class)).willReturn(recordBuilder);
        given(recordBuilder.status(ResponseCodeEnum.SUCCESS)).willReturn(recordBuilder);
        given(recordBuilder.contractID(ContractID.DEFAULT)).willReturn(recordBuilder);

        // when
        subject.externalizeResult(contractFunctionResult, ResultStatus.IS_SUCCESS);

        // then
        verify(recordBuilder).contractID(ContractID.DEFAULT);
        verify(recordBuilder).status(ResponseCodeEnum.SUCCESS);
        verify(recordBuilder).contractCallResult(contractFunctionResult);
    }

    @Test
    void externalizeFailedResultTest() {
        var contractFunctionResult = SystemContractUtils.contractFunctionResultSuccessFor(
                0, org.apache.tuweni.bytes.Bytes.EMPTY, ContractID.DEFAULT);

        // given
        given(context.addChildRecordBuilder(ContractCallRecordBuilder.class)).willReturn(recordBuilder);
        given(recordBuilder.status(ResponseCodeEnum.FAIL_INVALID)).willReturn(recordBuilder);
        given(recordBuilder.contractID(ContractID.DEFAULT)).willReturn(recordBuilder);

        // when
        subject.externalizeResult(contractFunctionResult, ResultStatus.IS_ERROR);

        // then
        verify(recordBuilder).contractID(ContractID.DEFAULT);
        verify(recordBuilder).status(ResponseCodeEnum.FAIL_INVALID);
        verify(recordBuilder).contractCallResult(contractFunctionResult);
    }
}
