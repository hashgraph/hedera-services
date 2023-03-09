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

import static org.mockito.Mockito.lenient;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.node.app.service.mono.Utils;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.fixtures.TransactionFactory;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.test.utils.KeyUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ContractHandlerTestBase implements TransactionFactory {
    protected static final String ACCOUNTS = "ACCOUNTS";
    protected static final String ALIASES = "ALIASES";
    protected final AccountID payer = asAccount("0.0.3");
    protected final AccountID autoRenewAccountId = asAccount("0.0.10001");
    protected final HederaKey payerKey =
            Utils.asHederaKey(KeyUtils.A_COMPLEX_KEY).get();
    protected final Key adminKey = PbjConverter.toPbj(KeyUtils.A_COMPLEX_KEY);
    protected final Key adminContractKey =
            Key.newBuilder().contractID(asContract("0.0.10002")).build();
    protected final HederaKey adminHederaKey =
            Utils.asHederaKey(KeyUtils.A_COMPLEX_KEY).get();
    protected final HederaKey autoRenewHederaKey =
            Utils.asHederaKey(KeyUtils.A_COMPLEX_KEY).get();
    protected final Timestamp consensusTimestamp =
            Timestamp.newBuilder().seconds(1_234_567L).build();
    protected final ContractID targetContract =
            ContractID.newBuilder().contractNum(9_999L).build();

    @Mock
    protected MerkleAccount payerAccount;

    @Mock
    protected AccountAccess keyLookup;

    @BeforeEach
    void commonSetUp() {
        setUpPayer();
    }

    protected void basicMetaAssertions(
            final PreHandleContext context,
            final int nonPayerKeySize,
            final boolean failed,
            final ResponseCodeEnum failureStatus) {
        Assertions.assertThat(context.getRequiredNonPayerKeys()).hasSize(nonPayerKeySize);
        Assertions.assertThat(context.failed()).isEqualTo(failed);
        Assertions.assertThat(context.getStatus()).isEqualTo(failureStatus);
    }

    protected void setUpPayer() {
        lenient().when(keyLookup.getKey(payer)).thenReturn(KeyOrLookupFailureReason.withKey(payerKey));
        lenient().when(payerAccount.getAccountKey()).thenReturn((JKey) payerKey);
    }
}
