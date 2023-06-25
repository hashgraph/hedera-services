/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.token.impl.handlers.transfer;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.node.app.service.mono.ledger.BalanceChange;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hedera.node.app.service.mono.ledger.BalanceChange.changingFtUnits;
import static com.hedera.node.app.service.mono.ledger.BalanceChange.changingNftOwnership;
import static java.util.Collections.emptyList;

public class ZeroSumFungibleTransfersStep implements TransferStep{
    final CryptoTransferTransactionBody op;
    public ZeroSumFungibleTransfersStep(final CryptoTransferTransactionBody op) {
        this.op = op;
    }
    @Override
    public Set<Key> authorizingKeysIn(final TransferContext transferContext) {
        return null;
    }

    @Override
    public void doIn(final TransferContext transferContext) {
        final var Map<EntityNumPair, Long> aggregatedFungibleTokenChanges = new LinkedHashMap<>();
        for (var xfers : op.tokenTransfers()) {
            final var tokenId = xfers.token();
            boolean decimalsSet = false;
            for (var aa : xfers.transfersOrElse(emptyList())) {
                var currChange = changingFtUnits(tokenId, grpcTokenId, aa, payerID);
                // set only for the first balance change of the token with expectedDecimals
                if (xfers.hasExpectedDecimals() && !decimalsSet) {
                    currChange.setExpectedDecimals(xfers.getExpectedDecimals().getValue());
                    decimalsSet = true;
                }

                var tokenAndAccountId =
                        new ImpliedTransfersMarshal.TokenAndAccountID(EntityNum.fromLong(tokenId.num()), currChange.accountId());

                if (!aggregatedFungibleTokenChanges.containsKey(tokenAndAccountId)) {
                    aggregatedFungibleTokenChanges.put(tokenAndAccountId, currChange);
                } else {
                    var existingChange = aggregatedFungibleTokenChanges.get(tokenAndAccountId);
                    existingChange.aggregateUnits(currChange.getAggregatedUnits());
                    existingChange.addAllowanceUnits(currChange.getAllowanceUnits());
                }
            }
            changes.addAll(aggregatedFungibleTokenChanges.values());

            for (var oc : xfers.getNftTransfersList()) {
                if (ownershipChanges == null) {
                    ownershipChanges = new ArrayList<>();
                }
                ownershipChanges.add(changingNftOwnership(tokenId, grpcTokenId, oc, payerID));
            }
        }
        if (ownershipChanges != null) {
            changes.addAll(ownershipChanges);
        }
    }
}
