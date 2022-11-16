/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.token.impl.AccountStore;
import com.hedera.node.app.service.token.impl.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Metadata collected when transactions are handled as part of "pre-handle" needed for signature
 * verification. This class may have subclasses in the future.
 *
 * <p>NOTE: This class shouldn't exist here, and is something of a puzzle. We cannot add it to SPI,
 * because it includes a dependency on AccountStore. But we also cannot put it in the app module,
 * because doing so would cause service modules to have a circular dependency on the app module.
 * Maybe we need some kind of base module from which services can extend and put it there?
 */
public class SigTransactionMetadata implements TransactionMetadata {
    protected List<HederaKey> requiredKeys = new ArrayList<>();
    protected TransactionBody txn;
    protected AccountStore store;
    protected AccountID payer;

    protected ResponseCodeEnum status = OK;

    public SigTransactionMetadata(
            final AccountStore store,
            final TransactionBody txn,
            final AccountID payer,
            final List<HederaKey> otherKeys) {
        this.store = store;
        this.txn = txn;
        this.payer = payer;
        requiredKeys.addAll(otherKeys);
        addPayerKey();
    }

    public SigTransactionMetadata(
            final AccountStore store, final TransactionBody txn, final AccountID payer) {
        this(store, txn, payer, Collections.emptyList());
    }

    @Override
    public TransactionBody getTxn() {
        return txn;
    }

    @Override
    public List<HederaKey> getReqKeys() {
        return requiredKeys;
    }

    @Override
    public ResponseCodeEnum status() {
        return status;
    }

    /* ---------- Mutating methods ---------- */
    @Override
    public void setStatus(final ResponseCodeEnum status) {
        this.status = status;
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account. If the lookup fails, sets the default failureReason given in the result.
     *
     * @param id given accountId
     */
    public void addNonPayerKey(final AccountID id) {
        addNonPayerKey(id, null);
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account. If the lookup fails, sets the given failure reason on the metadata. If the
     * failureReason is null, sets the default failureReason given in the result.
     *
     * @param id given accountId
     * @param failureStatus given failure status
     */
    public void addNonPayerKey(final AccountID id, final ResponseCodeEnum failureStatus) {
        if (isNotNeeded(id)) {
            return;
        }
        final var result = store.getKey(id);
        failOrAddToKeys(result, failureStatus);
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account if receiverSigRequired is true on the account. If the lookup fails, sets the
     * given failure reason on the metadata. If the failureReason is null, sets the default
     * failureReason given in the result.
     *
     * @param id given accountId
     * @param failureStatus given failure status
     */
    public void addNonPayerKeyIfReceiverSigRequired(
            final AccountID id, final ResponseCodeEnum failureStatus) {
        if (isNotNeeded(id)) {
            return;
        }
        final var result = store.getKeyIfReceiverSigRequired(id);
        failOrAddToKeys(result, failureStatus);
    }

    /* ---------- Helper methods ---------- */

    /**
     * Look up the keys for payer account and add payer key to the required keys list. If the lookup
     * fails adds failure status {@code INVALID_PAYER_ACCOUNT_ID} to the metadata.
     */
    private void addPayerKey() {
        final var result = store.getKey(payer);
        failOrAddToKeys(result, INVALID_PAYER_ACCOUNT_ID);
    }

    /**
     * Checks if the account given is same as payer or if the metadata is already failed. In either
     * case, no need to lookup tha account's key.
     *
     * @param id given account
     * @return true if the lookup is not needed, false otherwise
     */
    private boolean isNotNeeded(@NotNull final AccountID id) {
        return id.equals(payer)
                || id.equals(AccountID.getDefaultInstance())
                || designatesAccountRemoval(id)
                || status != OK;
    }

    /**
     * Checks if the accountId is a sentinel id 0.0.0
     *
     * @param id given accountId
     * @return true if the given accountID is
     */
    private boolean designatesAccountRemoval(@NotNull AccountID id) {
        return id.getShardNum() == 0
                && id.getRealmNum() == 0
                && id.getAccountNum() == 0
                && id.getAlias().isEmpty();
    }

    /**
     * Given a successful key lookup, adds its key to the required signers. Given a failed key
     * lookup, sets this SigTransactionMetadata's status to either the failure reason of the lookup;
     * or (if it is non-null), the requested failureStatus parameter.
     *
     * @param result key lookup result
     * @param failureStatus failure reason for the lookup
     */
    private void failOrAddToKeys(
            final KeyOrLookupFailureReason result, @Nullable final ResponseCodeEnum failureStatus) {
        if (result.failed()) {
            this.status = failureStatus != null ? failureStatus : result.failureReason();
        } else if (result.key() != null) {
            requiredKeys.add(result.key());
        }
    }
}
