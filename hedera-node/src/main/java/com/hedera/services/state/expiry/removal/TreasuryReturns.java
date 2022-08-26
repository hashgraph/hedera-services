package com.hedera.services.state.expiry.removal;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.throttling.ExpiryThrottle;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TreasuryReturns {
//    static final List<MapAccessType> ONLY__REMOVAL_WORK = List.of(STORAGE_REMOVE);
//    static final List<MapAccessType> NEXT_SLOT_REMOVAL_WORK = List.of(STORAGE_REMOVE, STORAGE_GET, STORAGE_PUT);
    /*
        List<CurrencyAdjustments> returnTransfers = Collections.emptyList();
        var expectedRels = account.getNumAssociations();
        var done = account.getNftsOwned() == 0;
        if (expectedRels > 0) {
            tokenTypes = new ArrayList<>();
            returnTransfers = new ArrayList<>();
            doFungibleTreasuryReturnsWith(
                    expectedRels,
                    expiredAccountNum,
                    account.getLatestAssociation(),
                    tokenTypes,
                    returnTransfers,
                    tokenRels.get());
            account.setNumAssociations(0);
        }

        if (!done) {
            final var nftsOwned = account.getNftsOwned();
            returnNftsToTreasury(
                    nftsOwned,
                    account.getHeadNftId(),
                    account.getHeadNftSerialNum(),
                    uniqueTokens.get());

            final var remainingNfts =
                    nftsOwned < dynamicProperties.getMaxReturnedNftsPerTouch()
                            ? 0
                            : nftsOwned - dynamicProperties.getMaxReturnedNftsPerTouch();
            account.setNftsOwned(remainingNfts);
            done = remainingNfts == 0;
        }

     */

   private final ExpiryThrottle expiryThrottle;
   private final TreasuryReturnHelper returnHelper;

   @Inject
    public TreasuryReturns(final ExpiryThrottle expiryThrottle, final TreasuryReturnHelper returnHelper) {
        this.expiryThrottle = expiryThrottle;
        this.returnHelper = returnHelper;
    }

    @Nullable
    public NonFungibleTreasuryReturns returnNftsFrom(final MerkleAccount expiredAccount) {
        throw new AssertionError("Not implemented");
    }

    @Nullable
    public FungibleTreasuryReturns returnFungibleUnitsFrom(final MerkleAccount expiredAccount) {
        throw new AssertionError("Not implemented");
    }

    /*

    private void returnNftsToTreasury(
            final long nftsOwned,
            final long headNftNum,
            final long headSerialNum,
            final MerkleMap<EntityNumPair, MerkleUniqueToken> currUniqueTokens) {
        var nftKey = EntityNumPair.fromLongs(headNftNum, headSerialNum);
        var i = Math.min(nftsOwned, dynamicProperties.getMaxReturnedNftsPerTouch());
        while (nftKey != null && i-- > 0) {
            nftKey = treasuryReturnHelper.updateNftReturns(nftKey, currUniqueTokens);
        }
    }

     */

    /*

    private void doFungibleTreasuryReturnsWith(
            final int expectedRels,
            final EntityNum expiredAccountNum,
            final EntityNumPair firstRelKey,
            final List<EntityId> tokenTypes,
            final List<CurrencyAdjustments> returnTransfers,
            final MerkleMap<EntityNumPair, MerkleTokenRelStatus> curRels) {
        final var listRemoval = new TokenRelsListMutation(expiredAccountNum.longValue(), curRels);
        var i = expectedRels;
        var relKey = firstRelKey;
        while (relKey != null && i-- > 0) {
            final var rel = curRels.get(relKey);
            final var tokenNum = relKey.getLowOrderAsNum();
            final var tokenBalance = rel.getBalance();
            if (tokenBalance > 0) {
                treasuryReturnHelper.updateFungibleReturns(
                        expiredAccountNum, tokenNum, tokenBalance, returnTransfers);
            }
            // We are always removing the root, hence receiving the new root
            relKey = removalFacilitation.removeNext(relKey, relKey, listRemoval);
            tokenTypes.add(tokenNum.toEntityId());
        }
    }
     */

    /*

    @FunctionalInterface
    interface RemovalFacilitation {
        EntityNumPair removeNext(
                EntityNumPair key, EntityNumPair root, TokenRelsListMutation listRemoval);
    }

    @VisibleForTesting
    void setRemovalFacilitation(final RemovalFacilitation removalFacilitation) {
        this.removalFacilitation = removalFacilitation;
    }
    private RemovalFacilitation removalFacilitation =
            MapValueListUtils::removeInPlaceFromMapValueList;

    private final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels;
    private final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> uniqueTokens;
     */
}
