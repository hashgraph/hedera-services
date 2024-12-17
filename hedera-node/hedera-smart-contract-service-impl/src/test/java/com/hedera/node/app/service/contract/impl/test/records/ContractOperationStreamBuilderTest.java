/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.records;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractStateChange;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.node.app.service.contract.impl.exec.CallOutcome;
import com.hedera.node.app.service.contract.impl.records.ContractOperationStreamBuilder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractOperationStreamBuilderTest {
    @Mock
    private ContractOperationStreamBuilder subject;

    @BeforeEach
    void setUp() {
        doCallRealMethod().when(subject).withCommonFieldsSetFrom(any());
    }

    @Test
    void setsAllCommonFieldsIfPresent() {
        final var stateChanges = new ContractStateChanges(List.of(ContractStateChange.DEFAULT));
        final var outcome = new CallOutcome(
                ContractFunctionResult.newBuilder().gasUsed(1L).build(),
                ResponseCodeEnum.SUCCESS,
                ContractID.DEFAULT,
                123L,
                ContractActions.DEFAULT,
                stateChanges);
        final var builder = subject.withCommonFieldsSetFrom(outcome);

        verify(subject).transactionFee(123L);
        verify(subject).addContractActions(ContractActions.DEFAULT, false);
        verify(subject).addContractStateChanges(stateChanges, false);
        assertSame(subject, builder);
    }

    @Test
    void skipsCommonFieldsIfNotPresent() {
        final var outcome = new CallOutcome(
                ContractFunctionResult.newBuilder().gasUsed(1L).build(),
                ResponseCodeEnum.SUCCESS,
                ContractID.DEFAULT,
                123L,
                null,
                ContractStateChanges.DEFAULT);
        final var builder = subject.withCommonFieldsSetFrom(outcome);

        verify(subject).transactionFee(123L);
        verify(subject, never()).addContractActions(any(), anyBoolean());
        verify(subject, never()).addContractStateChanges(any(), anyBoolean());
        assertSame(subject, builder);
    }
}
