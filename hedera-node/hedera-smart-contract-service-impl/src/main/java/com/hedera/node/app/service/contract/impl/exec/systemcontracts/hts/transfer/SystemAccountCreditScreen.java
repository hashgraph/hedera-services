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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import static com.hedera.node.app.service.contract.impl.exec.processors.ProcessorModule.NUM_SYSTEM_ACCOUNTS;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Small helper to screen whether a {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody} tries
 * to credit a system account. (These accounts are supposed to be effectively invisible inside the EVM.)
 */
public class SystemAccountCreditScreen {
    public static final SystemAccountCreditScreen SYSTEM_ACCOUNT_CREDIT_SCREEN = new SystemAccountCreditScreen();

    private SystemAccountCreditScreen() {
        // Singleton
    }

    /**
     * Returns {@code true} if the given {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody} tries
     * to credit a system account.
     *
     * @param cryptoTranfers the {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody} to screen
     * @return whether it credits a system account
     */
    public boolean creditsToSystemAccount(@NonNull final CryptoTransferTransactionBody cryptoTranfers) {
        final var hbarAdjusts =
                cryptoTranfers.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList());
        for (final var adjust : hbarAdjusts) {
            if (creditsSystemAccount(adjust)) {
                return true;
            }
        }
        final var tokenTransferLists = cryptoTranfers.tokenTransfersOrElse(emptyList());
        for (final var tokenTransferList : tokenTransferLists) {
            final var tokenAdjusts = tokenTransferList.transfersOrElse(emptyList());
            for (final var adjust : tokenAdjusts) {
                if (creditsSystemAccount(adjust)) {
                    return true;
                }
            }
            final var nftTransfers = tokenTransferList.nftTransfersOrElse(emptyList());
            for (final var nftTransfer : nftTransfers) {
                if (isSystemAccountNumber(nftTransfer.receiverAccountIDOrThrow().accountNumOrElse(0L))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean creditsSystemAccount(@NonNull final AccountAmount adjust) {
        return adjust.amount() > 0
                && isSystemAccountNumber(adjust.accountIDOrThrow().accountNumOrElse(0L));
    }

    private boolean isSystemAccountNumber(final long number) {
        return number > 0 && number <= NUM_SYSTEM_ACCOUNTS;
    }
}
