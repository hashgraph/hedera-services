/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.Predicate;

/**
 * Helper class for switching unauthorized debits to approvals in a synthetic
 * {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody}.
 */
public class ApprovalSwitchHelper {
    public static final ApprovalSwitchHelper APPROVAL_SWITCH_HELPER = new ApprovalSwitchHelper();

    private ApprovalSwitchHelper() {
        // Singleton
    }

    /**
     * Given a synthetic {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody}, returns a new
     * {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody} that is identical except any debits
     * whose linked signing keys do not have active signatures are switched to approvals.
     *
     * @param original         the synthetic {@link CryptoTransferTransactionBody} to switch
     * @param signatureTest    the {@link Predicate} that determines whether a given key has an active signature
     * @param nativeOperations the {@link HederaNativeOperations} that provides account key access
     * @return the new {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody}
     */
    public CryptoTransferTransactionBody switchToApprovalsAsNeededIn(
            @NonNull final CryptoTransferTransactionBody original,
            @NonNull final Predicate<Key> signatureTest,
            @NonNull final HederaNativeOperations nativeOperations) {
        requireNonNull(original);
        requireNonNull(signatureTest);
        requireNonNull(nativeOperations);
        final var revision = CryptoTransferTransactionBody.newBuilder();
        if (original.hasTransfers()) {
            final var originalAdjusts = original.transfersOrThrow().accountAmountsOrElse(emptyList());
            revision.transfers(TransferList.newBuilder()
                    .accountAmounts(revisedAdjusts(originalAdjusts, signatureTest, nativeOperations))
                    .build());
        }
        if (original.hasTokenTransfers()) {
            final var originalTokenTransfers = original.tokenTransfersOrThrow();
            final TokenTransferList[] revisedTokenTransfers = new TokenTransferList[originalTokenTransfers.size()];
            for (int i = 0, n = originalTokenTransfers.size(); i < n; i++) {
                final var originalTokenTransfer = originalTokenTransfers.get(i);
                final var revisedTokenTransfer = TokenTransferList.newBuilder().token(originalTokenTransfer.token());
                if (originalTokenTransfer.transfersOrElse(emptyList()).isEmpty()) {
                    revisedTokenTransfer.nftTransfers(revisedOwnershipChanges(
                            originalTokenTransfer.nftTransfersOrThrow(), signatureTest, nativeOperations));
                } else {
                    revisedTokenTransfer.transfers(
                            revisedAdjusts(originalTokenTransfer.transfersOrThrow(), signatureTest, nativeOperations));
                }
                revisedTokenTransfers[i] = revisedTokenTransfer.build();
            }
            revision.tokenTransfers(revisedTokenTransfers);
        }
        return revision.build();
    }

    private AccountAmount[] revisedAdjusts(
            @NonNull final List<AccountAmount> original,
            @NonNull final Predicate<Key> signatureTest,
            @NonNull final HederaNativeOperations nativeOperations) {
        final AccountAmount[] revision = new AccountAmount[original.size()];
        for (int i = 0, n = original.size(); i < n; i++) {
            revision[i] = revisedAdjust(original.get(i), signatureTest, nativeOperations);
        }
        return revision;
    }

    private AccountAmount revisedAdjust(
            @NonNull final AccountAmount original,
            @NonNull final Predicate<Key> signatureTest,
            @NonNull final HederaNativeOperations nativeOperations) {
        if (original.amount() < 0) {
            final var key =
                    nativeOperations.getAccountKey(original.accountIDOrThrow().accountNumOrElse(0L));
            if (key != null && !signatureTest.test(key)) {
                return original.copyBuilder().isApproval(true).build();
            }
        }
        return original;
    }

    private NftTransfer[] revisedOwnershipChanges(
            @NonNull final List<NftTransfer> original,
            @NonNull final Predicate<Key> signatureTest,
            @NonNull final HederaNativeOperations nativeOperations) {
        final NftTransfer[] revision = new NftTransfer[original.size()];
        for (int i = 0, n = original.size(); i < n; i++) {
            revision[i] = revisedNftTransfer(original.get(i), signatureTest, nativeOperations);
        }
        return revision;
    }

    private NftTransfer revisedNftTransfer(
            @NonNull final NftTransfer original,
            @NonNull final Predicate<Key> signatureTest,
            @NonNull final HederaNativeOperations nativeOperations) {
        final var key =
                nativeOperations.getAccountKey(original.senderAccountIDOrThrow().accountNumOrElse(0L));
        if (key != null && !signatureTest.test(key)) {
            return original.copyBuilder().isApproval(true).build();
        }
        return original;
    }
}
