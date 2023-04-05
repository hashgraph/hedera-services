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

package com.hedera.node.app.service.contract.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallHandler;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractCallHandlerTest extends ContractHandlerTestBase {
    private final ContractCallHandler subject = new ContractCallHandler();

    @Test
    @DisplayName("Succeeds for valid payer account")
    void validPayer() {
        final var txn = contractCallTransaction();
        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 0, false, OK);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
    }

    private TransactionBody contractCallTransaction() {
        final var transactionID = TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .contractCall(ContractCallTransactionBody.newBuilder()
                        .gas(1_234)
                        .amount(1_234L)
                        .contractID(targetContract))
                .build();
    }
}
