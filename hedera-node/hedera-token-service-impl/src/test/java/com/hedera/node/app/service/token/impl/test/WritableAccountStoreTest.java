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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.impl.WritableAccountStore;import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.test.handlers.CryptoHandlerTestBase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class WritableAccountStoreTest extends CryptoHandlerTestBase {
    private Account account;

    @Test
    void throwsIfNullValuesAsArgs() {
        assertThrows(NullPointerException.class, () -> new WritableAccountStore(null));
        assertThrows(NullPointerException.class, () -> writableStore.put(null));
        assertThrows(NullPointerException.class, () -> writableStore.put(null));
    }

    @Test
    void constructorCreatesTokenState() {
        final var store = new WritableTokenStore(writableStates);
        assertNotNull(store);
    }

    @Test
    void getReturnsImmutableToken() {
        account = createAccount();
        writableStore.put(token);

        final var maybeReadToken = writableStore.get(id.accountNum()).longValue());

        assertTrue(maybeReadToken.isPresent());
        final var readToken = maybeReadToken.get();
        assertEquals(token, readToken);
    }

    @Test
    void getForModifyReturnsImmutableToken() {
        token = createToken();

        writableStore.put(token);

        final var maybeReadToken = writableStore.getForModify(tokenEntityNum.longValue());

        assertTrue(maybeReadToken.isPresent());
        final var readToken = maybeReadToken.get();
        assertEquals(token, readToken);
    }

    @Test
    void putsTokenChangesToStateInModifications() {
        token = createToken();
        assertFalse(writableTokenState.contains(tokenEntityNum));

        // put, keeps the token in the modifications
        writableStore.put(token);

        assertTrue(writableTokenState.contains(tokenEntityNum));
        final var writtenToken = writableTokenState.get(tokenEntityNum);
        assertEquals(token, writtenToken);
    }

    @Test
    void commitsTokenChangesToState() {
        token = createToken();
        assertFalse(writableTokenState.contains(tokenEntityNum));
        // put, keeps the token in the modifications.
        // Size of state includes modifications and size of backing state.
        writableStore.put(token);

        assertTrue(writableTokenState.contains(tokenEntityNum));
        final var writtenToken = writableTokenState.get(tokenEntityNum);
        assertEquals(1, writableStore.sizeOfState());
        assertTrue(writableStore.modifiedTokens().contains(tokenEntityNum));
        assertEquals(token, writtenToken);

        // commit, pushes modifications to backing store. But the size of state is still 1
        writableStore.commit();
        assertEquals(1, writableStore.sizeOfState());
        assertTrue(writableStore.modifiedTokens().contains(tokenEntityNum));
    }

    @Test
    void getsSizeOfState() {
        token = createToken();
        assertEquals(0, writableStore.sizeOfState());
        assertEquals(Collections.EMPTY_SET, writableStore.modifiedTokens());
        writableStore.put(token);

        assertEquals(1, writableStore.sizeOfState());
        assertEquals(Set.of(tokenEntityNum), writableStore.modifiedTokens());
    }
}
