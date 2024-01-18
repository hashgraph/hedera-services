/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.schemas.InitialModServiceTokenSchema;
import com.hedera.node.app.service.token.impl.schemas.SyntheticRecordsGenerator;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.SortedSet;
import java.util.function.Supplier;

/** An implementation of the {@link TokenService} interface. */
public class TokenServiceImpl implements TokenService {
    public static final String NFTS_KEY = "NFTS";
    public static final String TOKENS_KEY = "TOKENS";
    public static final String ALIASES_KEY = "ALIASES";
    public static final String ACCOUNTS_KEY = "ACCOUNTS";
    public static final String TOKEN_RELS_KEY = "TOKEN_RELS";
    public static final String STAKING_INFO_KEY = "STAKING_INFOS";
    public static final String STAKING_NETWORK_REWARDS_KEY = "STAKING_NETWORK_REWARDS";
    private final Supplier<SortedSet<Account>> sysAccts;
    private final Supplier<SortedSet<Account>> stakingAccts;
    private final Supplier<SortedSet<Account>> treasuryAccts;
    private final Supplier<SortedSet<Account>> miscAccts;
    private final Supplier<SortedSet<Account>> blocklistAccts;
    private VirtualMap<UniqueTokenKey, UniqueTokenValue> nftsFs;
    private VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> trFs;
    private VirtualMap<EntityNumVirtualKey, OnDiskAccount> acctsFs;
    private MerkleMap<EntityNum, MerkleToken> tFs;
    private MerkleMap<EntityNum, MerkleStakingInfo> stakingFs;
    private MerkleNetworkContext mnc;

    public void setStakingFs(MerkleMap<EntityNum, MerkleStakingInfo> stakingFs, MerkleNetworkContext mnc) {
        this.stakingFs = stakingFs;
        this.mnc = mnc;
    }

    /**
     * Constructor for the token service. Each of the given suppliers should produce a {@link SortedSet}
     * of {@link Account} objects, where each account object represents a SYNTHETIC RECORD (see {@link
     * SyntheticRecordsGenerator} for more details). Even though these sorted sets contain account objects,
     * these account objects may or may not yet exist in state. They're needed for event recovery circumstances
     * @param sysAccts
     * @param stakingAccts
     * @param treasuryAccts
     * @param miscAccts
     * @param blocklistAccts
     */
    public TokenServiceImpl(
            @NonNull final Supplier<SortedSet<Account>> sysAccts,
            @NonNull final Supplier<SortedSet<Account>> stakingAccts,
            @NonNull final Supplier<SortedSet<Account>> treasuryAccts,
            @NonNull final Supplier<SortedSet<Account>> miscAccts,
            @NonNull final Supplier<SortedSet<Account>> blocklistAccts) {
        this.sysAccts = sysAccts;
        this.stakingAccts = stakingAccts;
        this.treasuryAccts = treasuryAccts;
        this.miscAccts = miscAccts;
        this.blocklistAccts = blocklistAccts;
    }

    /**
     * Necessary default constructor. See all params constructor for more details
     */
    public TokenServiceImpl() {
        this.sysAccts = Collections::emptySortedSet;
        this.stakingAccts = Collections::emptySortedSet;
        this.treasuryAccts = Collections::emptySortedSet;
        this.miscAccts = Collections::emptySortedSet;
        this.blocklistAccts = Collections::emptySortedSet;
    }

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry, final SemanticVersion version) {
        requireNonNull(registry);
        registry.register(new InitialModServiceTokenSchema(
                sysAccts,
                stakingAccts,
                treasuryAccts,
                miscAccts,
                blocklistAccts,
                version,
                acctsFs,
                tFs,
                stakingFs,
                nftsFs,
                trFs,
                mnc));

        // Once the 'from' state is passed in to the schema class, we don't need that reference in this class anymore.
        // We don't want to keep these references around because, in the case of migrating from mono to mod service, we
        // want the old mono state routes to disappear
        nftsFs = null;
        trFs = null;
        acctsFs = null;
        tFs = null;
        stakingFs = null;
        mnc = null;
    }

    public void setNftsFromState(VirtualMap<UniqueTokenKey, UniqueTokenValue> fs) {
        this.nftsFs = fs;
    }

    public void setTokenRelsFromState(VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> fs) {
        this.trFs = fs;
    }

    public void setAcctsFromState(VirtualMap<EntityNumVirtualKey, OnDiskAccount> fs) {
        this.acctsFs = fs;
    }

    public void setTokensFromState(MerkleMap<EntityNum, MerkleToken> fs) {
        this.tFs = fs;
    }
}
