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

import static com.hedera.test.utils.TxnUtils.asymmetricTtlOf;
import static com.hedera.test.utils.TxnUtils.ttlOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.swirlds.merkle.map.MerkleMap;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryReturnHelperTest {
    @Mock private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels;

    private final List<CurrencyAdjustments> returnTransfers = new ArrayList<>();

    private TreasuryReturnHelper subject;

    @BeforeEach
    void setUp() {
        subject = new TreasuryReturnHelper(() -> tokenRels);
    }

    @Test
    void justReportsDebitIfTokenIsDeleted() {
        subject.updateFungibleReturns(
                expiredAccountNum, deletedTokenNum, deletedToken, tokenBalance, returnTransfers);

        final var ttls =
                List.of(
                        asymmetricTtlOf(
                                deletedTokenNum.toGrpcTokenId(),
                                expiredAccountNum.toGrpcAccountId(),
                                tokenBalance));
        assertEquals(adjustmentsFrom(ttls), returnTransfers);
    }

    @Test
    void doesTreasuryReturnForNonzeroFungibleBalance() {
        final var treasuryRel = mutableRel(tokenBalance);
        givenModifiableRelPresent(treasuryNum, fungibleTokenNum, treasuryRel);

        subject.updateFungibleReturns(
                expiredAccountNum, fungibleTokenNum, fungibleToken, tokenBalance, returnTransfers);

        final var ttls =
                List.of(
                        ttlOf(
                                survivedTokenGrpcId,
                                expiredAccountNum.toGrpcAccountId(),
                                treasuryId.toGrpcAccountId(),
                                tokenBalance));
        assertEquals(adjustmentsFrom(ttls), returnTransfers);
        assertEquals(2 * tokenBalance, treasuryRel.getBalance());
    }

    @Test
    void ordersTreasuryReturnsByAccountNumber() {
        final var treasuryRel = mutableRel(tokenBalance);
        givenModifiableRelPresent(treasuryNum, fungibleTokenNum, treasuryRel);

        subject.updateFungibleReturns(
                olderExpiredAccountNum,
                fungibleTokenNum,
                fungibleToken,
                tokenBalance,
                returnTransfers);

        final var ttls =
                List.of(
                        ttlOf(
                                survivedTokenGrpcId,
                                treasuryId.toGrpcAccountId(),
                                olderExpiredAccountNum.toGrpcAccountId(),
                                -tokenBalance));
        assertEquals(adjustmentsFrom(ttls), returnTransfers);
        assertEquals(2 * tokenBalance, treasuryRel.getBalance());
    }

    private void givenTokenPresent(EntityNum id, MerkleToken token) {}

    private void givenModifiableRelPresent(
            EntityNum account, EntityNum token, MerkleTokenRelStatus mutableRel) {
        var rel = EntityNumPair.fromLongs(account.longValue(), token.longValue());
        given(tokenRels.getForModify(rel)).willReturn(mutableRel);
    }

    private MerkleTokenRelStatus mutableRel(long balance) {
        return new MerkleTokenRelStatus(balance, false, false, true);
    }

    static List<CurrencyAdjustments> adjustmentsFrom(final List<TokenTransferList> ttls) {
        return ttls.stream()
                .map(
                        ttl ->
                                new CurrencyAdjustments(
                                        ttl.getTransfersList().stream()
                                                .mapToLong(AccountAmount::getAmount)
                                                .toArray(),
                                        ttl.getTransfersList().stream()
                                                .map(AccountAmount::getAccountID)
                                                .mapToLong(AccountID::getAccountNum)
                                                .toArray()))
                .collect(Collectors.toList());
    }

    private final long tokenBalance = 1_234L;
    private final EntityId expiredTreasuryId = new EntityId(0, 0, 2L);
    private final EntityNum treasuryNum = EntityNum.fromLong(666L);
    private final EntityNum expiredAccountNum = expiredTreasuryId.asNum();
    private final EntityNum olderExpiredAccountNum = EntityNum.fromLong(1_000_000L);
    private final EntityNum deletedTokenNum = EntityNum.fromLong(1234L);
    private final EntityNum fungibleTokenNum = EntityNum.fromLong(4321L);
    private final EntityNum nonFungibleTokenNum = EntityNum.fromLong(9999L);
    private final EntityNum missingTokenNum = EntityNum.fromLong(5678L);
    private final EntityId treasuryId = treasuryNum.toEntityId();
    private final TokenID survivedTokenGrpcId = fungibleTokenNum.toGrpcTokenId();
    private final MerkleToken deletedToken =
            new MerkleToken(
                    Long.MAX_VALUE,
                    1L,
                    0,
                    "GONE",
                    "Long lost dream",
                    true,
                    true,
                    expiredTreasuryId);
    private final MerkleToken fungibleToken =
            new MerkleToken(
                    Long.MAX_VALUE, 1L, 0, "HERE", "Dreams never die", true, true, treasuryId);
    private final MerkleToken nonFungibleToken =
            new MerkleToken(
                    Long.MAX_VALUE, 1L, 0, "HERE", "Dreams never die", true, true, treasuryId);

    {
        deletedToken.setDeleted(true);
        fungibleToken.setTokenType(TokenType.FUNGIBLE_COMMON);
        nonFungibleToken.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);
    }
}
