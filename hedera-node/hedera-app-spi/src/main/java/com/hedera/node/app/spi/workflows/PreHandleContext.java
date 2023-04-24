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

package com.hedera.node.app.spi.workflows;

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
import com.hedera.node.app.spi.accounts.AccountAccess;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents the context of a single {@code preHandle()}-call.
 *
 * <p>During pre-handle, each transaction handler needs access to the transaction body data (i.e. the "operation"
 * being performed, colloquially also called the "transaction" and "transaction body" although both are more
 * or less technically incorrect). The actual {@link TransactionBody} can be accessed from this context. The body
 * contains the operation, the transaction ID, the originating node, and other information.
 *
 * <p>The main responsibility for a transaction handler during pre-handle is to semantically validate the operation
 * and to gather all required keys. The handler, when created, is preloaded with the correct payer key (which is
 * almost always the same as the transaction body's {@link TransactionID}, except in the case of a scheduled
 * transaction). {@link TransactionHandler}s must add any additional required signing keys. Several convenience
 * methods have been created for this purpose.
 *
 * <p>{@link #requireKey(Key)} is used to add a required non-payer signing key (remember, the payer signing
 * key was added when the context was created). Some basic validation is performed (the key cannot be null or empty).
 */
public final class PreHandleContext {
    private final ReadableStoreFactory storeFactory;
    /** Used to get keys for accounts and contracts. */
    private final AccountAccess accountAccess;
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

    /**
     * Create a new PreHandleContext instance. The payer and key will be extracted from the transaction body.
     *
     * @param accountAccess used to get keys for accounts and contracts
     * @param txn the transaction body
     * @throws PreCheckException if the payer account ID is invalid or the key is null
     * @deprecated Use {@link #PreHandleContext(ReadableStoreFactory, TransactionBody)} instead.
     */
    @Deprecated(forRemoval = true)
    public PreHandleContext(@NonNull final AccountAccess accountAccess, @NonNull final TransactionBody txn)
            throws PreCheckException {
        this(
                new ReadableStoreFactoryHack(accountAccess),
                txn,
                txn.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT),
                INVALID_PAYER_ACCOUNT_ID);
    }

    public PreHandleContext(@NonNull final ReadableStoreFactory storeFactory, @NonNull final TransactionBody txn)
            throws PreCheckException {
        this(
                storeFactory,
                txn,
                txn.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT),
                INVALID_PAYER_ACCOUNT_ID);
    }

    /** Create a new instance */
    private PreHandleContext(
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final TransactionBody txn,
            @NonNull final AccountID payer,
            @NonNull final ResponseCodeEnum responseCode)
            throws PreCheckException {
        this.storeFactory = requireNonNull(storeFactory, "The supplied argument 'storeFactory' cannot be null!");
        this.txn = requireNonNull(txn, "The supplied argument 'txn' cannot be null!");
        this.payer = requireNonNull(payer, "The supplied argument 'payer' cannot be null!");

        this.accountAccess = storeFactory.createStore(AccountAccess.class);

        // Find the account, which must exist or throw a PreCheckException with the given response code.
        final var account = accountAccess.getAccountById(payer);
        mustExist(account, responseCode);
        // NOTE: While it is true that the key can be null on some special accounts like
        // account 800, those accounts cannot be the payer.
        this.payerKey = account.key();
        mustExist(this.payerKey, responseCode);
    }

    /**
     * Create a new store given the store's interface. This gives read-only access to the store.
     *
     * @param storeInterface The store interface to find and create a store for
     * @return An implementation of store interface provided, or null if the store
     * @param <C> Interface class for a Store
     * @throws IllegalArgumentException if the storeInterface class provided is unknown to the app
     * @throws NullPointerException if {@code storeInterface} is {@code null}
     */
    @NonNull
    public <C> C createStore(@NonNull final Class<C> storeInterface) {
        return storeFactory.createStore(storeInterface);
    }

    /**
     * Gets the {@link AccountAccess}.
     *
     * @return the {@link AccountAccess}
     * @deprecated Use {@link #createStore(Class)} instead.
     */
    @Deprecated(forRemoval = true)
    @NonNull
    public AccountAccess accountAccess() {
        return accountAccess;
    }

    /**
     * Gets the {@link TransactionBody}
     *
     * @return the {@link TransactionBody} in this context
     */
    @NonNull
    public TransactionBody body() {
        return txn;
    }

    /**
     * Gets the payer {@link AccountID}.
     *
     * @return the {@link AccountID} of the payer in this context
     */
    @NonNull
    public AccountID payer() {
        return payer;
    }

    /**
     * Returns an immutable copy of the list of required non-payer keys.
     *
     * @return the {@link Set} with the required non-payer keys
     */
    public Set<Key> requiredNonPayerKeys() {
        return Collections.unmodifiableSet(requiredNonPayerKeys);
    }

    /**
     * Getter for the payer key
     *
     * @return the payer key
     */
    @Nullable
    public Key payerKey() {
        return payerKey;
    }

    /**
     * Adds the given key to required non-payer keys. If the key is the same as the payer key, or if the key has
     * already been added, then the call is a no-op. The key must not be null.
     *
     * @param key key to be added
     * @return {@code this} object
     * @throws NullPointerException if the key is null
     */
    @NonNull
    public PreHandleContext requireKey(@NonNull final Key key) {
        if (!key.equals(payerKey) && isValid(key)) {
            requiredNonPayerKeys.add(key);
        }
        return this;
    }

    /**
     * Adds the given key to required non-payer keys. If the key is the same as the payer key, or if the key has
     * already been added, then the call is a no-op. The key must not be null and not empty, otherwise a
     * PreCheckException is thrown with the given {@code responseCode}.
     *
     * @param key key to be added
     * @param responseCode the response code to be used in case the key is null or empty
     * @return {@code this} object
     * @throws PreCheckException if the key is null or empty
     */
    @NonNull
    public PreHandleContext requireKeyOrThrow(@Nullable final Key key, @NonNull final ResponseCodeEnum responseCode)
            throws PreCheckException {
        requireNonNull(responseCode);
        if (!isValid(key)) {
            throw new PreCheckException(responseCode);
        }
        return requireKey(key);
    }

    /**
     * Adds the admin key of the account addressed by the given {@code accountID} to the required non-payer keys. If
     * the key is the same as the payer key, or if the key has already been added, then the call is a no-op. The
     * {@link AccountID} must not be null, and must refer to an actual account. The admin key on that account must not
     * be null or empty. If any of these conditions are not met, a PreCheckException is thrown with the given
     * {@code responseCode}.
     *
     * @param accountID The ID of the account whose key is to be added
     * @param responseCode the response code to be used in case the key is null or empty
     * @return {@code this} object
     * @throws PreCheckException if the key is null or empty or the account is null or the
     * account does not exist.
     */
    @NonNull
    public PreHandleContext requireKeyOrThrow(
            @Nullable final AccountID accountID, @NonNull final ResponseCodeEnum responseCode)
            throws PreCheckException {
        requireNonNull(responseCode);

        if (accountID == null) {
            throw new PreCheckException(responseCode);
        }

        final var account = accountAccess.getAccountById(accountID);
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

    /**
     * The same as {@link #requireKeyOrThrow(AccountID, ResponseCodeEnum)} but for a {@link ContractID}.
     *
     * @param accountID The ID of the contract account whose key is to be added
     * @param responseCode the response code to be used in case the key is null or empty
     * @return {@code this} object
     * @throws PreCheckException if the key is null or empty or the account is null or the
     * contract account does not exist or the account is not a contract account.
     */
    @NonNull
    public PreHandleContext requireKeyOrThrow(
            @Nullable final ContractID accountID, @NonNull final ResponseCodeEnum responseCode)
            throws PreCheckException {
        requireNonNull(responseCode);
        if (accountID == null) {
            throw new PreCheckException(responseCode);
        }

        final var account = accountAccess.getContractById(accountID);
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

    /**
     * Adds the admin key of the account addressed by the given {@code accountID} to the required non-payer keys if
     * the {@link AccountID} is not null and if the account has `receiverSigRequired` set to true. If the account
     * does not exist, or `receiverSigRequired` is true but the key is null or empty, then a
     * {@link PreCheckException} will be thrown with the supplied {@code responseCode}.
     *
     * @param accountID The ID of the account whose key is to be added
     * @param responseCode the response code to be used if a {@link PreCheckException} is thrown
     * @throws PreCheckException if the account does not exist or the account has `receiverSigRequired` but a null or
     * empty key.
     */
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
        final var account = accountAccess.getAccountById(accountID);
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

    /**
     * The same as {@link #requireKeyIfReceiverSigRequired(AccountID, ResponseCodeEnum)} but for a {@link ContractID}.
     *
     * @param contractID The ID of the contract account whose key is to be added
     * @param responseCode the response code to be used if a {@link PreCheckException} is thrown
     * @throws PreCheckException if the account does not exist or the account has `receiverSigRequired` but a null or
     * empty key, or the account exists but is not a contract account.
     */
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
        final var account = accountAccess.getContractById(contractID);
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

    /**
     * Creates a new {@link PreHandleContext} for a nested transaction. The nested transaction will be set on
     * this context as the "inner context". There can only be one such at a time. The inner context is returned
     * for convenience.
     *
     * @param nestedTxn the nested transaction
     * @param payerForNested the payer for the nested transaction
     * @param responseCode the response code to be used if a {@link PreCheckException} is thrown
     * @return the inner context
     * @throws PreCheckException If the payer is not valid
     */
    @NonNull
    public PreHandleContext createNestedContext(
            @NonNull final TransactionBody nestedTxn,
            @NonNull final AccountID payerForNested,
            @NonNull final ResponseCodeEnum responseCode)
            throws PreCheckException {
        this.innerContext = new PreHandleContext(storeFactory, nestedTxn, payerForNested, responseCode);
        return this.innerContext;
    }

    /**
     * Gets the inner context, if any.
     *
     * @return The inner context.
     */
    public PreHandleContext innerContext() {
        return innerContext;
    }

    @Override
    public String toString() {
        return "PreHandleContext{" + "accountAccess="
                + accountAccess + ", txn="
                + txn + ", payer="
                + payer + ", requiredNonPayerKeys="
                + requiredNonPayerKeys + ", status="
                + payerKey + ", innerContext="
                + innerContext + '}';
    }

    /**
     * A factory for creating readable stores.
     *
     * <p>This interface is only a temporary solution. In the future, we probably want to turn {@code PreHandleContext}
     * into an interface and have the implementation in hedera-app. Unfortunately this cannot easily be done,
     * because many unit-tests in services rely on implementation details of {@code PreHandleContext}.
     */
    public interface ReadableStoreFactory {

        /**
         * Create a new store given the store's interface. This gives read-only access to the store.
         *
         * @param storeInterface The store interface to find and create a store for
         * @param <C> Interface class for a Store
         * @return An implementation of the provided store interface
         * @throws IllegalArgumentException if the storeInterface class provided is unknown to the app
         * @throws NullPointerException if {@code storeInterface} is {@code null}
         */
        @NonNull
        <C> C createStore(@NonNull Class<C> storeInterface) throws IllegalArgumentException;
    }

    @Deprecated(forRemoval = true)
    private static class ReadableStoreFactoryHack implements ReadableStoreFactory {
        private final AccountAccess accountAccess;

        private ReadableStoreFactoryHack(@NonNull final AccountAccess accountAccess) {
            this.accountAccess = requireNonNull(accountAccess, "The supplied argument 'accountAccess' cannot be null!");
        }

        @Override
        @NonNull
        public <C> C createStore(@NonNull final Class<C> storeInterface) {
            requireNonNull(storeInterface, "The supplied argument 'storeInterface' cannot be null!");
            if (storeInterface == AccountAccess.class) {
                return (C) accountAccess;
            }
            throw new UnsupportedOperationException("Not implemented yet");
        }
    }
}
