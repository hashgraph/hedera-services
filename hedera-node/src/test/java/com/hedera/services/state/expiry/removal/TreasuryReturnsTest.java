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

import static com.hedera.services.state.expiry.removal.TreasuryReturns.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.TokenRelsListMutation;
import com.hedera.services.state.expiry.classification.EntityLookup;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.swirlds.merkle.map.MerkleMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryReturnsTest {
    @Mock private ExpiryThrottle expiryThrottle;
    @Mock private TreasuryReturnHelper returnHelper;

    @Mock private EntityLookup entityLookup;
    @Mock private TreasuryReturns.RemovalFacilitation removalFacilitation;
    @Mock private MerkleMap<EntityNum, MerkleToken> tokens;
    @Mock private MerkleMap<EntityNumPair, MerkleUniqueToken> nfts;
    @Mock private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels;

    private TreasuryReturns subject;

    @BeforeEach
    void setUp() {
        subject =
                new TreasuryReturns(
                        entityLookup,
                        () -> tokens,
                        () -> nfts,
                        () -> tokenRels,
                        expiryThrottle,
                        returnHelper);
    }

    @Test
    void finishedIfNoNfts() {
        final var expected = NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS;

        final var actual = subject.returnNftsFrom(new MerkleAccount());

        assertEquals(expected, actual);
    }

    @Test
    void finishedIfNoRels() {
        final var expected = FungibleTreasuryReturns.FINISHED_NOOP_FUNGIBLE_RETURNS;

        final var actual = subject.returnFungibleUnitsFrom(new MerkleAccount());

        assertEquals(expected, actual);
    }

    @Test
    void doesNothingIfCannotReturnUnits() {
        final var expected = FungibleTreasuryReturns.UNFINISHED_NOOP_FUNGIBLE_RETURNS;

        final var actual = subject.returnFungibleUnitsFrom(accountWithRels);

        assertEquals(expected, actual);
    }

    @Test
    void returnsAllGivenCapacity() {
        givenStandardSetup(true, true);
        given(expiryThrottle.allow(any())).willReturn(true);
        given(entityLookup.getMutableAccount(num)).willReturn(accountWithRels);

        final var expected = new FungibleTreasuryReturns(List.of(bTokenId), List.of(), true);

        final var actual = subject.returnFungibleUnitsFrom(accountWithRels);

        assertEquals(expected, actual);
        verify(returnHelper)
                .updateFungibleReturns(
                        eq(num), eq(bTokenId.asNum()), eq(fungibleToken), eq(1L), any(List.class));
        assertEquals(0, accountWithRels.getNumAssociations());
    }

    @Test
    void doesntIncludeMissingTypes() {
        givenStandardSetup(false, false);
        given(tokens.get(bRelKey.getLowOrderAsNum())).willReturn(null);
        given(expiryThrottle.allow(any())).willReturn(true);
        given(entityLookup.getMutableAccount(num)).willReturn(accountWithRels);

        final var expected = FungibleTreasuryReturns.FINISHED_NOOP_FUNGIBLE_RETURNS;

        final var actual = subject.returnFungibleUnitsFrom(accountWithRels);

        assertEquals(expected, actual);
        verifyNoInteractions(returnHelper);
        assertEquals(0, accountWithRels.getNumAssociations());
    }

    @Test
    void doesntIncludeNonFungibleTypes() {
        givenStandardSetup(false, false);
        given(tokens.get(bRelKey.getLowOrderAsNum())).willReturn(nfToken);
        given(expiryThrottle.allow(any())).willReturn(true);
        given(entityLookup.getMutableAccount(num)).willReturn(accountWithRels);

        final var expected = FungibleTreasuryReturns.FINISHED_NOOP_FUNGIBLE_RETURNS;

        final var actual = subject.returnFungibleUnitsFrom(accountWithRels);

        assertEquals(expected, actual);
        verifyNoInteractions(returnHelper);
        assertEquals(0, accountWithRels.getNumAssociations());
    }

    @Test
    void abortsWithoutCapacityForBalanceIncrement() {
        givenStandardSetup(true, false);
        given(expiryThrottle.allow(ROOT_REL_UPDATE_WORK)).willReturn(true);
        given(expiryThrottle.allow(TREASURY_BALANCE_INCREMENT)).willReturn(false);
        given(expiryThrottle.allow(TOKEN_TYPE_CHECK)).willReturn(true);
        given(expiryThrottle.allow(NEXT_REL_REMOVAL_WORK)).willReturn(true);
        given(expiryThrottle.allow(ONLY_REL_REMOVAL_WORK)).willReturn(true);
        given(entityLookup.getMutableAccount(num)).willReturn(accountWithRels);

        final var expected = new FungibleTreasuryReturns(List.of(), List.of(), false);

        final var actual = subject.returnFungibleUnitsFrom(accountWithRels);

        assertEquals(expected, actual);
        verifyNoInteractions(returnHelper);
        assertEquals(1, accountWithRels.getNumAssociations());
    }

    @Test
    void abortsWithoutCapacityForTypeCheck() {
        givenStandardSetup(false, false);
        given(expiryThrottle.allow(ROOT_REL_UPDATE_WORK)).willReturn(true);
        given(expiryThrottle.allow(TOKEN_TYPE_CHECK)).willReturn(true).willReturn(false);
        given(expiryThrottle.allow(NEXT_REL_REMOVAL_WORK)).willReturn(true);
        given(expiryThrottle.allow(ONLY_REL_REMOVAL_WORK)).willReturn(true);
        given(entityLookup.getMutableAccount(num)).willReturn(accountWithRels);

        final var expected = new FungibleTreasuryReturns(List.of(), List.of(), false);

        final var actual = subject.returnFungibleUnitsFrom(accountWithRels);

        assertEquals(expected, actual);
        verifyNoInteractions(returnHelper);
        assertEquals(1, accountWithRels.getNumAssociations());
    }

    @Test
    void returnsOnlyFirstWithLimitedCapacity() {
        givenStandardSetup(false, false);
        given(expiryThrottle.allow(TOKEN_TYPE_CHECK)).willReturn(true);
        given(expiryThrottle.allow(ROOT_REL_UPDATE_WORK)).willReturn(true);
        given(expiryThrottle.allow(NEXT_REL_REMOVAL_WORK)).willReturn(true);
        given(expiryThrottle.allow(ONLY_REL_REMOVAL_WORK)).willReturn(false);
        given(entityLookup.getMutableAccount(num)).willReturn(accountWithRels);

        final var expected = new FungibleTreasuryReturns(List.of(), List.of(), false);

        final var actual = subject.returnFungibleUnitsFrom(accountWithRels);

        assertEquals(expected, actual);
        verifyNoInteractions(returnHelper);
        assertEquals(1, accountWithRels.getNumAssociations());
        assertEquals(bRelKey, accountWithRels.getLatestAssociation());
    }

    private void givenStandardSetup(final boolean includeB, final boolean includeBRemoval) {
        subject.setRelRemovalFacilitation(removalFacilitation);
        given(tokens.get(aRelKey.getLowOrderAsNum())).willReturn(fungibleToken);
        given(tokenRels.get(aRelKey)).willReturn(aRelStatus);
        if (includeB) {
            given(tokenRels.get(bRelKey)).willReturn(bRelStatus);
            given(tokens.get(bRelKey.getLowOrderAsNum())).willReturn(fungibleToken);
        }

        given(
                        removalFacilitation.removeNext(
                                eq(aRelKey), eq(aRelKey), any(TokenRelsListMutation.class)))
                .willReturn(bRelKey);
        if (includeBRemoval) {
            given(
                            removalFacilitation.removeNext(
                                    eq(bRelKey), eq(bRelKey), any(TokenRelsListMutation.class)))
                    .willReturn(null);
        }
    }

    private final EntityNum num = EntityNum.fromLong(123L);
    private final EntityId aTokenId = EntityId.fromNum(666L);
    private final EntityId bTokenId = EntityId.fromNum(777L);
    private final EntityNumPair aRelKey = EntityNumPair.fromNums(num, aTokenId.asNum());
    private final EntityNumPair bRelKey = EntityNumPair.fromNums(num, bTokenId.asNum());
    private final MerkleTokenRelStatus aRelStatus =
            new MerkleTokenRelStatus(0L, false, false, true);
    private final MerkleTokenRelStatus bRelStatus =
            new MerkleTokenRelStatus(1L, false, false, true);

    private final MerkleAccount accountWithRels =
            MerkleAccountFactory.newAccount()
                    .lastAssociatedToken(aTokenId.num())
                    .associatedTokensCount(2)
                    .get();

    {
        accountWithRels.setKey(num);
    }

    private final MerkleToken fungibleToken = new MerkleToken();

    {
        fungibleToken.setTokenType(TokenType.FUNGIBLE_COMMON);
    }

    private final MerkleToken nfToken = new MerkleToken();

    {
        nfToken.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);
    }
}
