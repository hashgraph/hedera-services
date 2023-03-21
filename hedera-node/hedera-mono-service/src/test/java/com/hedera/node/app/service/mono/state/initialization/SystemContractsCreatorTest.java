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

package com.hedera.node.app.service.mono.state.initialization;

import static com.hedera.node.app.service.mono.state.initialization.SystemContractsCreator.SYSTEM_CONTRACT_BYTECODE;
import static com.hedera.node.app.service.mono.state.initialization.SystemContractsCreator.SYSTEM_CONTRACT_MEMO;
import static com.hedera.node.app.service.mono.txns.contract.ContractCreateTransitionLogic.STANDIN_CONTRACT_ID_KEY;
import static com.hedera.node.app.spi.config.PropertyNames.BOOTSTRAP_SYSTEM_ENTITY_EXPIRY;
import static com.hedera.node.app.spi.config.PropertyNames.CONTRACTS_SYSTEM_CONTRACTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.store.contracts.EntityAccess;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemContractsCreatorTest {

    private static final long EXPIRY = 100L;
    private static final long systemContractNum = 359L;
    private static final long systemContractNum2 = 360L;

    @Mock
    private BackingStore<AccountID, HederaAccount> accounts;

    @Mock
    private PropertySource properties;

    @Mock
    private EntityAccess entityAccess;

    SystemContractsCreator subject;

    @BeforeEach
    void setUp() {
        given(properties.getLongProperty(BOOTSTRAP_SYSTEM_ENTITY_EXPIRY)).willReturn(EXPIRY);

        subject = new SystemContractsCreator(MerkleAccount::new, accounts, properties, entityAccess);
    }

    @Test
    void doesNothingOnEmptySystemContracts() {
        // given
        given(properties.getAccountNums(CONTRACTS_SYSTEM_CONTRACTS)).willReturn(Collections.emptySet());

        // when
        subject.ensureSystemContractsExist();

        // then
        assertEquals(0, subject.getContractsCreated().size());
    }

    @Test
    void doesNothingOnAlreadyExistingSystemContracts() {
        // given
        given(properties.getAccountNums(CONTRACTS_SYSTEM_CONTRACTS))
                .willReturn(Set.of(systemContractNum, systemContractNum2));
        given(accounts.contains(AccountID.newBuilder().setAccountNum(359L).build()))
                .willReturn(true);
        given(accounts.contains(AccountID.newBuilder().setAccountNum(360L).build()))
                .willReturn(true);

        // when
        subject.ensureSystemContractsExist();

        // then
        assertEquals(0, subject.getContractsCreated().size());
    }

    @Test
    void createsNeededSystemContracts() {
        // given
        given(properties.getAccountNums(CONTRACTS_SYSTEM_CONTRACTS))
                .willReturn(Set.of(systemContractNum, systemContractNum2));

        // when
        subject.ensureSystemContractsExist();

        // then
        final var expectedContractAccount = new HederaAccountCustomizer()
                .isSmartContract(true)
                .isReceiverSigRequired(true)
                .isDeclinedReward(true)
                .isDeleted(false)
                .expiry(EXPIRY)
                .memo(SYSTEM_CONTRACT_MEMO)
                .key(STANDIN_CONTRACT_ID_KEY)
                .autoRenewPeriod(EXPIRY)
                .customizing(new MerkleAccount());
        assertEquals(2, subject.getContractsCreated().size());
        subject.getContractsCreated().forEach(a -> assertEquals(expectedContractAccount, a));
        // and:
        final var accountId =
                AccountID.newBuilder().setAccountNum(systemContractNum).build();
        final var accountId2 =
                AccountID.newBuilder().setAccountNum(systemContractNum2).build();
        verify(accounts).put(accountId, expectedContractAccount);
        verify(accounts).put(accountId2, expectedContractAccount);
        verify(entityAccess).storeCode(accountId, SYSTEM_CONTRACT_BYTECODE);
        verify(entityAccess).storeCode(accountId2, SYSTEM_CONTRACT_BYTECODE);
        // and:
        subject.forgetCreatedContracts();
        assertEquals(0, subject.getContractsCreated().size());
    }
}
