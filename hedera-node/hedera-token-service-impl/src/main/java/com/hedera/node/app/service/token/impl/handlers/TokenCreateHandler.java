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
package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.node.app.service.mono.Utils.asHederaKey;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_CREATE}.
 */
public class TokenCreateHandler implements TransactionHandler {

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, warms the cache, and creates the {@link TransactionMetadata} that is used in
     * the handle stage.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param txBody the {@link TransactionBody} with the transaction data
     * @param payer the {@link AccountID} of the payer
     * @param accountStore the {@link AccountKeyLookup} to use to resolve keys
     * @return the {@link TransactionMetadata} with all information that needs to be passed to
     *     {@link #handle(TransactionMetadata)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public TransactionMetadata preHandle(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID payer,
            @NonNull final AccountKeyLookup accountStore) {
        final var tokenCreateTxnBody = txBody.tokenCreation().orElseThrow();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txBody);
        if (tokenCreateTxnBody.treasury() != null) {
            final var treasuryId = tokenCreateTxnBody.treasury();
            meta.addNonPayerKey(treasuryId, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
        }
        if (tokenCreateTxnBody.autoRenewAccount() != null) {
            final var autoRenewalAccountId = tokenCreateTxnBody.autoRenewAccount();
            meta.addNonPayerKey(autoRenewalAccountId, INVALID_AUTORENEW_ACCOUNT);
        }
        if (tokenCreateTxnBody.adminKey() != null) {
            final var adminKey = asHederaKey(tokenCreateTxnBody.adminKey());
            adminKey.ifPresent(meta::addToReqNonPayerKeys);
        }
        final var customFees = tokenCreateTxnBody.customFees();
        addCustomFeeCollectorKeys(meta, customFees);
        return meta.build();
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param metadata the {@link TransactionMetadata} that was generated during pre-handle.
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final TransactionMetadata metadata) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /* --------------- Helper methods --------------- */

    /**
     * Validates the collector key from the custom fees.
     *
     * @param meta given transaction metadata
     * @param customFeesList list with the custom fees
     */
    private void addCustomFeeCollectorKeys(
            SigTransactionMetadataBuilder meta, final List<CustomFee> customFeesList) {

        for (final var customFee : customFeesList) {
            final var collector = customFee.feeCollectorAccountId();

            /* A fractional fee collector and a collector for a fixed fee denominated
            in the units of the newly created token both must always sign a TokenCreate,
            since these are automatically associated to the newly created token. */
            final var fixedFeeOpt = customFee.fixedFee();
            if (fixedFeeOpt.isPresent()) {
                final var fixedFee = fixedFeeOpt.get();
                final var alwaysAdd =
                        fixedFee.denominatingTokenId() != null
                                && fixedFee.denominatingTokenId().tokenNum() == 0L;
                addAccount(meta, collector, alwaysAdd);
            } else if (customFee.fractionalFee().isPresent()) {
                meta.addNonPayerKey(collector, INVALID_CUSTOM_FEE_COLLECTOR);
            } else {
                final var royaltyFee = customFee.royaltyFee().orElseThrow();
                var alwaysAdd = false;
                if (royaltyFee.fallbackFee() != null) {
                    final var fFee = royaltyFee.fallbackFee();
                    alwaysAdd =
                            fFee.denominatingTokenId() != null
                                    && fFee.denominatingTokenId().tokenNum() == 0;
                }
                addAccount(meta, collector, alwaysAdd);
            }
        }
    }

    /**
     * Signs the metadata or adds failure status.
     *
     * @param meta given transaction metadata
     * @param collector the ID of the collector
     * @param alwaysAdd if true, will always add the key
     */
    private void addAccount(
            final SigTransactionMetadataBuilder meta,
            final AccountID collector,
            final boolean alwaysAdd) {
        if (alwaysAdd) {
            meta.addNonPayerKey(collector, INVALID_CUSTOM_FEE_COLLECTOR);
        } else {
            meta.addNonPayerKeyIfReceiverSigRequired(collector, INVALID_CUSTOM_FEE_COLLECTOR);
        }
    }
}
