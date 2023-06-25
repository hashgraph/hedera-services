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

import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import java.util.Collections;
import java.util.Set;

public class ChargeCustomFeeStep implements TransferStep {
    private final CryptoTransferTransactionBody op;

    public ChargeCustomFeeStep(final CryptoTransferTransactionBody op) {
        this.op = op;
    }

    @Override
    public Set<Key> authorizingKeysIn(final TransferContext transferContext) {
        return Set.of();
    }

    @Override
    public void doIn(final TransferContext transferContext) {
        final var handleContext = transferContext.getHandleContext();
        final var tokenTransfers = op.tokenTransfersOrElse(Collections.emptyList());
        final var nftStore = handleContext.writableStore(WritableNftStore.class);
        final var accountStore = handleContext.writableStore(WritableAccountStore.class);
        final var tokenStore = handleContext.writableStore(WritableTokenStore.class);
        final var tokenRelStore = handleContext.writableStore(WritableTokenRelationStore.class);
        final var expiryValidator = handleContext.expiryValidator();

        for (final var xfer : tokenTransfers) {
            final var tokenId = xfer.token();
            final var token = getIfUsable(tokenId, tokenStore);
            final var customFees = token.customFeesOrElse(Collections.emptyList());
            if (customFees.isEmpty()) {
                continue;
            }
            for (final var aa : xfer.transfers()) {

            }
        }
}
