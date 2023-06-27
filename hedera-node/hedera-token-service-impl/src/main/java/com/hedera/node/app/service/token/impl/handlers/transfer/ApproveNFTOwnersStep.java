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

import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import java.util.Collections;

public class ApproveNFTOwnersStep extends BaseTokenHandler implements TransferStep {
    private final CryptoTransferTransactionBody op;

    public ApproveNFTOwnersStep(final CryptoTransferTransactionBody op) {
        this.op = op;
    }

    @Override
    public void doIn(final TransferContext transferContext) {
        final var handleContext = transferContext.getHandleContext();
        final var nftStore = handleContext.writableStore(WritableNftStore.class);
        for (var tokenTransfer : op.tokenTransfersOrElse(Collections.emptyList())) {
            final var tokenId = tokenTransfer.token();
            for (var oc : tokenTransfer.nftTransfersOrElse(Collections.emptyList())) {
                final var serial = oc.serialNumber();
                if (oc.isApproval()) {
                    final var nft = nftStore.get(tokenId, serial);
                    final var copy = nft.copyBuilder();
                    nftStore.put(copy.spenderNumber(0).build());
                }
            }
        }
    }
}
