/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.util;

import static com.hedera.hapi.node.base.HederaFunctionality.NONE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static java.util.Collections.emptySet;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class HandleUtils {
    public static TransactionBaseData extractTransactionBaseData(@NonNull final Bytes content) {
        // This method is only called if something fatal happened. We do a best effort approach to extract the
        // type of the transaction, the TransactionBody and the payer if not known.
        if (content.length() == 0) {
            return new TransactionBaseData(NONE, Bytes.EMPTY, null, null, null);
        }

        HederaFunctionality function = NONE;
        Bytes transactionBytes = content;
        Transaction transaction = null;
        TransactionBody txBody = null;
        AccountID payer = null;
        try {
            transaction = Transaction.PROTOBUF.parseStrict(transactionBytes);

            final Bytes bodyBytes;
            if (transaction.signedTransactionBytes().length() > 0) {
                final var signedTransaction = SignedTransaction.PROTOBUF.parseStrict(
                        transaction.signedTransactionBytes().toReadableSequentialData());
                bodyBytes = signedTransaction.bodyBytes();
                transactionBytes = bodyBytes;
            } else {
                bodyBytes = transaction.bodyBytes();
            }
            txBody = TransactionBody.PROTOBUF.parseStrict(bodyBytes.toReadableSequentialData());

            payer = txBody.transactionIDOrElse(TransactionID.DEFAULT).accountID();

            function = HapiUtils.functionOf(txBody);
        } catch (Exception ex) {
            // ignore
        }
        return new TransactionBaseData(function, transactionBytes, transaction, txBody, payer);
    }

    /**
     * Returns a set of "extra" account ids that should be considered as eligible for
     * collecting their accrued staking rewards with the given transaction info and
     * record builder.
     *
     * <p><b>IMPORTANT:</b> Needed only for mono-service fidelity.
     *
     * <p>There are three cases, none of which HIP-406 defined as a reward situation;
     * but were "false positives" in the original mono-service implementation:
     * <ol>
     *     <li>For a crypto transfer, any account explicitly listed in the HBAR
     *     transfer list, even with a zero balance adjustment.</li>
     *     <li>For a contract operation, any called contract.</li>
     *     <li>For a contract operation, any account loaded in a child
     *     transaction (primarily, any account involved in a child
     *     token transfer).</li>
     * </ol>
     *
     * @param body the {@link TransactionBody} of the transaction
     * @param function the {@link HederaFunctionality} of the transaction
     * @param recordBuilder the record builder
     * @return the set of extra account ids
     */
    public static Set<AccountID> extraRewardReceivers(
            @Nullable final TransactionBody body,
            @NonNull final HederaFunctionality function,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder) {
        if (recordBuilder.status() != SUCCESS || body == null) {
            return emptySet();
        }
        return switch (function) {
            case CRYPTO_TRANSFER -> zeroAdjustIdsFrom(body.cryptoTransferOrThrow()
                    .transfersOrElse(TransferList.DEFAULT)
                    .accountAmounts());
            case ETHEREUM_TRANSACTION, CONTRACT_CALL, CONTRACT_CREATE -> recordBuilder.explicitRewardSituationIds();
            default -> emptySet();
        };
    }

    /**
     * Returns any ids from the given list of explicit hbar adjustments that have a zero amount.
     *
     * @param explicitHbarAdjustments the list of explicit hbar adjustments
     * @return the set of account ids that have a zero amount
     */
    private static @NonNull Set<AccountID> zeroAdjustIdsFrom(
            @NonNull final List<AccountAmount> explicitHbarAdjustments) {
        Set<AccountID> zeroAdjustmentAccounts = null;
        for (final var aa : explicitHbarAdjustments) {
            if (aa.amount() == 0) {
                if (zeroAdjustmentAccounts == null) {
                    zeroAdjustmentAccounts = new LinkedHashSet<>();
                }
                zeroAdjustmentAccounts.add(aa.accountID());
            }
        }
        return zeroAdjustmentAccounts == null ? emptySet() : zeroAdjustmentAccounts;
    }
}
