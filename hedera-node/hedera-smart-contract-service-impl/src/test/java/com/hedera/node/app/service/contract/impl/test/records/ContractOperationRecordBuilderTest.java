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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractBytecode;
import com.hedera.hapi.streams.ContractStateChange;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.node.app.service.contract.impl.exec.CallOutcome;
import com.hedera.node.app.service.contract.impl.records.ContractOperationRecordBuilder;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class ContractOperationRecordBuilderTest {
    @Test
    void withGasFeeWorksAsExpected() {
        final var subject = new ContractOperationRecordBuilder() {
            private long totalFee = 456L;
            private ContractActions actions = null;
            private ContractStateChanges stateChanges = null;

            @Override
            public long transactionFee() {
                return totalFee;
            }

            @NotNull
            @Override
            public TransactionBody transactionBody() {
                return TransactionBody.DEFAULT;
            }

            @Override
            public ContractOperationRecordBuilder transactionFee(final long transactionFee) {
                totalFee = transactionFee;
                return this;
            }

            @NonNull
            @Override
            public ContractOperationRecordBuilder addContractActions(
                    @NonNull ContractActions contractActions, boolean isMigration) {
                this.actions = contractActions;
                return this;
            }

            @NonNull
            @Override
            public ContractOperationRecordBuilder addContractBytecode(
                    @NonNull ContractBytecode contractBytecode, boolean isMigration) {
                return this;
            }

            @NonNull
            @Override
            public ContractOperationRecordBuilder addContractStateChanges(
                    @NonNull ContractStateChanges contractStateChanges, boolean isMigration) {
                stateChanges = contractStateChanges;
                return this;
            }

            @Override
            public int getNumberOfDeletedAccounts() {
                return 0;
            }

            @Nullable
            @Override
            public AccountID getDeletedAccountBeneficiaryFor(@NonNull AccountID deletedAccountID) {
                return null;
            }

            @Override
            public void addBeneficiaryForDeletedAccount(
                    @NonNull AccountID deletedAccountID, @NonNull AccountID beneficiaryForDeletedAccount) {
                // No-op
            }

            @NonNull
            @Override
            public ResponseCodeEnum status() {
                return ResponseCodeEnum.SUCCESS;
            }

            @Override
            public SingleTransactionRecordBuilder status(@NonNull ResponseCodeEnum status) {
                return this;
            }
        };

        final var outcomeWithoutSidecars = new CallOutcome(
                ContractFunctionResult.newBuilder().gasUsed(1L).build(),
                ResponseCodeEnum.SUCCESS,
                ContractID.DEFAULT,
                123L,
                null,
                null);
        final var actions = new ContractActions(List.of(ContractAction.DEFAULT));
        final var stateChanges = new ContractStateChanges(List.of(ContractStateChange.DEFAULT));
        final var outcomeWithSidecars = new CallOutcome(
                ContractFunctionResult.newBuilder().gasUsed(1L).build(),
                ResponseCodeEnum.SUCCESS,
                ContractID.DEFAULT,
                123L,
                actions,
                stateChanges);
        assertSame(subject, subject.withCommonFieldsSetFrom(outcomeWithoutSidecars));
        assertSame(subject, subject.withCommonFieldsSetFrom(outcomeWithSidecars));
        assertEquals(456L + 2 * 123L, subject.transactionFee());
        assertSame(actions, subject.actions);
        assertSame(stateChanges, subject.stateChanges);
    }
}
