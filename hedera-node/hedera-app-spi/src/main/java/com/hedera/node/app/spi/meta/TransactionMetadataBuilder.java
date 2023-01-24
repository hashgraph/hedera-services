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
package com.hedera.node.app.spi.meta;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Base abstract class for constructing {@link TransactionMetadata} by collecting information that
 * is needed when transactions are handled as part of "pre-handle" needed for signature
 * verification.
 *
 * <p>NOTE : This class is designed to be subclassed
 */
@SuppressWarnings("UnusedReturnValue")
public abstract class TransactionMetadataBuilder<T extends TransactionMetadataBuilder<T>> {
    protected final List<HederaKey> requiredNonPayerKeys = new ArrayList<>();
    protected final List<TransactionMetadata.ReadKeys> readKeys = new ArrayList<>();
    protected HederaKey payerKey;
    protected ResponseCodeEnum status = OK;
    protected TransactionBody txn;
    protected final AccountKeyLookup keyLookup;
    protected AccountID payer;

    protected TransactionMetadataBuilder(@NonNull final AccountKeyLookup keyLookup) {
        this.keyLookup = Objects.requireNonNull(keyLookup);
    }

    /**
     * Getter for the {@link TransactionBody}
     *
     * @return the {@link TransactionBody} that was previously set
     */
    public TransactionBody getTxn() {
        return txn;
    }

    /**
     * Getter for the payer
     *
     * @return the {@link AccountID} of the payer
     */
    public AccountID getPayer() {
        return payer;
    }

    /**
     * Sets status on {@link TransactionMetadata}. It will be {@link ResponseCodeEnum#OK} if there
     * is no failure.
     *
     * @param status status to be set on {@link TransactionMetadata}
     * @return builder object
     */
    public T status(@NonNull final ResponseCodeEnum status) {
        this.status = Objects.requireNonNull(status);
        return self();
    }

    /**
     * Add a keys to required keys list
     *
     * @param keys list of keys to add
     * @return builder object
     */
    public T addAllReqKeys(@NonNull final List<HederaKey> keys) {
        requiredNonPayerKeys.addAll(Objects.requireNonNull(keys));
        return self();
    }

    /**
     * Fetches the payer key and add to required keys in {@link TransactionMetadata}.
     *
     * @param payer payer for the transaction
     * @return builder object
     */
    public T payerKeyFor(@NonNull AccountID payer) {
        this.payer = Objects.requireNonNull(payer);
        addPayerKey();
        return self();
    }

    /**
     * Adds given key to required non-payer keys in {@link TransactionMetadata}. If the status is
     * already failed, or if the payer's key is not added, given keys will not be added to
     * requiredNonPayerKeys list. This method is used when the payer's key is already fetched, and
     * we want to add other keys from {@link TransactionBody} to required keys to sign.
     *
     * @param key key to be added
     * @return builder object
     */
    public T addToReqNonPayerKeys(@NonNull HederaKey key) {
        if (status != OK || payerKey == null) {
            return self();
        }
        requiredNonPayerKeys.add(Objects.requireNonNull(key));
        return self();
    }

    /**
     * Adds the {@link TransactionBody} of the transaction on {@link TransactionMetadata}.
     *
     * @param txn transaction body of the transaction
     * @return builder object
     */
    public T txnBody(@NonNull TransactionBody txn) {
        this.txn = Objects.requireNonNull(txn);
        return self();
    }

    /**
     * Adds a {@link Set} of read keys for a {@link com.hedera.node.app.spi.state.ReadableKVState}
     *
     * @param statesKey the key of the {@link com.hedera.node.app.spi.state.ReadableStates}
     * @param stateKey the key of the {@link com.hedera.node.app.spi.state.ReadableKVState}
     * @param readKeys the read keys
     * @return builder object
     */
    public T addReadKeys(
            final String statesKey,
            final String stateKey,
            final Set<? extends Comparable<?>> readKeys) {
        this.readKeys.add(new TransactionMetadata.ReadKeys(statesKey, stateKey, readKeys));
        return self();
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account. If the lookup fails, sets the default failureReason given in the result.
     *
     * @param id given accountId
     */
    public T addNonPayerKey(@NonNull final AccountID id) {
        return addNonPayerKey(id, null);
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account. If the lookup fails, sets the given failure reason on the metadata. If the
     * failureReason is null, sets the default failureReason given in the result.
     *
     * @param id given accountId
     * @param failureStatusToUse failure status to be set if there is failure
     */
    public T addNonPayerKey(
            @NonNull final AccountID id, @Nullable final ResponseCodeEnum failureStatusToUse) {
        if (isNotNeeded(Objects.requireNonNull(id))) {
            return self();
        }
        final var result = keyLookup.getKey(id);
        addToKeysOrFail(result, failureStatusToUse, false);
        return self();
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account if receiverSigRequired is true on the account. If the lookup fails, sets the
     * given failure reason on the metadata. If the failureReason is null, sets the default
     * failureReason given in the result.
     *
     * @param id given accountId
     * @param failureStatusToUse failure status to be set if there is failure
     */
    public T addNonPayerKeyIfReceiverSigRequired(
            @NonNull final AccountID id, @Nullable final ResponseCodeEnum failureStatusToUse) {
        if (isNotNeeded(Objects.requireNonNull(id))) {
            return self();
        }
        final var result = keyLookup.getKeyIfReceiverSigRequired(id);
        addToKeysOrFail(result, failureStatusToUse, false);
        return self();
    }

    /* ---------- Helper methods ---------- */

    /**
     * Look up the keys for payer account and add payer key to the required keys list. If the lookup
     * fails adds failure status {@code INVALID_PAYER_ACCOUNT_ID} to the metadata.
     */
    private void addPayerKey() {
        final var result = keyLookup.getKey(payer);
        addToKeysOrFail(result, INVALID_PAYER_ACCOUNT_ID, true);
    }

    /**
     * Checks if the account given is same as payer or if the metadata is already failed. In either
     * case, no need to look up that account's key. If the payer key has not been set we don't add
     * other keys.
     *
     * @param id given account
     * @return true if the lookup is not needed, false otherwise
     */
    private boolean isNotNeeded(@NonNull final AccountID id) {
        return id.equals(payer)
                || id.equals(AccountID.getDefaultInstance())
                || designatesAccountRemoval(id)
                || status != OK
                || payerKey == null;
    }

    /**
     * Checks if the accountId is a sentinel id 0.0.0
     *
     * @param id given accountId
     * @return true if the given accountID is
     */
    private boolean designatesAccountRemoval(@NonNull final AccountID id) {
        return id.getShardNum() == 0
                && id.getRealmNum() == 0
                && id.getAccountNum() == 0
                && id.getAlias().isEmpty();
    }

    /**
     * Given a successful key lookup, adds its key to the required signers. Given a failed key
     * lookup, sets this {@link SigTransactionMetadata}'s status to either the failure reason of the
     * lookup; or (if it is non-null), the requested failureStatus parameter.
     *
     * @param result key lookup result
     * @param failureStatus failure reason for the lookup
     */
    private void addToKeysOrFail(
            final KeyOrLookupFailureReason result,
            @Nullable final ResponseCodeEnum failureStatus,
            final boolean isPayer) {
        if (result.failed()) {
            this.status = failureStatus != null ? failureStatus : result.failureReason();
        } else if (result.key() != null) {
            if (isPayer) {
                this.payerKey = result.key();
            } else {
                this.requiredNonPayerKeys.add(result.key());
            }
        }
    }

    /**
     * Creates and returns a new {@link TransactionMetadata} based on the values configured in this
     * builder.
     *
     * @return a new {@link SigTransactionMetadata}
     */
    public abstract TransactionMetadata build();

    /**
     * Returns the builder object.
     *
     * @return builder object
     */
    protected abstract T self();
}
