/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.accounts;

import static com.hedera.services.ledger.accounts.HederaAccountCustomizer.STAKED_ACCOUNT_ID_CASE;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.DECLINE_REWARD;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_ID;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SynthCreationCustomizerTest {
    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;

    private SynthCreationCustomizer subject;

    @BeforeEach
    void setUp() {
        subject = new SynthCreationCustomizer(accountsLedger);
    }

    @Test
    void customizesAsExpected() {
        given(accountsLedger.get(callerId, KEY)).willReturn(cryptoAdminKey);
        given(accountsLedger.get(callerId, MEMO)).willReturn(memo);
        given(accountsLedger.get(callerId, EXPIRY)).willReturn(expiry);
        given(accountsLedger.get(callerId, AUTO_RENEW_PERIOD)).willReturn(autoRenewPeriod);
        given(accountsLedger.get(callerId, AUTO_RENEW_ACCOUNT_ID)).willReturn(autoRenewAccount);
        given(accountsLedger.get(callerId, MAX_AUTOMATIC_ASSOCIATIONS))
                .willReturn(maxAutoAssociations);
        given(accountsLedger.get(callerId, STAKED_ID)).willReturn(stakedId);
        given(accountsLedger.get(callerId, DECLINE_REWARD)).willReturn(declineReward);

        final var origCreation =
                TransactionBody.newBuilder()
                        .setContractCreateInstance(
                                ContractCreateTransactionBody.getDefaultInstance())
                        .build();
        final var customCreation = subject.customize(origCreation, callerId);

        final var customOp = customCreation.getContractCreateInstance();
        assertEquals(grpcCryptoAdminKey, customOp.getAdminKey());
        assertEquals(memo, customOp.getMemo());
        assertEquals(autoRenewPeriod, customOp.getAutoRenewPeriod().getSeconds());
        assertEquals(autoRenewAccount.toGrpcAccountId(), customOp.getAutoRenewAccountId());
        assertEquals(asAccount("0.0." + stakedId), customOp.getStakedAccountId());
        assertEquals(STAKED_ACCOUNT_ID_CASE, customOp.getStakedIdCase().name());
        assertEquals(0, customOp.getStakedNodeId());
        assertEquals(declineReward, customOp.getDeclineReward());
    }

    private static final JKey cryptoAdminKey =
            new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
    private static final Key grpcCryptoAdminKey = MiscUtils.asKeyUnchecked(cryptoAdminKey);
    private static final AccountID callerId = asAccount("0.0.666");
    private static final long expiry = 1_234_567L;
    private static final long autoRenewPeriod = 7776000L;
    private static final String memo = "the grey rock";
    private static final long stakedId = 3L;
    private static final EntityId autoRenewAccount = new EntityId(0, 0, 4);
    private static final int maxAutoAssociations = 25;
    private static final boolean declineReward = true;
}
