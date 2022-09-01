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
import static org.mockito.Mockito.*;

import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.TokenRelsListMutation;
import com.hedera.services.state.expiry.classification.EntityLookup;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.NftNumPair;
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
    @Mock private RelRemovalFacilitation relRemovalFacilitation;
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
    void doesNothingIfCannotReturnNfts() {
        final var expected = NonFungibleTreasuryReturns.UNFINISHED_NOOP_NON_FUNGIBLE_RETURNS;

        final var actual = subject.returnNftsFrom(accountWithNfts);

        assertEquals(expected, actual);
    }

    @Test
    void returnsAllUnitsGivenCapacity() {
        givenStandardUnitsSetup(true, true);
        given(expiryThrottle.allow(any())).willReturn(true);
        given(entityLookup.getMutableAccount(num)).willReturn(accountWithRels);

        final var expected = new FungibleTreasuryReturns(List.of(bTokenId), List.of(), true);

        final var actual = subject.returnFungibleUnitsFrom(accountWithRels);

        assertEquals(expected, actual);
        verify(returnHelper)
                .updateFungibleReturns(
                        eq(num),
                        eq(bTokenId.asNum()),
                        eq(fungibleToken),
                        eq(1L),
                        any(List.class),
                        eq(tokenRels));
        assertEquals(0, accountWithRels.getNumAssociations());
    }

    @Test
    void returnsAllNftsGivenCapacity() {
        givenStandardNftsSetup(true, true);
        given(expiryThrottle.allow(any())).willReturn(true);
        given(entityLookup.getMutableAccount(num)).willReturn(accountWithNfts);

        final var expected = NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS;

        final var actual = subject.returnNftsFrom(accountWithNfts);

        assertEquals(expected, actual);
        assertEquals(0, accountWithNfts.getNftsOwned());
    }

    @Test
    void returnsOnlyNftsGivenTokenCheckCapacity() {
        givenStandardNftsSetup(false, false);
        given(expiryThrottle.allow(ROOT_META_UPDATE_WORK)).willReturn(true);
        given(expiryThrottle.allow(TOKEN_DELETION_CHECK)).willReturn(true).willReturn(false);
        given(expiryThrottle.allow(NFT_RETURN_WORK)).willReturn(true);
        given(entityLookup.getMutableAccount(num)).willReturn(accountWithNfts);

        final var expected = NonFungibleTreasuryReturns.UNFINISHED_NOOP_NON_FUNGIBLE_RETURNS;

        final var actual = subject.returnNftsFrom(accountWithNfts);

        assertEquals(expected, actual);
        assertEquals(1, accountWithNfts.getNftsOwned());
        assertEquals(bNftKey, accountWithNfts.getHeadNftKey());
    }

    @Test
    void returnsOnlyNftsGivenWorkCapacity() {
        givenStandardNftsSetup(false, false);
        given(tokens.get(bNftKey.getHiOrderAsNum())).willReturn(deletedNfToken);
        given(expiryThrottle.allow(ROOT_META_UPDATE_WORK)).willReturn(true);
        given(expiryThrottle.allow(TOKEN_DELETION_CHECK)).willReturn(true);
        given(expiryThrottle.allow(NFT_RETURN_WORK)).willReturn(true);
        given(entityLookup.getMutableAccount(num)).willReturn(accountWithNfts);

        final var expected = NonFungibleTreasuryReturns.UNFINISHED_NOOP_NON_FUNGIBLE_RETURNS;

        final var actual = subject.returnNftsFrom(accountWithNfts);

        assertEquals(expected, actual);
        assertEquals(1, accountWithNfts.getNftsOwned());
        assertEquals(bNftKey, accountWithNfts.getHeadNftKey());
    }

    @Test
    void worksAroundDisappearedToken() {
        givenStandardNftsSetup(true, true);
        given(tokens.get(bNftKey.getHiOrderAsNum())).willReturn(null);
        given(expiryThrottle.allow(any())).willReturn(true);
        given(entityLookup.getMutableAccount(num)).willReturn(accountWithNfts);

        final var expected = NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS;

        final var actual = subject.returnNftsFrom(accountWithNfts);

        assertEquals(expected, actual);
        assertEquals(0, accountWithNfts.getNftsOwned());
    }

    @Test
    void returnsAllGivenCapacityEvenIfDeleted() {
        givenStandardUnitsSetup(true, true);
        given(tokens.get(bRelKey.getLowOrderAsNum())).willReturn(deletedFungibleToken);
        given(expiryThrottle.allow(any())).willReturn(true);
        given(entityLookup.getMutableAccount(num)).willReturn(accountWithRels);

        final var expected = new FungibleTreasuryReturns(List.of(bTokenId), List.of(), true);

        final var actual = subject.returnFungibleUnitsFrom(accountWithRels);

        assertEquals(expected, actual);
        verify(returnHelper)
                .updateFungibleReturns(
                        eq(num),
                        eq(bTokenId.asNum()),
                        eq(deletedFungibleToken),
                        eq(1L),
                        any(List.class),
                        eq(tokenRels));
        verify(expiryThrottle, never()).allow(TREASURY_BALANCE_INCREMENT);
        assertEquals(0, accountWithRels.getNumAssociations());
    }

    @Test
    void doesntIncludeMissingTypes() {
        givenStandardUnitsSetup(false, false);
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
        givenStandardUnitsSetup(false, false);
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
        givenStandardUnitsSetup(true, false);
        given(expiryThrottle.allow(ROOT_META_UPDATE_WORK)).willReturn(true);
        given(expiryThrottle.allow(TREASURY_BALANCE_INCREMENT)).willReturn(false);
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
        givenStandardUnitsSetup(false, false);
        given(expiryThrottle.allow(ROOT_META_UPDATE_WORK)).willReturn(true);
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

    private void givenStandardUnitsSetup(final boolean includeB, final boolean includeBRemoval) {
        subject.setRelRemovalFacilitation(relRemovalFacilitation);
        given(tokens.get(aRelKey.getLowOrderAsNum())).willReturn(fungibleToken);
        given(tokenRels.get(aRelKey)).willReturn(aRelStatus);
        if (includeB) {
            given(tokenRels.get(bRelKey)).willReturn(bRelStatus);
            given(tokens.get(bRelKey.getLowOrderAsNum())).willReturn(fungibleToken);
        }

        given(
                        relRemovalFacilitation.removeNext(
                                eq(aRelKey), eq(aRelKey), any(TokenRelsListMutation.class)))
                .willReturn(bRelKey);
        if (includeBRemoval) {
            given(
                            relRemovalFacilitation.removeNext(
                                    eq(bRelKey), eq(bRelKey), any(TokenRelsListMutation.class)))
                    .willReturn(null);
        }
    }

    private void givenStandardNftsSetup(final boolean includeB, final boolean includeBRemoval) {
        given(tokens.get(aNftKey.getHiOrderAsNum())).willReturn(nfToken);
        if (includeB) {
            given(tokens.get(bNftKey.getHiOrderAsNum())).willReturn(deletedNfToken);
        }

        given(
                        returnHelper.updateNftReturns(
                                eq(num),
                                eq(aNftKey.getHiOrderAsNum()),
                                eq(nfToken),
                                eq(aNftKey.getLowOrderAsLong()),
                                any(List.class),
                                any(List.class)))
                .willReturn(true);
        given(returnHelper.finishNft(false, aNftKey, nfts)).willReturn(bNftKey);
        if (includeBRemoval) {
            given(
                            returnHelper.updateNftReturns(
                                    eq(num),
                                    eq(bNftKey.getHiOrderAsNum()),
                                    any(),
                                    eq(bNftKey.getLowOrderAsLong()),
                                    any(List.class),
                                    any(List.class)))
                    .willReturn(false);
            given(returnHelper.finishNft(true, bNftKey, nfts)).willReturn(null);
        }
    }

    private final long aSerialNo = 777L;
    private final long bSerialNo = 888L;
    private final EntityNum num = EntityNum.fromLong(123L);
    private final EntityId aTokenId = EntityId.fromNum(666L);
    private final EntityId bTokenId = EntityId.fromNum(777L);
    private final EntityNumPair aRelKey = EntityNumPair.fromNums(num, aTokenId.asNum());
    private final EntityNumPair aNftKey =
            new NftNumPair(aTokenId.num(), aSerialNo).asEntityNumPair();
    private final EntityNumPair bNftKey =
            new NftNumPair(bTokenId.num(), bSerialNo).asEntityNumPair();
    private final EntityNumPair bRelKey = EntityNumPair.fromNums(num, bTokenId.asNum());
    private final MerkleTokenRelStatus aRelStatus =
            new MerkleTokenRelStatus(0L, false, false, true);
    private final MerkleTokenRelStatus bRelStatus =
            new MerkleTokenRelStatus(1L, false, false, true);

    private final MerkleUniqueToken someNft =
            new MerkleUniqueToken(num.toEntityId(), "A".getBytes(), RichInstant.MISSING_INSTANT);

    private final MerkleAccount accountWithRels =
            MerkleAccountFactory.newAccount()
                    .lastAssociatedToken(aTokenId.num())
                    .associatedTokensCount(2)
                    .get();

    private final MerkleAccount accountWithNfts =
            MerkleAccountFactory.newAccount()
                    .nftsOwned(2)
                    .headNftTokenNum(aNftKey.getHiOrderAsLong())
                    .headNftSerialNo(aNftKey.getLowOrderAsLong())
                    .get();

    {
        accountWithRels.setKey(num);
        accountWithNfts.setKey(num);
        someNft.setNext(bNftKey.asNftNumPair());
    }

    private final MerkleToken fungibleToken = new MerkleToken();

    {
        fungibleToken.setTokenType(TokenType.FUNGIBLE_COMMON);
    }

    private final MerkleToken deletedFungibleToken = new MerkleToken();

    {
        deletedFungibleToken.setTokenType(TokenType.FUNGIBLE_COMMON);
        deletedFungibleToken.setDeleted(true);
    }

    private final MerkleToken nfToken = new MerkleToken();

    {
        nfToken.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);
    }

    private final MerkleToken deletedNfToken = new MerkleToken();

    {
        deletedNfToken.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);
        deletedNfToken.setDeleted(true);
    }
}
