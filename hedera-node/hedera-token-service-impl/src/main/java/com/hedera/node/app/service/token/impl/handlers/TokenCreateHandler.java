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

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.meta.PrehandleHandlerContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#TokenCreate}.
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
     * @param context the {@link PrehandleHandlerContext} which collects all information that will
     *     be passed to {@link #handle(TransactionMetadata)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PrehandleHandlerContext context) {
        requireNonNull(context);
        final var tokenCreateTxnBody = context.getTxn().getTokenCreation();
        if (tokenCreateTxnBody.hasTreasury()) {
            final var treasuryId = tokenCreateTxnBody.getTreasury();
            context.addNonPayerKey(treasuryId, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
        }
        if (tokenCreateTxnBody.hasAutoRenewAccount()) {
            final var autoRenewalAccountId = tokenCreateTxnBody.getAutoRenewAccount();
            context.addNonPayerKey(autoRenewalAccountId, INVALID_AUTORENEW_ACCOUNT);
        }
        if (tokenCreateTxnBody.hasAdminKey()) {
            final var adminKey = asHederaKey(tokenCreateTxnBody.getAdminKey());
            adminKey.ifPresent(context::addToReqNonPayerKeys);
        }
        final var customFees = tokenCreateTxnBody.getCustomFeesList();
        addCustomFeeCollectorKeys(context, customFees);
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
        requireNonNull(metadata);
        throw new UnsupportedOperationException("Not implemented");
    }

    /* --------------- Helper methods --------------- */

    /**
     * Validates the collector key from the custom fees.
     *
     * @param context given context
     * @param customFeesList list with the custom fees
     */
    private void addCustomFeeCollectorKeys(
            @NonNull final PrehandleHandlerContext context,
            @NonNull final List<CustomFee> customFeesList) {

        for (final var customFee : customFeesList) {
            final var collector = customFee.getFeeCollectorAccountId();

            /* A fractional fee collector and a collector for a fixed fee denominated
            in the units of the newly created token both must always sign a TokenCreate,
            since these are automatically associated to the newly created token. */
            if (customFee.hasFixedFee()) {
                final var fixedFee = customFee.getFixedFee();
                final var alwaysAdd =
                        fixedFee.hasDenominatingTokenId()
                                && fixedFee.getDenominatingTokenId().getTokenNum() == 0L;
                addAccount(context, collector, alwaysAdd);
            } else if (customFee.hasFractionalFee()) {
                context.addNonPayerKey(collector, INVALID_CUSTOM_FEE_COLLECTOR);
            } else {
                final var royaltyFee = customFee.getRoyaltyFee();
                var alwaysAdd = false;
                if (royaltyFee.hasFallbackFee()) {
                    final var fFee = royaltyFee.getFallbackFee();
                    alwaysAdd =
                            fFee.hasDenominatingTokenId()
                                    && fFee.getDenominatingTokenId().getTokenNum() == 0;
                }
                addAccount(context, collector, alwaysAdd);
            }
        }
    }

    /**
     * Signs the metadata or adds failure status.
     *
     * @param context given context
     * @param collector the ID of the collector
     * @param alwaysAdd if true, will always add the key
     */
    private void addAccount(
            final PrehandleHandlerContext context,
            final AccountID collector,
            final boolean alwaysAdd) {
        if (alwaysAdd) {
            context.addNonPayerKey(collector, INVALID_CUSTOM_FEE_COLLECTOR);
        } else {
            context.addNonPayerKeyIfReceiverSigRequired(collector, INVALID_CUSTOM_FEE_COLLECTOR);
        }
    }
}
