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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_CREATE}.
 */
@Singleton
public class TokenCreateHandler implements TransactionHandler {
    @Inject
    public TokenCreateHandler() {
        // Exists for injection
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, warms the cache.
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *     passed to {@link #handle(TokenCreateTransactionBody)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context) {
        requireNonNull(context);
        final var tokenCreateTxnBody = context.getTxn().tokenCreationOrThrow();
        if (tokenCreateTxnBody.hasTreasury()) {
            final var treasuryId = tokenCreateTxnBody.treasuryOrThrow();
            context.addNonPayerKey(treasuryId, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
        }
        if (tokenCreateTxnBody.hasAutoRenewAccount()) {
            final var autoRenewalAccountId = tokenCreateTxnBody.autoRenewAccountOrThrow();
            context.addNonPayerKey(autoRenewalAccountId, INVALID_AUTORENEW_ACCOUNT);
        }
        if (tokenCreateTxnBody.hasAdminKey()) {
            final var adminKey = asHederaKey(tokenCreateTxnBody.adminKeyOrThrow());
            adminKey.ifPresent(context::addToReqNonPayerKeys);
        }
        final var customFees = tokenCreateTxnBody.customFeesOrElse(emptyList());
        addCustomFeeCollectorKeys(context, customFees);
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * @param tx the transaction to handle
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final TokenCreateTransactionBody tx) {
        requireNonNull(tx);
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
            @NonNull final PreHandleContext context, @NonNull final List<CustomFee> customFeesList) {

        for (final var customFee : customFeesList) {
            final var collector = customFee.feeCollectorAccountIdOrElse(AccountID.DEFAULT);

            /* A fractional fee collector and a collector for a fixed fee denominated
            in the units of the newly created token both must always sign a TokenCreate,
            since these are automatically associated to the newly created token. */
            if (customFee.hasFixedFee()) {
                final var fixedFee = customFee.fixedFeeOrThrow();
                final var alwaysAdd = fixedFee.hasDenominatingTokenId()
                        && fixedFee.denominatingTokenIdOrThrow().tokenNum() == 0L;
                addAccount(context, collector, alwaysAdd);
            } else if (customFee.hasFractionalFee()) {
                context.addNonPayerKey(collector, INVALID_CUSTOM_FEE_COLLECTOR);
            } else {
                // TODO: Need to validate if this is actually needed
                final var royaltyFee = customFee.royaltyFeeOrThrow();
                var alwaysAdd = false;
                if (royaltyFee.hasFallbackFee()) {
                    final var fFee = royaltyFee.fallbackFeeOrThrow();
                    alwaysAdd = fFee.hasDenominatingTokenId()
                            && fFee.denominatingTokenIdOrThrow().tokenNum() == 0;
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
    private void addAccount(final PreHandleContext context, final AccountID collector, final boolean alwaysAdd) {
        if (alwaysAdd) {
            context.addNonPayerKey(collector, INVALID_CUSTOM_FEE_COLLECTOR);
        } else {
            context.addNonPayerKeyIfReceiverSigRequired(collector, INVALID_CUSTOM_FEE_COLLECTOR);
        }
    }
}
