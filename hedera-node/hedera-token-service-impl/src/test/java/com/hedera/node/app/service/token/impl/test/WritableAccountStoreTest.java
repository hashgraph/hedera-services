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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.test.handlers.CryptoHandlerTestBase;
import java.util.Collections;
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
    void throwsIfNullValuesAsArgs() {
        assertThrows(NullPointerException.class, () -> new WritableAccountStore(null));
        assertThrows(NullPointerException.class, () -> writableStore.put(null));
        assertThrows(NullPointerException.class, () -> writableStore.put(null));
    }

    @Test
    void getReturnsImmutableAccount() {
        refreshStoresWithCurrentTokenInWritable();

        writableStore.put(account);

        final var maybeReadAccount = writableStore.get(id);

        assertTrue(maybeReadAccount.isPresent());
        final var readaccount = maybeReadAccount.get();
        assertEquals(account, readaccount);
    }

    @Test
    void getForModifyReturnsImmutableAccount() {
        refreshStoresWithCurrentTokenInWritable();

        writableStore.put(account);

        final var maybeReadAccount = writableStore.getForModify(id);

        assertTrue(maybeReadAccount.isPresent());
        final var readaccount = maybeReadAccount.get();
        assertEquals(account, readaccount);
    }

    @Test
    void getForModifyLooksForAlias() {
        assertEquals(0, writableStore.sizeOfAliasesState());

        writableStore.put(account);
        writableStore.putAlias(alias.alias().asUtf8String(), accountNum);

        final var maybeReadAccount = writableStore.getForModify(alias);

        assertTrue(maybeReadAccount.isPresent());
        final var readaccount = maybeReadAccount.get();
        assertEquals(account, readaccount);
        assertEquals(1, writableStore.sizeOfAliasesState());
        assertEquals(Set.of(alias.alias().asUtf8String()), writableStore.modifiedAliasesInState());
    }

    @Test
    void getForModifyReturnEmptyIfAliasNotPresent() {
        writableStore.put(account);

        final var maybeReadAccount = writableStore.getForModify(alias);

        assertFalse(maybeReadAccount.isPresent());
        assertEquals(0, writableStore.sizeOfAliasesState());
    }

    @Test
    void putsAccountChangesToStateInModifications() {
        assertFalse(writableStore.get(id).isPresent());

        // put, keeps the account in the modifications
        writableStore.put(account);

        assertTrue(writableAccounts.contains(accountEntityNumVirtualKey));
        final var writtenaccount = writableAccounts.get(accountEntityNumVirtualKey);
        assertEquals(account, writtenaccount);
    }

    @Test
    void commitsAccountChangesToState() {
        assertFalse(writableAccounts.contains(accountEntityNumVirtualKey));
        // put, keeps the account in the modifications.
        // Size of state includes modifications and size of backing state.
        writableStore.put(account);

        assertTrue(writableAccounts.contains(accountEntityNumVirtualKey));
        final var writtenaccount = writableAccounts.get(accountEntityNumVirtualKey);
        assertEquals(1, writableStore.sizeOfAccountState());
        assertTrue(writableStore.modifiedAccountsInState().contains(accountEntityNumVirtualKey));
        assertEquals(account, writtenaccount);

        // commit, pushes modifications to backing store. But the size of state is still 1
        writableStore.commit();
        assertEquals(1, writableStore.sizeOfAccountState());
        assertTrue(writableStore.modifiedAccountsInState().contains(accountEntityNumVirtualKey));
    }

    @Test
    void getsSizeOfState() {
        assertEquals(0, writableStore.sizeOfAccountState());
        assertEquals(Collections.EMPTY_SET, writableStore.modifiedAccountsInState());
        writableStore.put(account);

        assertEquals(1, writableStore.sizeOfAccountState());
        assertEquals(Set.of(EntityNumVirtualKey.fromLong(3)), writableStore.modifiedAccountsInState());
    }
}
