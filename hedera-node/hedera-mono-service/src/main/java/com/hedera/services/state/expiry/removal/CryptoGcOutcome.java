/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.expiry.removal;

import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.NftAdjustments;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the result of doing some amount of "garbage collection" for an expired account (or
 * contract). The result includes three pieces of information:
 *
 * <ol>
 *   <li>Fungible token units that were owned by the expired account and are now either burned or
 *       returned to their treasury.
 *   <li>NFTs that were owned by the expired account and are now either burned or returned to their
 *       treasury.
 *   <li>Whether garbage collection for the expired account is finished.
 * </ol>
 *
 * <p><b>IMPORTANT:</b> {@link ExpirableTxnRecord} uses an suboptimal representation for token
 * transfers, based on a list of token ids plus <i>two</i> parallel lists; one for (possibly null)
 * {@link CurrencyAdjustments} objects, and another for (possibly null) {@link NftAdjustments}
 * objects. (If the token id at index {@code i} is fungible, then the {@code CurrencyAdjustments} at
 * index {@code i} in the first list will be non-null; and null otherwise, with a non-null {@code
 * NftAdjustments} at index {@code i} in the second list).
 *
 * <p>To make it easier for clients to store the returns information in a {@code
 * ExpirableTxnRecord}, we provide helper methods to generate the three parallel lists, with all
 * fungible token ids and adjustments coming first; and all non-fungible token ids following.
 *
 * @param fungibleTreasuryReturns fungible units returned or burned
 * @param nonFungibleTreasuryReturns NFTs returned or burned
 * @param finished if all garbage collection is done for the expired account
 */
public record CryptoGcOutcome(
        FungibleTreasuryReturns fungibleTreasuryReturns,
        NonFungibleTreasuryReturns nonFungibleTreasuryReturns,
        boolean finished) {

    public boolean needsExternalizing() {
        return finished
                || fungibleTreasuryReturns().numReturns() != 0
                || nonFungibleTreasuryReturns.numReturns() != 0;
    }

    public List<EntityId> allReturnedTokens() {
        List<EntityId> ans = Collections.emptyList();
        if (fungibleTreasuryReturns.numReturns() != 0) {
            ans = fungibleTreasuryReturns.tokenTypes();
        }
        if (nonFungibleTreasuryReturns.numReturns() != 0) {
            ans = new ArrayList<>(ans);
            ans.addAll(nonFungibleTreasuryReturns.tokenTypes());
        }
        return ans;
    }

    public List<CurrencyAdjustments> parallelAdjustments() {
        if (nonFungibleTreasuryReturns.numReturns() == 0) {
            return fungibleTreasuryReturns.transfers();
        } else {
            final List<CurrencyAdjustments> ans =
                    new ArrayList<>(fungibleTreasuryReturns.transfers());
            for (int i = 0, n = nonFungibleTreasuryReturns.numReturns(); i < n; i++) {
                ans.add(null);
            }
            return ans;
        }
    }

    public List<NftAdjustments> parallelExchanges() {
        if (fungibleTreasuryReturns.numReturns() == 0) {
            return nonFungibleTreasuryReturns.exchanges();
        } else {
            final List<NftAdjustments> ans =
                    new ArrayList<>(nonFungibleTreasuryReturns.exchanges());
            for (int i = 0, n = fungibleTreasuryReturns.numReturns(); i < n; i++) {
                ans.add(0, null);
            }
            return ans;
        }
    }
}
