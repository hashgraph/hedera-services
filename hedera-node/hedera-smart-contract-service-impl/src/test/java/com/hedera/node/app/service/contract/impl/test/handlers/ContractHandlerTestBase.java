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

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.fixtures.TransactionFactory;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NewClassNamingConvention")
@ExtendWith(MockitoExtension.class)
public class ContractHandlerTestBase implements TransactionFactory {
    protected final AccountID payer = asAccount("0.0.3");
    protected final AccountID autoRenewAccountId = asAccount("0.0.10001");
    protected final Key payerKey = A_COMPLEX_KEY;
    protected final HederaKey payerHederaKey = asHederaKey(A_COMPLEX_KEY).orElseThrow();
    protected final Key adminKey = A_COMPLEX_KEY;
    protected final Key adminContractKey =
            Key.newBuilder().contractID(asContract("0.0.10002")).build();
    protected final Key autoRenewKey = A_COMPLEX_KEY;
    protected final Timestamp consensusTimestamp =
            Timestamp.newBuilder().seconds(1_234_567L).build();
    protected final ContractID targetContract = ContractID.newBuilder().contractNum(9_999L).build();

    @Mock protected MerkleAccount payerMerkleAccount;

    @Mock protected Account payerAccount;

    @Mock protected AccountAccess keyLookup;

    @BeforeEach
    void commonSetUp() {
        try {
            setUpPayer();
        } catch (PreCheckException e) {
            throw new RuntimeException(e);
        }
    }

    protected void basicMetaAssertions(final PreHandleContext context, final int nonPayerKeySize) {
        assertThat(context.requiredNonPayerKeys()).hasSize(nonPayerKeySize);
    }

    protected void setUpPayer() throws PreCheckException {
        lenient().when(keyLookup.getAccountById(payer)).thenReturn(payerAccount);
        lenient().when(payerAccount.key()).thenReturn(payerKey);
        lenient().when(payerMerkleAccount.getAccountKey()).thenReturn((JKey) payerHederaKey);
    }
}
