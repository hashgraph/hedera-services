/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.fixtures.workflows;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake implementation of {@link PreHandleContext} to simplify moving forward without breaking all kinds of tests
 * in services. This class should be replaced with a mock of {@link PreHandleContext}.
 */
public class FakePreHandleContext extends PreHandleContext {

    private final Map<Class<?>, Object> stores = new ConcurrentHashMap<>();

    /**
     * Create a new PreHandleContext instance. The payer and key will be extracted from the transaction body.
     *
     * @param accountAccess used to get keys for accounts and contracts
     * @param txn the transaction body
     * @throws PreCheckException if the payer account ID is invalid or the key is null
     */
    public FakePreHandleContext(@NonNull final AccountAccess accountAccess, @NonNull final TransactionBody txn)
            throws PreCheckException {
        super(accountAccess, txn);
        stores.put(AccountAccess.class, accountAccess);
        stores.put(ReadableAccountStore.class, accountAccess);
    }

    @Override
    @NonNull
    public <C> C createStore(@NonNull final Class<C> storeInterface) {
        requireNonNull(storeInterface, "The supplied argument 'storeInterface' cannot be null.");
        final var store = stores.get(storeInterface);
        if (store != null) {
            return storeInterface.cast(store);
        }
        throw new IllegalArgumentException("No store for " + storeInterface);
    }

    public <T> void registerStore(@NonNull final Class<T> storeInterface, @NonNull final T store) {
        requireNonNull(storeInterface, "The supplied argument 'storeInterface' cannot be null.");
        requireNonNull(store, "The supplied argument 'store' cannot be null.");
        stores.put(storeInterface, store);
    }
}
