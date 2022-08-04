package com.hedera.services.state.migration;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.services.utils.MiscUtils.forEach;
import static java.util.Comparator.comparingLong;

import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ReleaseTwentySevenMigration {
	private static final long UNUSABLE_NUM = -1;
	private static final Logger log = LogManager.getLogger(ReleaseTwentySevenMigration.class);
	private ReleaseTwentySevenMigration() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static MerkleMap<EntityNum, MerkleStakingInfo> buildStakingInfoMap(
			final AddressBook addressBook,
			final BootstrapProperties bootstrapProperties
	) {
		final MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos = new MerkleMap<>();

		final var numberOfNodes = addressBook.getSize();
		long maxStakePerNode = bootstrapProperties.getLongProperty("ledger.totalTinyBarFloat") / numberOfNodes;
		long minStakePerNode = maxStakePerNode / 2;
		for (int i = 0; i < numberOfNodes; i++) {
			final var nodeNum = EntityNum.fromLong(addressBook.getAddress(i).getId());
			final var info = new MerkleStakingInfo(bootstrapProperties);
			info.setMinStake(minStakePerNode);
			info.setMaxStake(maxStakePerNode);
			stakingInfos.put(nodeNum, info);
		}

		return stakingInfos;
	}

	public static void fixNftCounts(
			final MerkleMap<EntityNum, MerkleToken> tokens,
			final MerkleMap<EntityNum, MerkleAccount> accounts,
			final MerkleMap<EntityNumPair, MerkleUniqueToken> nfts,
			final MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels
	) {
		/* The actual number of NFTs owned by each account */
		final Map<EntityNum, Long> nftsOwned = new HashMap<>();
		/* The actual number of NFTs by type owned by each account */
		final Map<EntityNumPair, Long> nftBalances = new HashMap<>();

		forEach(nfts, (id, nft) -> {
			final var tokenTypeKey = id.getHiOrderAsNum();
			final var owner = effectiveOwner(tokens, nft, tokenTypeKey);
			if (owner == UNUSABLE_NUM) {
				return;
			}
			final var ownerKey = EntityNum.fromLong(owner);
			nftsOwned.merge(ownerKey, 1L, Long::sum);
			final var relKey = EntityNumPair.fromLongs(owner, tokenTypeKey.longValue());
			nftBalances.merge(relKey, 1L, Long::sum);
		});

		fixCounts(nftsOwned, accounts);
		fixBalances(nftBalances, tokenRels);
	}

	private static void fixBalances(
			final Map<EntityNumPair, Long> nftBalances,
			final MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels
	) {
		nftBalances.entrySet()
				.stream()
				.sorted(comparingLong(entry -> entry.getKey().value()))
				.forEach(entry -> {
					final var key = entry.getKey();
					final var expected = entry.getValue();
					final var rel = tokenRels.get(key);
					if (rel == null) {
						log.error(
								"Missing (0.0.{}, 0.0.{}) account/token rel claimed to include {} NFTs",
								key.getHiOrderAsLong(), key.getLowOrderAsLong(), expected);
					} else {
						final var actual = rel.getBalance();
						if (actual != expected) {
							log.warn(
									"(0.0.{}, 0.0.{}) account/token rel " +
											"claimed to include {} NFTs, is actually {}---fixing",
									key.getHiOrderAsLong(), key.getLowOrderAsLong(), actual, expected);
							final var mutableRel = tokenRels.getForModify(key);
							mutableRel.setBalance(expected);
						}
					}
				});
	}

	private static void fixCounts(
			final Map<EntityNum, Long> nftsOwned,
			final MerkleMap<EntityNum, MerkleAccount> accounts
	) {
		nftsOwned.entrySet()
				.stream()
				.sorted(comparingLong(entry -> entry.getKey().longValue()))
				.forEach(entry -> {
					final var key = entry.getKey();
					final var expected = entry.getValue();
					final var account = accounts.get(key);
					if (account == null) {
						log.error("Missing account 0.0.{} expected to own {} NFTs", key.longValue(), expected);
					} else {
						final var actual = account.getNftsOwned();
						if (actual != expected) {
							log.warn(
									"Account 0.0.{} claimed to own {} NFTs, is actually {}---fixing",
									key.longValue(), actual, expected);
							final var mutableAccount = accounts.getForModify(key);
							mutableAccount.setNftsOwned(expected);
						}
					}
				});
	}

	private static long effectiveOwner(
			final MerkleMap<EntityNum, MerkleToken> tokens,
			final MerkleUniqueToken nft,
			final EntityNum tokenType
	) {
		if (!nft.isTreasuryOwned()) {
			return nft.getOwner().num();
		} else {
			final var token = tokens.get(tokenType);
			if (token == null) {
				log.error("NFT {} linked to missing token type 0.0.{}", nft, tokenType);
				return UNUSABLE_NUM;
			}
			final var treasury = token.treasury();
			if (treasury == null) {
				log.error("Token type 0.0.{} has null treasury", tokenType);
				return UNUSABLE_NUM;
			}
			return treasury.num();
		}
	}
}
