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
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Context of a single {@code preHandle()}-call. Contains all information that needs to be exchanged between the
 * pre-handle workflow and the {@code preHandle()}-method of a
 * {@link com.hedera.node.app.spi.workflows.TransactionHandler}.
 */
public class PreHandleContext {

    private final AccountAccess accountAccess;

    private final TransactionBody txn;
    private final AccountID payer;
    private final List<HederaKey> requiredNonPayerKeys = new ArrayList<>();

    private ResponseCodeEnum status;
    private HederaKey payerKey;
    private PreHandleContext innerContext;

    public PreHandleContext(@NonNull final AccountAccess accountAccess, @NonNull final TransactionBody txn) {
        this(accountAccess, txn, txn.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT), OK);
    }

    public PreHandleContext(
            @NonNull final AccountAccess accountAccess,
            @NonNull final TransactionBody txn,
            @NonNull final AccountID payer) {
        this(accountAccess, txn, payer, OK);
    }

    public PreHandleContext(
            @NonNull final AccountAccess accountAccess,
            @NonNull final TransactionBody txn,
            @NonNull final ResponseCodeEnum status) {
        this(
                accountAccess,
                txn,
                txn.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT),
                status);
    }

    public PreHandleContext(
            @NonNull final AccountAccess accountAccess,
            @NonNull final TransactionBody txn,
            @NonNull final AccountID payer,
            @NonNull final ResponseCodeEnum status) {
        this.accountAccess = requireNonNull(accountAccess);
        this.txn = requireNonNull(txn);
        this.payer = requireNonNull(payer);
        this.status = requireNonNull(status);

        final var lookedUpPayerKey = accountAccess.getKey(payer);
        addToKeysOrFail(lookedUpPayerKey, INVALID_PAYER_ACCOUNT_ID, true);
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
     * Returns an immutable copy of the list of required non-payer keys.
     *
     * @return the {@link List} with the required non-payer keys
     */
    public List<HederaKey> getRequiredNonPayerKeys() {
        return List.copyOf(requiredNonPayerKeys);
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
     * Sets status on {@link PreHandleContext}. It will be {@link ResponseCodeEnum#OK} if there is no failure.
     *
     * @param status status of the pre-handle workflow
     * @return {@code this} object
     */
    @NonNull
    public PreHandleContext status(@NonNull final ResponseCodeEnum status) {
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
     * Add a keys to required keys list
     *
     * @param keys list of keys to add
     * @return {@code this} object
     */
    @NonNull
    public PreHandleContext addAllReqKeys(@NonNull final List<HederaKey> keys) {
        requiredNonPayerKeys.addAll(requireNonNull(keys));
        return this;
    }

    /**
     * Adds given key to required non-payer keys in {@link PreHandleContext}. If the status is already failed, or if the
     * payer's key is not added, given keys will not be added to requiredNonPayerKeys list. This method is used when the
     * payer's key is already fetched, and we want to add other keys from {@link TransactionBody} to required keys to
     * sign.
     *
     * @param key key to be added
     * @return {@code this} object
     */
    @NonNull
    public PreHandleContext addToReqNonPayerKeys(@NonNull final HederaKey key) {
        if (status == OK && payerKey != null) {
            requiredNonPayerKeys.add(requireNonNull(key));
        }
        return this;
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If either of the above
     * is true, doesn't look up the keys for given account. Else, looks up the keys for account. If the lookup fails,
     * sets the default failureReason given in the result.
     *
     * @param id given accountId
     * @return {@code this} object
     */
    @NonNull
    public PreHandleContext addNonPayerKey(@NonNull final AccountID id) {
        return addNonPayerKey(id, null);
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If either of the above
     * is true, doesn't look up the keys for given account. Else, looks up the keys for account. If the lookup fails,
     * sets the given failure reason on the metadata. If the failureReason is null, sets the default failureReason given
     * in the result.
     *
     * @param id given accountId
     * @param failureStatusToUse failure status to be set if there is failure
     * @return {@code this} object
     */
    @NonNull
    public PreHandleContext addNonPayerKey(
            @NonNull final AccountID id, @Nullable final ResponseCodeEnum failureStatusToUse) {
        if (isNotNeeded(requireNonNull(id))) {
            return this;
        }
        final var result = accountAccess.getKey(id);
        addToKeysOrFail(result, failureStatusToUse, false);
        return this;
    }

    @NonNull
    public PreHandleContext addNonPayerKey(@NonNull final ContractID id) {
        if (isNotNeeded(requireNonNull(id))) {
            return this;
        }
        final var result = accountAccess.getKey(id);
        addToKeysOrFail(result, null, false);
        return this;
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If either of the above
     * is true, doesn't look up the keys for given account. Else, looks up the keys for account if receiverSigRequired
     * is true on the account. If the lookup fails, sets the given failure reason on the metadata. If the failureReason
     * is null, sets the default failureReason given in the result.
     *
     * @param id given accountId
     * @param failureStatusToUse failure status to be set if there is failure
     */
    @NonNull
    public PreHandleContext addNonPayerKeyIfReceiverSigRequired(
            @NonNull final AccountID id, @Nullable final ResponseCodeEnum failureStatusToUse) {
        if (isNotNeeded(requireNonNull(id))) {
            return this;
        }
        final var result = accountAccess.getKeyIfReceiverSigRequired(id);
        addToKeysOrFail(result, failureStatusToUse, false);
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public PreHandleContext addNonPayerKeyIfReceiverSigRequired(@NonNull final ContractID id) {
        if (isNotNeeded(requireNonNull(id))) {
            return this;
        }
        final var result = accountAccess.getKeyIfReceiverSigRequired(id);
        addToKeysOrFail(result, null, false);
        return this;
    }

    public PreHandleContext getInnerContext() {
        return innerContext;
    }

    public void setInnerContext(PreHandleContext innerContext) {
        this.innerContext = innerContext;
    }

    @Override
    public String toString() {
        return "PreHandleContext{" + "accountAccess="
                + accountAccess + ", txn="
                + txn + ", payer="
                + payer + ", requiredNonPayerKeys="
                + requiredNonPayerKeys + ", status="
                + status + ", payerKey="
                + payerKey + ", innerContext="
                + innerContext + '}';
    }

    /* ---------- Helper methods ---------- */

    /**
     * Checks if the account given is same as payer or if the metadata is already failed. In either case, no need to
     * look up that account's key. If the payer key has not been set we don't add other keys.
     *
     * @param id given account
     * @return true if the lookup is not needed, false otherwise
     */
    private boolean isNotNeeded(@NonNull final AccountID id) {
        return id.equals(payer)
                || id.equals(AccountID.DEFAULT)
                || designatesAccountRemoval(id)
                || status != OK
                || payerKey == null;
    }

    /**
     * Checks if the metadata is already failed. In this case no need to look up that contract's key If the payer key
     * has not been set we don't add other keys.
     *
     * @param id given contract
     * @return true if the lookup is not needed, false otherwise
     */
    private boolean isNotNeeded(@NonNull final ContractID id) {
        return id.equals(ContractID.DEFAULT) || designatesContractRemoval(id) || status != OK || payerKey == null;
    }

    /**
     * Checks if the accountId is a sentinel id 0.0.0
     *
     * @param id given accountId
     * @return true if the given accountID is
     */
    private boolean designatesAccountRemoval(@NonNull final AccountID id) {
        return id.shardNum() == 0 && id.realmNum() == 0 && id.accountNumOrElse(0L) == 0 && !id.hasAlias();
    }

    /**
     * Checks if the contractId is a sentinel id <code>0.0.0</code>
     *
     * @param id given contractId
     * @return true if the given contractId is
     */
    private boolean designatesContractRemoval(@NonNull final ContractID id) {
        return id.shardNum() == 0 && id.realmNum() == 0 && id.contractNumOrElse(0L) == 0 && !id.hasEvmAddress();
    }

    /**
     * Given a successful key lookup, adds its key to the required signers. Given a failed key lookup, sets this
     * {@link PreHandleContext}'s status to either the failure reason of the lookup; or (if it is non-null), the
     * requested failureStatus parameter.
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

    @NonNull
    public PreHandleContext createNestedContext(
            @NonNull final TransactionBody nestedTxn, @NonNull final AccountID payerForNested) {
        return new PreHandleContext(accountAccess, nestedTxn, payerForNested);
    }
}
