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
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Interface for constructing {@link TransactionMetadata} by collecting information that is needed
 * when transactions are handled as part of "pre-handle" needed for signature verification.
 */
public class PrehandleHandlerContext {

    private final AccountKeyLookup keyLookup;

    private final TransactionBody txn;
    private final AccountID payer;
    private final List<HederaKey> requiredNonPayerKeys = new ArrayList<>();

    private ResponseCodeEnum status = OK;
    private HederaKey payerKey;
    private Object handlerMetadata;

    public PrehandleHandlerContext(
            @NonNull final AccountKeyLookup keyLookup,
            @NonNull final TransactionBody txn,
            @NonNull final AccountID payer) {
        this.keyLookup = requireNonNull(keyLookup);
        this.txn = requireNonNull(txn);
        this.payer = requireNonNull(payer);

        final var lookedUpPayerKey = keyLookup.getKey(payer);
        addToKeysOrFail(lookedUpPayerKey, INVALID_PAYER_ACCOUNT_ID, true);
    }

    public PrehandleHandlerContext(
            @NonNull final AccountKeyLookup keyLookup, @NonNull final TransactionBody txn) {
        this(keyLookup, txn, txn.getTransactionID().getAccountID());
    }

    /**
     * Getter for the {@link TransactionBody}
     *
     * @return the {@link TransactionBody} in this context
     */
    @NonNull
    public TransactionBody getTxn() {
        return txn;
    }

    /**
     * Getter for the payer
     *
     * @return the {@link AccountID} of the payer in this context
     */
    @NonNull
    public AccountID getPayer() {
        return payer;
    }

    /**
     * Returns the list of required non-payer keys.
     *
     * @return the {@link List} with the required non-payer keys
     */
    public List<HederaKey> getRequiredNonPayerKeys() {
        return requiredNonPayerKeys;
    }

    /**
     * Getter for the status
     *
     * @return the {@code status} that was previously set
     */
    public ResponseCodeEnum getStatus() {
        return status;
    }

    /**
     * Checks the failure by validating the status is not {@link ResponseCodeEnum OK}
     *
     * @return returns true if status is not OK
     */
    public boolean failed() {
        return !getStatus().equals(ResponseCodeEnum.OK);
    }

    /**
     * Sets status on {@link TransactionMetadata}. It will be {@link ResponseCodeEnum#OK} if there
     * is no failure.
     *
     * @param status status to be set on {@link TransactionMetadata}
     * @return {@code this} object
     */
    @NonNull
    public PrehandleHandlerContext status(@NonNull final ResponseCodeEnum status) {
        this.status = requireNonNull(status);
        return this;
    }

    /**
     * Getter for the payer key
     *
     * @return the payer key
     */
    @Nullable
    public HederaKey getPayerKey() {
        return payerKey;
    }

    /**
     * Getter for the metadata set by the handler
     *
     * @return the metadata set by the handler
     */
    public Object getHandlerMetadata() {
        return handlerMetadata;
    }

    /**
     * Sets the handler specific metadata
     *
     * @param handlerMetadata an arbitrary object that gets passed to the handler method
     * @return builder object
     */
    @NonNull
    public PrehandleHandlerContext handlerMetadata(@NonNull final Object handlerMetadata) {
        this.handlerMetadata = handlerMetadata;
        return this;
    }

    /**
     * Add a keys to required keys list
     *
     * @param keys list of keys to add
     * @return {@code this} object
     */
    @NonNull
    public PrehandleHandlerContext addAllReqKeys(@NonNull final List<HederaKey> keys) {
        requiredNonPayerKeys.addAll(requireNonNull(keys));
        return this;
    }

    /**
     * Adds given key to required non-payer keys in {@link TransactionMetadata}. If the status is
     * already failed, or if the payer's key is not added, given keys will not be added to
     * requiredNonPayerKeys list. This method is used when the payer's key is already fetched, and
     * we want to add other keys from {@link TransactionBody} to required keys to sign.
     *
     * @param key key to be added
     * @return {@code this} object
     */
    @NonNull
    public PrehandleHandlerContext addToReqNonPayerKeys(@NonNull final HederaKey key) {
        if (status == OK && payerKey != null) {
            requiredNonPayerKeys.add(requireNonNull(key));
        }
        return this;
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account. If the lookup fails, sets the default failureReason given in the result.
     *
     * @param id given accountId
     * @return {@code this} object
     */
    @NonNull
    public PrehandleHandlerContext addNonPayerKey(@NonNull final AccountID id) {
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
     * @return {@code this} object
     */
    @NonNull
    public PrehandleHandlerContext addNonPayerKey(
            @NonNull final AccountID id, @Nullable final ResponseCodeEnum failureStatusToUse) {
        if (isNotNeeded(requireNonNull(id))) {
            return this;
        }
        final var result = keyLookup.getKey(id);
        addToKeysOrFail(result, failureStatusToUse, false);
        return this;
    }

    @NonNull
    public PrehandleHandlerContext addNonPayerKey(@NonNull final ContractID id) {
        if (isNotNeeded(requireNonNull(id))) {
            return this;
        }
        final var result = keyLookup.getKey(id);
        addToKeysOrFail(result, null, false);
        return this;
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
    @NonNull
    public PrehandleHandlerContext addNonPayerKeyIfReceiverSigRequired(
            @NonNull final AccountID id, @Nullable final ResponseCodeEnum failureStatusToUse) {
        if (isNotNeeded(requireNonNull(id))) {
            return this;
        }
        final var result = keyLookup.getKeyIfReceiverSigRequired(id);
        addToKeysOrFail(result, failureStatusToUse, false);
        return this;
    }

    @NonNull
    public PrehandleHandlerContext addNonPayerKeyIfReceiverSigRequired(@NonNull final ContractID id) {
        if (isNotNeeded(requireNonNull(id))) {
            return this;
        }
        final var result = keyLookup.getKeyIfReceiverSigRequired(id);
        addToKeysOrFail(result, null, false);
        return this;
    }


    @Override
    public String toString() {
        return "TransactionMetadataBuilder{"
                + "requiredNonPayerKeys="
                + requiredNonPayerKeys
                + ", txn="
                + txn
                + ", payer="
                + payer
                + ", status="
                + status
                + ", payerKey="
                + payerKey
                + ", handlerMetadata="
                + handlerMetadata
                + '}';
    }

    /* ---------- Helper methods ---------- */

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
     * Checks if the metadata is already failed. In this case no need to look up that contract's key
     * If the payer key has not been set we don't add other keys.
     *
     * @param id given contract
     * @return true if the lookup is not needed, false otherwise
     */
    private boolean isNotNeeded(@NonNull final ContractID id) {
        return id.equals(ContractID.getDefaultInstance())
                || designatesContractRemoval(id)
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
     * Checks if the contractId is a sentinel id <code>0.0.0</code>
     *
     * @param id given contractId
     * @return true if the given contractId is
     */
    private boolean designatesContractRemoval(@NonNull final ContractID id) {
        return id.getShardNum() == 0
                && id.getRealmNum() == 0
                && id.getContractNum() == 0
                && id.getEvmAddress().isEmpty();
    }

    /**
     * Given a successful key lookup, adds its key to the required signers. Given a failed key
     * lookup, sets this {@link TransactionMetadata}'s status to either the failure reason of the
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
}
