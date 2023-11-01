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

package com.hedera.node.app.service.token.impl.test;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractNonceInfo;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoHandlerTestBase;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableAccountStoreTest extends CryptoHandlerTestBase {

    @BeforeEach
    public void setUp() {
        super.setUp();
        resetStores();
    }

    @Test
    void returnsOnlyUpdatedContractNonces() {
        final var beforeNonce = 123L;
        final var afterNonce = 456L;
        final var unchangedOtherNonce = 789;
        final var brandNewNonce = 999;
        final var contractIdByNum = AccountID.newBuilder().accountNum(666).build();
        final var otherContractIdByNum = AccountID.newBuilder().accountNum(777).build();
        final var newContractIdByNum = AccountID.newBuilder().accountNum(888).build();
        writableAccounts.put(contractIdByNum, contractWith(contractIdByNum, beforeNonce));
        writableAccounts.put(otherContractIdByNum, contractWith(otherContractIdByNum, unchangedOtherNonce));
        writableAccounts.commit();

        final var newContract = requireNonNull(writableStore.getForModify(contractIdByNum))
                .copyBuilder()
                .ethereumNonce(afterNonce)
                .build();
        writableStore.put(newContract);
        writableStore.put(contractWith(newContractIdByNum, brandNewNonce));

        final var expectedUpdates = Set.of(
                new ContractNonceInfo(ContractID.newBuilder().contractNum(666).build(), afterNonce),
                new ContractNonceInfo(ContractID.newBuilder().contractNum(888).build(), brandNewNonce));
        final var actualUpdates = writableStore.summarizeContractChanges().updatedContractNonces();

        assertEquals(expectedUpdates, new HashSet<>(actualUpdates));
    }

    @Test
    void throwsIfNullValuesAsArgs() {
        assertThrows(NullPointerException.class, () -> new WritableAccountStore(null));
        assertThrows(NullPointerException.class, () -> writableStore.put(null));
        assertThrows(NullPointerException.class, () -> writableStore.put(null));
    }

    @Test
    void getReturnsImmutableAccount() {
        refreshStoresWithCurrentTokenInWritable();

        writableStore.put(account);

        final var readaccount = writableStore.get(id);

        assertThat(readaccount).isNotNull();
        assertThat(account).isEqualTo(readaccount);
    }

    @Test
    void getForModifyReturnsImmutableAccount() {
        refreshStoresWithCurrentTokenInWritable();

        writableStore.put(account);

        final var readaccount = writableStore.getForModify(id);

        assertThat(readaccount).isNotNull();
        assertThat(account).isEqualTo(readaccount);
    }

    @Test
    void canRemoveAlias() {
        writableStore.putAlias(alias.aliasOrThrow(), id);
        assertEquals(1, writableStore.sizeOfAliasesState());
        writableStore.removeAlias(alias.aliasOrThrow());
        assertEquals(0, writableStore.sizeOfAliasesState());
    }

    @Test
    void getForModifyLooksForAlias() {
        assertEquals(0, writableStore.sizeOfAliasesState());

        writableStore.put(account);
        writableStore.putAlias(alias.alias(), id);

        final var readaccount = writableStore.getForModify(alias);

        assertThat(readaccount).isNotNull();
        assertThat(account).isEqualTo(readaccount);
        assertEquals(1, writableStore.sizeOfAliasesState());
        assertEquals(Set.of(edKeyAlias), writableStore.modifiedAliasesInState());
    }

    @Test
    void getForModifyReturnEmptyIfAliasNotPresent() {
        writableStore.put(account);

        final var readaccount = writableStore.getForModify(alias);

        assertThat(readaccount).isNull();
        assertThat(writableStore.sizeOfAliasesState()).isZero();
    }

    @Test
    void putsAccountChangesToStateInModifications() {
        assertThat(writableStore.get(id)).isNull();

        // put, keeps the account in the modifications
        writableStore.put(account);

        assertTrue(writableAccounts.contains(id));
        final var writtenaccount = writableAccounts.get(id);
        assertThat(account).isEqualTo(writtenaccount);
    }

    @Test
    void getsSizeOfState() {
        assertThat(writableStore.sizeOfAliasesState()).isZero();
        assertThat(writableStore.modifiedAccountsInState()).isEqualTo(Collections.EMPTY_SET);

        writableStore.put(account);
        assertThat(writableStore.sizeOfAccountState()).isEqualTo(1);
        assertThat(writableStore.modifiedAccountsInState())
                .isEqualTo(Set.of(AccountID.newBuilder().accountNum(3).build()));
    }

    private Account contractWith(final AccountID id, final long nonce) {
        return Account.newBuilder()
                .accountId(id)
                .smartContract(true)
                .ethereumNonce(nonce)
                .build();
    }
}
