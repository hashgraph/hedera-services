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

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Defines a type that can test each hbar, token, and NFT transfer in a
 * {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody} for some condition.
 */
public interface TransferAuditTest {
    /**
     * Returns {@code true} if the given hbar or fungible token {@link AccountAmount}
     * adjustment raises the audit flag.
     *
     * @param adjust the {@link AccountAmount} to test
     * @return whether the audit flag is raised
     */
    boolean flagsAdjustment(@NonNull AccountAmount adjust);

    /**
     * Returns {@code true} if the given {@link NftTransfer} raises the audit flag.
     *
     * @param nftTransfer the {@link NftTransfer} to test
     * @return whether the audit flag is raised
     */
    boolean flagsNftTransfer(@NonNull NftTransfer nftTransfer);

    /**
     * Returns {@code true} if the given {@link CryptoTransferTransactionBody} raises the audit flag.
     *
     * @param op the {@link CryptoTransferTransactionBody} to test
     * @param auditTest the {@link TransferAuditTest} to use
     * @return whether the audit flag is raised
     */
    static boolean isAuditFlagRaised(@NonNull CryptoTransferTransactionBody op, @NonNull TransferAuditTest auditTest) {
        final var hbarAdjusts = op.transfersOrElse(TransferList.DEFAULT).accountAmounts();
        for (final var adjust : hbarAdjusts) {
            if (auditTest.flagsAdjustment(adjust)) {
                return true;
            }
        }
        final var tokenTransferLists = op.tokenTransfers();
        for (final var tokenTransferList : tokenTransferLists) {
            final var tokenAdjusts = tokenTransferList.transfers();
            for (final var adjust : tokenAdjusts) {
                if (auditTest.flagsAdjustment(adjust)) {
                    return true;
                }
            }
            final var nftTransfers = tokenTransferList.nftTransfers();
            for (final var nftTransfer : nftTransfers) {
                if (auditTest.flagsNftTransfer(nftTransfer)) {
                    return true;
                }
            }
        }
        return false;
    }
}
