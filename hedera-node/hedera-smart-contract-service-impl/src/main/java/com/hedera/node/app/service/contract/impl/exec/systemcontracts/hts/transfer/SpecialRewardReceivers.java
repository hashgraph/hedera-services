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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.recordBuilderFor;

import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Provides logic to detect account ids that need to be treated as in special
 * reward situations for mono-service fidelity.
 */
public class SpecialRewardReceivers {
    public static final SpecialRewardReceivers SPECIAL_REWARD_RECEIVERS = new SpecialRewardReceivers();

    /**
     * Adds any special reward receivers to the given frame for the given {@link CryptoTransferTransactionBody}.
     *
     * @param frame the frame to add to
     * @param body the body to inspect
     */
    public void addInFrame(
            @NonNull final MessageFrame frame,
            @NonNull final CryptoTransferTransactionBody body,
            @NonNull final List<AssessedCustomFee> assessedCustomFees) {
        final var recordBuilder = recordBuilderFor(frame);
        body.transfersOrElse(TransferList.DEFAULT)
                .accountAmounts()
                .forEach(adjustment -> recordBuilder.trackExplicitRewardSituation(adjustment.accountIDOrThrow()));
        body.tokenTransfers().forEach(transfers -> {
            transfers
                    .transfers()
                    .forEach(adjustment -> recordBuilder.trackExplicitRewardSituation(adjustment.accountIDOrThrow()));
            transfers.nftTransfers().forEach(transfer -> {
                recordBuilder.trackExplicitRewardSituation(transfer.senderAccountIDOrThrow());
                recordBuilder.trackExplicitRewardSituation(transfer.receiverAccountIDOrThrow());
            });
        });
        assessedCustomFees.forEach(
                fee -> recordBuilder.trackExplicitRewardSituation(fee.feeCollectorAccountIdOrThrow()));
    }
}
