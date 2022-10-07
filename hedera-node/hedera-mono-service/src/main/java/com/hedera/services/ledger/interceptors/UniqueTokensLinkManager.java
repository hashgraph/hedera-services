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
package com.hedera.services.ledger.interceptors;

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.services.utils.MapValueListUtils.insertInPlaceAtMapValueListHead;
import static com.hedera.services.utils.MapValueListUtils.linkInPlaceAtMapValueListHead;
import static com.hedera.services.utils.MapValueListUtils.unlinkInPlaceFromMapValueList;

import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.context.properties.PropertyNames;
import com.hedera.services.state.expiry.UniqueTokensListMutation;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.migration.UniqueTokenMapAdapter;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UniqueTokensLinkManager {
    private static final Logger log = LogManager.getLogger(UniqueTokensLinkManager.class);

    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
    private final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens;
    private final Supplier<UniqueTokenMapAdapter> uniqueTokens;
    private final boolean enableVirtualNft;

    @Inject
    public UniqueTokensLinkManager(
            final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
            final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens,
            final Supplier<UniqueTokenMapAdapter> uniqueTokens,
            final BootstrapProperties bootstrapProperties) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.uniqueTokens = uniqueTokens;
        this.enableVirtualNft =
                bootstrapProperties.getBooleanProperty(
                        PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE);
    }

    /**
     * Given the previous owner and new owner of the NFT with some id, updates the link fields of
     * the {@code accounts} and {@code uniqueTokens} maps.
     *
     * <p>If the new owner is null, the call implies a burn.
     *
     * <p>If the previous owner is null, the call implies a "non-treasury" mint via a multi-stage
     * contract operation. In this case, there is no existing NFT to in the {@code uniqueTokens}
     * map, and the {@code linksManager} must insert one itself.
     *
     * @param from the previous owner of the NFT, if any
     * @param to the new owner of the NFT, if any
     * @param nftId the id of the NFT changing owners
     * @return the newly minted NFT, if one needed to be inserted
     */
    @Nullable
    public UniqueTokenAdapter updateLinks(
            @Nullable final EntityNum from,
            @Nullable final EntityNum to,
            @Nonnull final NftId nftId) {
        final var curAccounts = accounts.get();
        final var curTokens = tokens.get();
        final var curUniqueTokens = uniqueTokens.get();

        final var token = curTokens.get(EntityNum.fromLong(nftId.num()));
        final var listMutation = new UniqueTokensListMutation(curUniqueTokens);

        UniqueTokenAdapter insertedNft = null;
        // Update "from" account
        if (isValidAndNotTreasury(from, token)) {
            final var fromAccount = curAccounts.getForModify(from);
            var rootKey = rootKeyOf(fromAccount);
            if (rootKey != null) {
                rootKey = unlinkInPlaceFromMapValueList(nftId, rootKey, listMutation);
            } else {
                log.error("Invariant failure: {} owns NFT {}, but has no root link", from, nftId);
            }
            fromAccount.setHeadNftId((rootKey == null) ? 0 : rootKey.num());
            fromAccount.setHeadNftSerialNum((rootKey == null) ? 0 : rootKey.serialNo());
        }

        // Update "to" account
        if (isValidAndNotTreasury(to, token)) {
            final var toAccount = curAccounts.getForModify(to);
            var nft = listMutation.getForModify(nftId);
            var rootKey = rootKeyOf(toAccount);
            if (nft != null) {
                linkInPlaceAtMapValueListHead(nftId, nft, rootKey, null, listMutation);
            } else {
                // This is "non-treasury mint" done via a multi-stage contract op; we need to
                // create a NFT whose link pointers we can update, since it doesn't exist yet
                insertedNft =
                        enableVirtualNft
                                ? UniqueTokenAdapter.wrap(new UniqueTokenValue())
                                : UniqueTokenAdapter.wrap(new MerkleUniqueToken());
                insertInPlaceAtMapValueListHead(nftId, insertedNft, rootKey, null, listMutation);
            }
            toAccount.setHeadNftId(nftId.num());
            toAccount.setHeadNftSerialNum(nftId.serialNo());
        }

        return insertedNft;
    }

    private boolean isValidAndNotTreasury(EntityNum accountNum, MerkleToken token) {
        return accountNum != null
                && !accountNum.equals(MISSING_NUM)
                && !accountNum.equals(token.treasuryNum());
    }

    @Nullable
    private NftId rootKeyOf(final MerkleAccount account) {
        final var headNum = account.getHeadNftTokenNum();
        final var headSerialNum = account.getHeadNftSerialNum();
        return headNum == 0 ? null : NftId.withDefaultShardRealm(headNum, headSerialNum);
    }
}
