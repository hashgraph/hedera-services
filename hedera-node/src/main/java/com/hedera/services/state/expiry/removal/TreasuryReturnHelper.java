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

import static com.hedera.services.state.enums.TokenType.FUNGIBLE_COMMON;
import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.utils.NftNumPair.MISSING_NFT_NUM_PAIR;

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.merkle.map.MerkleMap;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TreasuryReturnHelper {
    private final ExpiryThrottle expiryThrottle;
    private final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens;
    private final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels;

    @Inject
    public TreasuryReturnHelper(
            final ExpiryThrottle expiryThrottle,
            final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens,
            final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels) {
        this.tokens = tokens;
        this.tokenRels = tokenRels;
        this.expiryThrottle = expiryThrottle;
    }

    void updateFungibleReturns(
            final EntityNum expiredAccountNum,
            final EntityNum tokenNum,
            final long balance,
            final List<CurrencyAdjustments> returnTransfers) {
        var treasury = MISSING_ENTITY_ID;
        final var curTokens = tokens.get();
        final var token = curTokens.get(tokenNum);
        if (token != null && token.tokenType() == FUNGIBLE_COMMON && !token.isDeleted()) {
            treasury = token.treasury();
        }

        if (treasury == MISSING_ENTITY_ID) {
            final var returnTransfer =
                    new CurrencyAdjustments(
                            new long[] {-balance}, new long[] {expiredAccountNum.longValue()});
            returnTransfers.add(returnTransfer);
            // We didn't actually have to do the treasury balance update
            expiryThrottle.reclaimLastAllowedUse();
        } else {
            final var treasuryNum = treasury.asNum();
            addProperReturn(expiredAccountNum, treasuryNum, balance, returnTransfers);
            incrementBalance(treasuryNum, tokenNum, balance);
        }
    }

    EntityNumPair updateNftReturns(
            final EntityNumPair nftKey,
            final MerkleMap<EntityNumPair, MerkleUniqueToken> currUniqueTokens) {
        final var curTokens = tokens.get();
        final var tokenNum = nftKey.getHiOrderAsNum();
        final var token = curTokens.get(tokenNum);
        final var uniqueToken = currUniqueTokens.getForModify(nftKey);
        if (token != null && token.tokenType() == NON_FUNGIBLE_UNIQUE && uniqueToken != null) {
            if (token.isDeleted()) {
                currUniqueTokens.remove(nftKey);
            } else {
                uniqueToken.setOwner(MISSING_ENTITY_ID);
            }
            final var nextKey = uniqueToken.getNext();
            return nextKey == MISSING_NFT_NUM_PAIR ? null : nextKey.asEntityNumPair();
        }
        return null;
    }

    private void incrementBalance(
            final EntityNum treasuryNum, final EntityNum tokenNum, final long balance) {
        final var curTokenRels = tokenRels.get();
        final var treasuryRelKey = EntityNumPair.fromNums(treasuryNum, tokenNum);
        final var treasuryRel = curTokenRels.getForModify(treasuryRelKey);
        final long newTreasuryBalance = treasuryRel.getBalance() + balance;
        treasuryRel.setBalance(newTreasuryBalance);
    }

    private void addProperReturn(
            final EntityNum expiredAccountNum,
            final EntityNum treasuryNum,
            final long balance,
            final List<CurrencyAdjustments> returnTransfers) {
        final boolean listDebitFirst = expiredAccountNum.compareTo(treasuryNum) < 0;
        // For consistency, order the transfer list by increasing account number
        returnTransfers.add(
                new CurrencyAdjustments(
                        listDebitFirst
                                ? new long[] {-balance, +balance}
                                : new long[] {+balance, -balance},
                        listDebitFirst
                                ? new long[] {
                                    expiredAccountNum.longValue(), treasuryNum.longValue()
                                }
                                : new long[] {
                                    treasuryNum.longValue(), expiredAccountNum.longValue()
                                }));
    }
}
