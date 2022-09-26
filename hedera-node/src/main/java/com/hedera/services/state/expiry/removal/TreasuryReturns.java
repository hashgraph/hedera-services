/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.state.expiry.removal.ContractGC.ROOT_KEY_UPDATE_WORK;
import static com.hedera.services.state.expiry.removal.FungibleTreasuryReturns.FINISHED_NOOP_FUNGIBLE_RETURNS;
import static com.hedera.services.state.expiry.removal.FungibleTreasuryReturns.UNFINISHED_NOOP_FUNGIBLE_RETURNS;
import static com.hedera.services.state.expiry.removal.NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS;
import static com.hedera.services.state.expiry.removal.NonFungibleTreasuryReturns.UNFINISHED_NOOP_NON_FUNGIBLE_RETURNS;
import static com.hedera.services.throttling.MapAccessType.*;
import static com.hedera.services.utils.EntityNumPair.MISSING_NUM_PAIR;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.TokenRelsListMutation;
import com.hedera.services.state.expiry.classification.EntityLookup;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.UniqueTokenMapAdapter;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.NftAdjustments;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.throttling.MapAccessType;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.MapValueListUtils;
import com.swirlds.merkle.map.MerkleMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class TreasuryReturns {
    private static final Logger log = LogManager.getLogger(TreasuryReturns.class);
    private static final MerkleToken STANDIN_DELETED_TOKEN = new MerkleToken();

    static {
        STANDIN_DELETED_TOKEN.setDeleted(true);
    }

    static final List<MapAccessType> TOKEN_DELETION_CHECK = List.of(TOKENS_GET);
    static final List<MapAccessType> ONLY_REL_REMOVAL_WORK =
            List.of(TOKENS_GET, TOKEN_ASSOCIATIONS_GET, TOKEN_ASSOCIATIONS_REMOVE);
    static final List<MapAccessType> NEXT_REL_REMOVAL_WORK =
            List.of(
                    TOKENS_GET,
                    TOKEN_ASSOCIATIONS_GET,
                    TOKEN_ASSOCIATIONS_REMOVE,
                    TOKEN_ASSOCIATIONS_GET_FOR_MODIFY);
    static final List<MapAccessType> NFT_BURN_WORK = List.of(NFTS_GET, NFTS_REMOVE);
    static final List<MapAccessType> NFT_RETURN_WORK = List.of(NFTS_GET_FOR_MODIFY);
    static final List<MapAccessType> ROOT_META_UPDATE_WORK = List.of(ACCOUNTS_GET_FOR_MODIFY);
    static final List<MapAccessType> TREASURY_BALANCE_INCREMENT =
            List.of(ACCOUNTS_GET, TOKEN_ASSOCIATIONS_GET_FOR_MODIFY);

    private final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens;
    private final Supplier<UniqueTokenMapAdapter> nfts;
    private final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels;

    private final EntityLookup entityLookup;
    private final ExpiryThrottle expiryThrottle;
    private final TreasuryReturnHelper returnHelper;

    private RelRemover relRemover = MapValueListUtils::removeInPlaceFromMapValueList;

    @Inject
    public TreasuryReturns(
            final EntityLookup entityLookup,
            final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens,
            final Supplier<UniqueTokenMapAdapter> nfts,
            final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels,
            final ExpiryThrottle expiryThrottle,
            final TreasuryReturnHelper returnHelper) {
        this.nfts = nfts;
        this.tokens = tokens;
        this.tokenRels = tokenRels;
        this.expiryThrottle = expiryThrottle;
        this.returnHelper = returnHelper;
        this.entityLookup = entityLookup;
    }

    @Nullable
    public FungibleTreasuryReturns returnFungibleUnitsFrom(final MerkleAccount expired) {
        final var expiredNum = expired.getKey();
        final var numRels = expired.getNumAssociations();
        if (numRels == 0) {
            return FINISHED_NOOP_FUNGIBLE_RETURNS;
        } else if (!expiryThrottle.allow(ROOT_KEY_UPDATE_WORK)) {
            return UNFINISHED_NOOP_FUNGIBLE_RETURNS;
        } else {
            final var outcome = tryFungibleReturns(expiredNum, expired);
            final var mutableExpired = entityLookup.getMutableAccount(expiredNum);
            mutableExpired.setNumAssociations(outcome.remainingAssociations());
            final var newLatestAssociation = outcome.newRoot();
            if (newLatestAssociation != null) {
                mutableExpired.setHeadTokenId(newLatestAssociation.getLowOrderAsLong());
            }
            // Once we've done any auto-removal work, we make sure the account is deleted
            mutableExpired.setDeleted(true);
            return outcome.fungibleReturns();
        }
    }

    @Nullable
    public NonFungibleTreasuryReturns returnNftsFrom(final MerkleAccount expired) {
        final var expiredNum = expired.getKey();
        final var numNfts = expired.getNftsOwned();
        if (numNfts == 0) {
            return FINISHED_NOOP_NON_FUNGIBLE_RETURNS;
        } else if (!expiryThrottle.allow(ROOT_KEY_UPDATE_WORK)) {
            return UNFINISHED_NOOP_NON_FUNGIBLE_RETURNS;
        } else {
            final var outcome = tryNftReturns(expiredNum, expired);
            final var mutableExpired = entityLookup.getMutableAccount(expiredNum);
            mutableExpired.setNftsOwned(outcome.remainingNfts());
            final var newNftKey = outcome.newRoot();
            if (newNftKey != null) {
                mutableExpired.setHeadNftId(newNftKey.getHiOrderAsLong());
                mutableExpired.setHeadNftSerialNum(newNftKey.getLowOrderAsLong());
            }
            // Once we've done any auto-removal work, we make sure the account is deleted
            mutableExpired.setDeleted(true);
            return outcome.nftReturns();
        }
    }

    private NftReturnOutcome tryNftReturns(
            final EntityNum expiredNum, final MerkleAccount expired) {
        final var curNfts = nfts.get();
        final var expectedNfts = expired.getNftsOwned();

        var n = 0;
        var i = expectedNfts;
        var nftKey = expired.getHeadNftKey();

        final List<EntityId> tokenTypes = new ArrayList<>();
        final List<NftAdjustments> returnExchanges = new ArrayList<>();
        if (MISSING_NUM_PAIR.equals(nftKey) && i > 0) {
            log.warn(
                    "Account 0.0.{} claimed to own {} NFTs, but head key is missing",
                    expiredNum.longValue(),
                    i);
            nftKey = null;
        }
        while (nftKey != null && expiryThrottle.allow(TOKEN_DELETION_CHECK) && i-- > 0) {
            final var tokenNum = nftKey.getHiOrderAsNum();
            var token = tokens.get().get(tokenNum);
            if (token == null) {
                token = STANDIN_DELETED_TOKEN;
            }
            final var expectedBurn = token.isDeleted();
            if (!hasCapacityForNftReturn(expectedBurn)) {
                break;
            }
            try {
                final var returnedSerialNo = nftKey.getLowOrderAsLong();
                nftKey =
                        returnHelper.burnOrReturnNft(
                                expectedBurn, nftKey.asNftNumPair().nftId(), curNfts);
                returnHelper.updateNftReturns(
                        expiredNum, tokenNum, token, returnedSerialNo, tokenTypes, returnExchanges);
                n++;
            } catch (Exception unrecoverable) {
                log.error(
                        "Unable to return all NFTs from account 0.0.{} (failed with 0.0.{}.{})",
                        expiredNum.longValue(),
                        nftKey.getHiOrderAsLong(),
                        nftKey.getLowOrderAsLong(),
                        unrecoverable);
                nftKey = null;
            }
        }

        final var numLeft = (nftKey == null) ? 0 : (expectedNfts - n);
        return new NftReturnOutcome(
                new NonFungibleTreasuryReturns(tokenTypes, returnExchanges, numLeft == 0),
                nftKey,
                numLeft);
    }

    @SuppressWarnings("java:S3776")
    private FungibleReturnOutcome tryFungibleReturns(
            final EntityNum expiredNum, final MerkleAccount expired) {
        final var curRels = tokenRels.get();
        final var listRemoval = new TokenRelsListMutation(expiredNum.longValue(), curRels);
        final var expectedRels = expired.getNumAssociations();

        var n = 0;
        var i = expectedRels;
        var relKey = expired.getLatestAssociation();
        final List<EntityId> tokenTypes = new ArrayList<>();
        final List<CurrencyAdjustments> returnTransfers = new ArrayList<>();
        while (relKey != null && hasCapacityForRelRemovalAt(i) && i-- > 0) {
            final var tokenNum = relKey.getLowOrderAsNum();
            final var token = tokens.get().get(tokenNum);
            try {
                if (token != null && token.tokenType() == TokenType.FUNGIBLE_COMMON) {
                    final var rel = curRels.get(relKey);
                    final var tokenBalance = rel.getBalance();
                    if (tokenBalance > 0) {
                        if (!token.isDeleted()
                                && !expiryThrottle.allow(TREASURY_BALANCE_INCREMENT)) {
                            break;
                        }
                        tokenTypes.add(tokenNum.toEntityId());
                        returnHelper.updateFungibleReturns(
                                expiredNum,
                                tokenNum,
                                token,
                                tokenBalance,
                                returnTransfers,
                                curRels);
                    }
                }
                relKey = relRemover.removeNext(relKey, relKey, listRemoval);
                n++;
            } catch (Exception unrecoverable) {
                log.error(
                        "Unable to return all fungible units from account 0.0.{}",
                        expiredNum.longValue(),
                        unrecoverable);
                relKey = null;
            }
        }
        final var numLeft = (relKey == null) ? 0 : (expectedRels - n);
        return new FungibleReturnOutcome(
                new FungibleTreasuryReturns(tokenTypes, returnTransfers, numLeft == 0),
                relKey,
                numLeft);
    }

    private boolean hasCapacityForRelRemovalAt(final int n) {
        return expiryThrottle.allow(n == 1 ? ONLY_REL_REMOVAL_WORK : NEXT_REL_REMOVAL_WORK);
    }

    private boolean hasCapacityForNftReturn(final boolean burn) {
        return expiryThrottle.allow(burn ? NFT_BURN_WORK : NFT_RETURN_WORK);
    }

    @FunctionalInterface
    interface RelRemover {
        EntityNumPair removeNext(
                EntityNumPair key, EntityNumPair root, TokenRelsListMutation listRemoval);
    }

    @VisibleForTesting
    void setRelRemovalFacilitation(final RelRemover relRemover) {
        this.relRemover = relRemover;
    }
}
