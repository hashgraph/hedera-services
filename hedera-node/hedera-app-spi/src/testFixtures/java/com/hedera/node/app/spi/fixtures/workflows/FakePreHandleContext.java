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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Key.KeyOneOfType;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake implementation of {@link PreHandleContext} to simplify moving forward without breaking all kinds of tests
 * in services.
 *
 * @deprecated for removal in favor of a mock of {@link PreHandleContext}
 */
@Deprecated(forRemoval = true)
public class FakePreHandleContext implements PreHandleContext {

    /** Used to get keys for accounts and contracts. */
    private final ReadableAccountStore accountStore;
    /** The transaction body. */
    private final TransactionBody txn;
    /** The payer account ID. Specified in the transaction body, extracted and stored separately for convenience. */
    private final AccountID payer;
    /** The payer's key, as found in state */
    private final Key payerKey;
    /**
     * The set of all required non-payer keys. A {@link LinkedHashSet} is used to maintain a consistent ordering.
     * While not strictly necessary, it is useful at the moment to ensure tests are deterministic. The tests should
     * be updated to compare set contents rather than ordering.
     */
    private final Set<Key> requiredNonPayerKeys = new LinkedHashSet<>();
    /** Scheduled transactions have a secondary "inner context". Seems not quite right. */
    private PreHandleContext innerContext;

    private final Map<Class<?>, Object> stores = new ConcurrentHashMap<>();

    /**
     * Create a new PreHandleContext instance. The payer and key will be extracted from the transaction body.
     *
     * @param accountStore used to get keys for accounts and contracts
     * @param txn the transaction body
     * @throws PreCheckException if the payer account ID is invalid or the key is null
     */
    public FakePreHandleContext(@NonNull final ReadableAccountStore accountStore, @NonNull final TransactionBody txn)
            throws PreCheckException {
        this(
                accountStore,
                txn,
                txn.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT),
                INVALID_PAYER_ACCOUNT_ID);
    }

    /** Create a new instance */
    private FakePreHandleContext(
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final TransactionBody txn,
            @NonNull final AccountID payer,
            @NonNull final ResponseCodeEnum responseCode)
            throws PreCheckException {
        this.accountStore = requireNonNull(accountStore, "The supplied argument 'accountStore' cannot be null!");
        this.txn = requireNonNull(txn, "The supplied argument 'txn' cannot be null!");
        this.payer = requireNonNull(payer, "The supplied argument 'payer' cannot be null!");

        stores.put(ReadableAccountStore.class, accountStore);

        // Find the account, which must exist or throw a PreCheckException with the given response code.
        final var account = accountStore.getAccountById(payer);
        mustExist(account, responseCode);
        // NOTE: While it is true that the key can be null on some special accounts like
        // account 800, those accounts cannot be the payer.
        this.payerKey = account.key();
        mustExist(this.payerKey, responseCode);
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

    @Override
    @NonNull
    public TransactionBody body() {
        return txn;
    }

    @Override
    @NonNull
    public AccountID payer() {
        return payer;
    }

    @NonNull
    @Override
    public Set<Key> requiredNonPayerKeys() {
        return Collections.unmodifiableSet(requiredNonPayerKeys);
    }

    @Override
    @Nullable
    public Key payerKey() {
        return payerKey;
    }

    @Override
    @NonNull
    public PreHandleContext requireKey(@NonNull final Key key) {
        if (!key.equals(payerKey) && isValid(key)) {
            requiredNonPayerKeys.add(key);
        }
        return this;
    }

    @Override
    @NonNull
    public PreHandleContext requireKeyOrThrow(@Nullable final Key key, @NonNull final ResponseCodeEnum responseCode)
            throws PreCheckException {
        requireNonNull(responseCode);
        if (!isValid(key)) {
            throw new PreCheckException(responseCode);
        }
        return requireKey(key);
    }

    @Override
    @NonNull
    public PreHandleContext requireKeyOrThrow(
            @Nullable final AccountID accountID, @NonNull final ResponseCodeEnum responseCode)
            throws PreCheckException {
        requireNonNull(responseCode);

        if (accountID == null) {
            throw new PreCheckException(responseCode);
        }

        final var account = accountStore.getAccountById(accountID);
        if (account == null) {
            throw new PreCheckException(responseCode);
        }

        final var key = account.key();
        if (!isValid(key)) { // Or if it is a Contract Key? Or if it is an empty key?
            // Or a KeyList with no
            // keys? Or KeyList with Contract keys only?
            throw new PreCheckException(responseCode);
        }

        return requireKey(key);
    }

    @Override
    @NonNull
    public PreHandleContext requireKeyOrThrow(
            @Nullable final ContractID accountID, @NonNull final ResponseCodeEnum responseCode)
            throws PreCheckException {
        requireNonNull(responseCode);
        if (accountID == null) {
            throw new PreCheckException(responseCode);
        }

        final var account = accountStore.getContractById(accountID);
        if (account == null) {
            throw new PreCheckException(responseCode);
        }

        final var key = account.key();
        if (!isValid(key)) { // Or if it is a Contract Key? Or if it is an empty key?
            // Or a KeyList with no
            // keys? Or KeyList with Contract keys only?
            throw new PreCheckException(responseCode);
        }

        return requireKey(key);
    }

    @Override
    @NonNull
    public PreHandleContext requireKeyIfReceiverSigRequired(
            @Nullable final AccountID accountID, @NonNull final ResponseCodeEnum responseCode)
            throws PreCheckException {
        requireNonNull(responseCode);
        // If no accountID is specified, then there is no key to require.
        if (accountID == null || accountID.equals(AccountID.DEFAULT)) {
            return this;
        }

        // If an accountID is specified, then the account MUST exist
        final var account = accountStore.getAccountById(accountID);
        if (account == null) {
            throw new PreCheckException(responseCode);
        }

        // If the account exists but does not require a signature, then there is no key to require.
        if (!account.receiverSigRequired()) {
            return this;
        }

        // We will require the key. If the key isn't present, then we will throw the given response code.
        final var key = account.key();
        if (key == null
                || key.key().kind() == KeyOneOfType.UNSET) { // Or if it is a Contract Key? Or if it is an empty key?
            // Or a KeyList with no
            // keys? Or KeyList with Contract keys only?
            throw new PreCheckException(responseCode);
        }

        return requireKey(key);
    }

    @Override
    @NonNull
    public PreHandleContext requireKeyIfReceiverSigRequired(
            @Nullable final ContractID contractID, @NonNull final ResponseCodeEnum responseCode)
            throws PreCheckException {
        requireNonNull(responseCode);
        // If no accountID is specified, then there is no key to require.
        if (contractID == null) {
            return this;
        }

        // If an accountID is specified, then the account MUST exist
        final var account = accountStore.getContractById(contractID);
        if (account == null) {
            throw new PreCheckException(responseCode);
        }

        // If the account exists but does not require a signature, then there is no key to require.
        if (!account.receiverSigRequired()) {
            return this;
        }

        // We will require the key. If the key isn't present, then we will throw the given response code.
        final var key = account.key();
        if (!isValid(key)) { // Or if it is a Contract Key? Or if it is an empty key?
            // Or a KeyList with no
            // keys? Or KeyList with Contract keys only?
            throw new PreCheckException(responseCode);
        }

        return requireKey(key);
    }

    @Override
    @NonNull
    public PreHandleContext createNestedContext(
            @NonNull final TransactionBody nestedTxn,
            @NonNull final AccountID payerForNested,
            @NonNull final ResponseCodeEnum responseCode)
            throws PreCheckException {
        this.innerContext = new FakePreHandleContext(accountStore, nestedTxn, payerForNested, responseCode);
        return this.innerContext;
    }

    @Override
    @Nullable
    public PreHandleContext innerContext() {
        return innerContext;
    }

    @Override
    public String toString() {
        return "FakePreHandleContext{" + "accountStore="
                + accountStore + ", txn="
                + txn + ", payer="
                + payer + ", payerKey="
                + payerKey + ", requiredNonPayerKeys="
                + requiredNonPayerKeys + ", innerContext="
                + innerContext + ", stores="
                + stores + '}';
    }
}
