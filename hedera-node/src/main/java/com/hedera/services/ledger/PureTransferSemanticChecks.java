/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static java.math.BigInteger.ZERO;

import com.hedera.services.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Given a maximum number of ℏ and token unit balance changes in a CryptoTransfer, validates the
 * actual exchanges requested in such a transaction.
 *
 * <p>Since in the normal transaction lifecycle, this logic will be performed during {@link
 * com.hedera.services.txns.span.SpanMapManager#expandSpan(TxnAccessor)}, we can accept some
 * inefficient use of gRPC types.
 */
@Singleton
public class PureTransferSemanticChecks {
    @Inject
    public PureTransferSemanticChecks() {
        // Default constructor
    }

    public ResponseCodeEnum fullPureValidation(
            TransferList hbarAdjustsWrapper,
            List<TokenTransferList> tokenAdjustsList,
            ImpliedTransfersMeta.ValidationProps validationProps) {
        final var maxHbarAdjusts = validationProps.maxHbarAdjusts();
        final var maxTokenAdjusts = validationProps.maxTokenAdjusts();
        final var maxOwnershipChanges = validationProps.maxOwnershipChanges();
        final var areNftsEnabled = validationProps.areNftsEnabled();
        final var areAllowanceEnabled = validationProps.areAllowancesEnabled();

        final var hbarAdjusts = hbarAdjustsWrapper.getAccountAmountsList();

        if (hasRepeatedAccount(hbarAdjusts)) {
            return ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
        }
        if (!isNetZeroAdjustment(hbarAdjusts)) {
            return INVALID_ACCOUNT_AMOUNTS;
        }
        if (!isAcceptableSize(hbarAdjusts, maxHbarAdjusts)) {
            return TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
        }
        // allowance exchanges feature toggle flag
        if (!areAllowanceEnabled && hasAllowanceTransfers(hbarAdjusts)) {
            return NOT_SUPPORTED;
        }

        final var tokenValidity =
                validateTokenTransferSyntax(
                        tokenAdjustsList,
                        maxTokenAdjusts,
                        maxOwnershipChanges,
                        areNftsEnabled,
                        areAllowanceEnabled);
        if (tokenValidity != OK) {
            return tokenValidity;
        }
        return validateTokenTransferSemantics(tokenAdjustsList);
    }

    ResponseCodeEnum validateTokenTransferSyntax(
            List<TokenTransferList> tokenTransfersList,
            int maxListLen,
            int maxOwnershipChanges,
            boolean areNftsEnabled,
            final boolean areAllowanceEnabled) {
        final int numScopedTransfers = tokenTransfersList.size();
        if (numScopedTransfers == 0) {
            return OK;
        }

        return checkTokenTransfersList(
                tokenTransfersList,
                areNftsEnabled,
                maxOwnershipChanges,
                maxListLen,
                areAllowanceEnabled);
    }

    private ResponseCodeEnum checkTokenTransfersList(
            final List<TokenTransferList> tokenTransfersList,
            final boolean areNftsEnabled,
            final int maxOwnershipChanges,
            final int maxListLen,
            final boolean areAllowanceEnabled) {
        var count = 0;
        var numOwnershipChanges = 0;
        for (final var scopedTransfers : tokenTransfersList) {
            final var ownershipChangesHere = scopedTransfers.getNftTransfersCount();
            final var transferCounts = scopedTransfers.getTransfersCount();
            if (ownershipChangesHere > 0) {
                if (!areNftsEnabled) {
                    return NOT_SUPPORTED;
                }
                /* Makes no sense to specify both fungible and non-fungible exchanges for a single token type */
                if (transferCounts > 0) {
                    return INVALID_ACCOUNT_AMOUNTS;
                }
                numOwnershipChanges += ownershipChangesHere;
            } else {
                if (transferCounts == 0) {
                    return EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
                }
                count += transferCounts;
            }
            // allowance exchanges feature toggle flag
            if (!areAllowanceEnabled
                    && (hasAllowanceTransfers(scopedTransfers.getTransfersList())
                            || hasAllowanceNftTransfers(scopedTransfers.getNftTransfersList()))) {
                return NOT_SUPPORTED;
            }
            if (numOwnershipChanges > maxOwnershipChanges) {
                return BATCH_SIZE_LIMIT_EXCEEDED;
            }
            if (count > maxListLen) {
                return TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
            }
        }
        return OK;
    }

    ResponseCodeEnum validateTokenTransferSemantics(List<TokenTransferList> tokenTransfersList) {
        if (tokenTransfersList.isEmpty()) {
            return OK;
        }
        ResponseCodeEnum validity;
        final Set<TokenID> uniqueTokens = new HashSet<>();
        final Set<Long> uniqueSerialNos = new HashSet<>();
        for (var tokenTransfers : tokenTransfersList) {
            validity =
                    validateScopedTransferSemantics(uniqueTokens, tokenTransfers, uniqueSerialNos);
            if (validity != OK) {
                return validity;
            }
        }
        if (uniqueTokens.size() < tokenTransfersList.size()) {
            return TOKEN_ID_REPEATED_IN_TOKEN_LIST;
        }
        return OK;
    }

    private ResponseCodeEnum validateScopedTransferSemantics(
            final Set<TokenID> uniqueTokens,
            final TokenTransferList tokenTransfers,
            final Set<Long> uniqueSerialNos) {
        if (!tokenTransfers.hasToken()) {
            return INVALID_TOKEN_ID;
        }
        uniqueTokens.add(tokenTransfers.getToken());
        final var ownershipChanges = tokenTransfers.getNftTransfersList();
        uniqueSerialNos.clear();
        for (final var ownershipChange : ownershipChanges) {
            if (ownershipChange
                    .getSenderAccountID()
                    .equals(ownershipChange.getReceiverAccountID())) {
                return ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
            }
            if (!uniqueSerialNos.add(ownershipChange.getSerialNumber())) {
                return INVALID_ACCOUNT_AMOUNTS;
            }
        }

        final var adjusts = tokenTransfers.getTransfersList();
        for (var adjust : adjusts) {
            if (!adjust.hasAccountID()) {
                return INVALID_ACCOUNT_ID;
            }
            if (adjust.getAmount() == 0) {
                return INVALID_ACCOUNT_AMOUNTS;
            }
        }
        if (hasRepeatedAccount(adjusts)) {
            return ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
        }
        if (!isNetZeroAdjustment(adjusts)) {
            return TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
        }
        return OK;
    }

    boolean hasRepeatedAccount(List<AccountAmount> adjusts) {
        final int n = adjusts.size();
        if (n < 2) {
            return false;
        }
        Set<AccountID> allowanceAAs = new HashSet<>();
        Set<AccountID> normalAAs = new HashSet<>();
        for (var i = 0; i < n; i++) {
            final var adjust = adjusts.get(i);
            if (adjust.getIsApproval()) {
                if (!allowanceAAs.contains(adjust.getAccountID())) {
                    allowanceAAs.add(adjust.getAccountID());
                } else {
                    return true;
                }
            } else {
                if (!normalAAs.contains(adjust.getAccountID())) {
                    normalAAs.add(adjust.getAccountID());
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    boolean isNetZeroAdjustment(List<AccountAmount> adjusts) {
        var net = ZERO;
        for (var adjust : adjusts) {
            net = net.add(BigInteger.valueOf(adjust.getAmount()));
        }
        return net.equals(ZERO);
    }

    boolean isAcceptableSize(List<AccountAmount> hbarAdjusts, int maxHbarAdjusts) {
        return hbarAdjusts.size() <= maxHbarAdjusts;
    }

    boolean hasAllowanceTransfers(final List<AccountAmount> adjusts) {
        for (var adjust : adjusts) {
            if (adjust.getIsApproval()) return true;
        }
        return false;
    }

    boolean hasAllowanceNftTransfers(final List<NftTransfer> adjusts) {
        for (var adjust : adjusts) {
            if (adjust.getIsApproval()) return true;
        }
        return false;
    }
}
