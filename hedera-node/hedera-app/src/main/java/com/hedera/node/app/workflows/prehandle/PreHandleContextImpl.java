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

package com.hedera.node.app.workflows.prehandle;

import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.verifyIsNotImmutableAccount;
import static com.hedera.node.app.spi.HapiUtils.EMPTY_KEY_LIST;
import static com.hedera.node.app.spi.HapiUtils.isHollow;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Key.KeyOneOfType;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionKeys;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Implementation of {@link PreHandleContext}.
 */
public class PreHandleContextImpl implements PreHandleContext {

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
    /** The set of all hollow accounts that need to be validated. */
    private final Set<Account> requiredHollowAccounts = new LinkedHashSet<>();
    /**
     * The set of all optional non-payer keys. A {@link LinkedHashSet} is used to maintain a consistent ordering.
     * While not strictly necessary, it is useful at the moment to ensure tests are deterministic. The tests should
     * be updated to compare set contents rather than ordering.
     */
    private final Set<Key> optionalNonPayerKeys = new LinkedHashSet<>();
    /** The set of all hollow accounts that <strong>might</strong> need to be validated, but also might not. */
    private final Set<Account> optionalHollowAccounts = new LinkedHashSet<>();
    /** Scheduled transactions have a secondary "inner context". Seems not quite right. */
    private PreHandleContext innerContext;

    private final ReadableStoreFactory storeFactory;

    /** Configuration to be used during pre-handle */
    private final Configuration configuration;

    private final TransactionDispatcher dispatcher;

    public PreHandleContextImpl(
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final TransactionBody txn,
            @NonNull final Configuration configuration,
            @NonNull final TransactionDispatcher dispatcher)
            throws PreCheckException {
        this(
                storeFactory,
                txn,
                txn.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT),
                configuration,
                dispatcher);
    }

    /** Create a new instance */
    public PreHandleContextImpl(
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final TransactionBody txn,
            @NonNull final AccountID payer,
            @NonNull final Configuration configuration,
            @NonNull final TransactionDispatcher dispatcher)
            throws PreCheckException {
        this.storeFactory = requireNonNull(storeFactory, "storeFactory must not be null.");
        this.txn = requireNonNull(txn, "txn must not be null!");
        this.payer = requireNonNull(payer, "payer msut not be null!");
        this.configuration = requireNonNull(configuration, "configuration must not be null!");
        this.dispatcher = requireNonNull(dispatcher, "dispatcher must not be null!");

        this.accountStore = storeFactory.getStore(ReadableAccountStore.class);

        // Find the account, which must exist or throw a PreCheckException with the given response code.
        final var account = accountStore.getAccountById(payer);
        mustExist(account, ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID);
        // NOTE: While it is true that the key can be null on some special accounts like
        // account 800, those accounts cannot be the payer.
        payerKey = account.key();
        mustExist(payerKey, ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID);
    }

    @Override
    @NonNull
    public <C> C createStore(@NonNull Class<C> storeInterface) {
        return storeFactory.getStore(storeInterface);
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

    @Override
    @NonNull
    public Configuration configuration() {
        return configuration;
    }

    @NonNull
    @Override
    public Set<Key> requiredNonPayerKeys() {
        return Collections.unmodifiableSet(requiredNonPayerKeys);
    }

    @NonNull
    @Override
    public Set<Key> optionalNonPayerKeys() {
        return Collections.unmodifiableSet(optionalNonPayerKeys);
    }

    @NonNull
    @Override
    public PreHandleContext optionalKey(@NonNull final Key key) throws PreCheckException {
        // Verify this key isn't for an immutable account
        verifyIsNotImmutableAccount(key, ResponseCodeEnum.INVALID_ACCOUNT_ID);

        if (!key.equals(payerKey) && isValid(key)) {
            optionalNonPayerKeys.add(key);
        }
        return this;
    }

    @NonNull
    @Override
    public PreHandleContext optionalKeys(@NonNull final Set<Key> keys) throws PreCheckException {
        for (final Key nextKey : keys) {
            optionalKey(nextKey);
        }
        return this;
    }

    @NonNull
    @Override
    public Set<Account> optionalHollowAccounts() {
        return Collections.unmodifiableSet(optionalHollowAccounts);
    }

    @NonNull
    @Override
    public PreHandleContext optionalSignatureForHollowAccount(@NonNull final Account hollowAccount) {
        requireNonNull(hollowAccount);
        final AccountID id = hollowAccount.accountId();
        if (!isHollow(hollowAccount)) {
            throw new IllegalArgumentException("Account %d.%d.%d is not a hollow account"
                    .formatted(id.shardNum(), id.realmNum(), id.accountNum()));
        }
        optionalHollowAccounts.add(hollowAccount);
        return this;
    }

    @Override
    @NonNull
    public Set<Account> requiredHollowAccounts() {
        return Collections.unmodifiableSet(requiredHollowAccounts);
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
    @SuppressWarnings(
            "java:S2637") // requireKey accepts "@NonNull" but warning states that null could be passed, seems like
    // false positive because of the !isValid(key) check
    public PreHandleContext requireKeyOrThrow(@Nullable final Key key, @NonNull final ResponseCodeEnum responseCode)
            throws PreCheckException {
        requireNonNull(responseCode);
        if (!isValid(key)) {
            throw new PreCheckException(responseCode);
        }

        // Verify this key isn't for an immutable account
        verifyIsNotImmutableAccount(key, responseCode);

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

        // Verify this key isn't for an immutable account
        verifyIsNotImmutableAccount(key, responseCode);

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

        // Verify this key isn't for an immutable account
        verifyIsNotImmutableAccount(key, responseCode);

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

        // Verify this key isn't for an immutable account
        verifyIsNotImmutableAccount(key, responseCode);

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

        // Verify this key isn't for an immutable account
        verifyIsNotImmutableAccount(key, responseCode);

        return requireKey(key);
    }

    @Override
    @NonNull
    public PreHandleContext requireSignatureForHollowAccount(@NonNull final Account hollowAccount) {
        requireNonNull(hollowAccount);
        final AccountID id = hollowAccount.accountId();
        if (!isHollow(hollowAccount)) {
            throw new IllegalArgumentException("Account %d.%d.%d is not a hollow account"
                    .formatted(id.shardNum(), id.realmNum(), id.accountNum()));
        }

        requiredHollowAccounts.add(hollowAccount);
        return this;
    }

    @Override
    @NonNull
    public PreHandleContext requireSignatureForHollowAccountCreation(@NonNull final Bytes hollowAccountAlias) {
        requireNonNull(hollowAccountAlias);
        requiredHollowAccounts.add(Account.newBuilder()
                .accountId(AccountID.DEFAULT)
                .key(EMPTY_KEY_LIST)
                .alias(hollowAccountAlias)
                .build());
        return this;
    }

    @NonNull
    @Override
    public TransactionKeys allKeysForTransaction(
            @NonNull TransactionBody nestedTxn, @NonNull final AccountID payerForNested) throws PreCheckException {
        dispatcher.dispatchPureChecks(nestedTxn);
        final var nestedContext =
                new PreHandleContextImpl(storeFactory, nestedTxn, payerForNested, configuration, dispatcher);
        dispatcher.dispatchPreHandle(nestedContext);
        return nestedContext;
    }

    @Override
    @NonNull
    public PreHandleContext createNestedContext(
            @NonNull final TransactionBody nestedTxn, @NonNull final AccountID payerForNested)
            throws PreCheckException {
        this.innerContext =
                new PreHandleContextImpl(storeFactory, nestedTxn, payerForNested, configuration, dispatcher);
        return this.innerContext;
    }

    @Override
    @Nullable
    public PreHandleContext innerContext() {
        return innerContext;
    }

    @Override
    public String toString() {
        return "PreHandleContextImpl{" + "accountStore="
                + accountStore + ", txn="
                + txn + ", payer="
                + payer + ", payerKey="
                + payerKey + ", requiredNonPayerKeys="
                + requiredNonPayerKeys + ", innerContext="
                + innerContext + ", storeFactory="
                + storeFactory + '}';
    }
}
