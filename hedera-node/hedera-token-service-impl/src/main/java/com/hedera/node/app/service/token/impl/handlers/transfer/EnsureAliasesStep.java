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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This step ensures that all aliases are resolved to their canonical forms. The resolved forms are
 * stored in TransferContext and then used in the rest of the transfer logic.
 */
public class EnsureAliasesStep implements TransferStep {
    final CryptoTransferTransactionBody op;

    /* ---- temporary token transfer resolutions map containing the token transfers to alias, is needed to check if
    an alias is repeated. It is allowed to be repeated in multiple token transfer lists, but not in a single
    token transfer list ---- */
    private final Map<Bytes, AccountID> tokenTransferResolutions = new HashMap<>();

    public EnsureAliasesStep(final CryptoTransferTransactionBody op) {
        this.op = op;
    }

    @Override
    public Set<Key> authorizingKeysIn(final TransferContext transferContext) {
        return Set.of();
    }

    @Override
    public void doIn(final TransferContext transferContext) {
        final var hbarTransfers = op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList());
        final var tokenTransfers = op.tokenTransfersOrElse(emptyList());
        // resolve hbar adjusts and add all alias resolutions to resolutions map in TransferContext
        resolveHbarAdjusts(hbarTransfers, transferContext);
        // resolve hbar adjusts and add all alias resolutions to resolutions map
        // and token resolutions map in TransferContext
        resolveTokenAdjusts(tokenTransfers, transferContext);
    }

    private void resolveTokenAdjusts(
            final List<TokenTransferList> tokenTransfers, final TransferContext transferContext) {
        for (final var tt : tokenTransfers) {
            tokenTransferResolutions.clear();
            for (final var adjust : tt.transfersOrElse(emptyList())) {
                if (isAlias(adjust.accountID())) {
                    final var account = resolveForFungibleToken(adjust, transferContext);
                    tokenTransferResolutions.put(adjust.accountID().alias(), account);
                    validateTrue(account != null, INVALID_ACCOUNT_ID);
                }
            }

            for (final var nftAdjust : tt.nftTransfersOrElse(emptyList())) {
                resolveForNft(nftAdjust, transferContext);
            }
        }
    }

    /**
     * Resolve hbar adjusts and add all alias resolutions to resolutions map in TransferContext.
     * If the accountID is an alias and is already in the resolutions map, it will be returned.
     * If the accountID is an alias and is not in the resolutions map, it will be autoCreated and
     * will be added to resolutions map.
     * @param hbarTransfers the hbar transfers to resolve
     * @param transferContext the transfer context
     */
    private void resolveHbarAdjusts(final List<AccountAmount> hbarTransfers, final TransferContext transferContext) {
        for (final var aa : hbarTransfers) {
            resolveForHbar(aa, transferContext);
        }
    }

    private void resolveForHbar(final AccountAmount adjust, TransferContext transferContext) {
        final var accountId = adjust.accountIDOrThrow();
        if (isAlias(accountId)) {
            final var account = transferContext.getFromAlias(accountId);
            if (adjust.amount() > 0) {
                if (account == null) {
                    final var isInResolutions = transferContext.resolutions().containsKey(accountId.alias());
                    validateTrue(!isInResolutions, ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS);
                    transferContext.createFromAlias(accountId.alias(), false);
                } else {
                    validateTrue(account != null, INVALID_ACCOUNT_ID);
                }
            } else {
                validateTrue(account != null, INVALID_ACCOUNT_ID);
            }
        }
    }

    private AccountID resolveForFungibleToken(final AccountAmount adjust, TransferContext transferContext) {
        final var accountId = adjust.accountIDOrThrow();
        final var account = transferContext.getFromAlias(accountId);
        if (account == null) {
            // If the token resolutions map already contains this unknown alias, we can assume
            // it was successfully auto-created by a prior mention in this CryptoTransfer.
            // (If it appeared in a sender location, this transfer will fail anyway.)
            validateFalse(tokenTransferResolutions.containsKey(accountId.alias()), INVALID_ALIAS_KEY);
            final var isInResolutions = transferContext.resolutions().containsKey(accountId.alias());
            if (adjust.amount() > 0 && !isInResolutions) {
                transferContext.createFromAlias(accountId.alias(), true);
            } else {
                validateTrue(transferContext.resolutions().containsKey(accountId.alias()), INVALID_ACCOUNT_ID);
            }
        }
        return account;
    }

    private void resolveForNft(final NftTransfer nftAdjust, TransferContext transferContext) {
        final var receiverId = nftAdjust.receiverAccountIDOrThrow();
        final var senderId = nftAdjust.senderAccountIDOrThrow();
        // sender can't be a missing accountId. It will fail if the alias doesn't exist
        if (isAlias(senderId)) {
            final var sender = transferContext.getFromAlias(senderId);
            validateTrue(sender != null, INVALID_ACCOUNT_ID);
        }
        // Note a repeated alias is still valid for the NFT receiver case
        if (isAlias(receiverId)) {
            final var receiver = transferContext.getFromAlias(receiverId);
            if (receiver == null) {
                final var isInResolutions = transferContext.resolutions().containsKey(senderId.alias());
                if (!isInResolutions) {
                    transferContext.createFromAlias(receiverId.alias(), false);
                }
            } else {
                validateTrue(receiver != null, INVALID_ACCOUNT_ID);
            }
        }
    }

    /**
     * Check if the given accountID is an alias
     * @param accountID the accountID to check
     * @return true if the accountID is an alias, false otherwise
     */
    public static boolean isAlias(AccountID accountID) {
        return accountID.hasAlias() && accountID.accountNum() == 0L;
    }
}
