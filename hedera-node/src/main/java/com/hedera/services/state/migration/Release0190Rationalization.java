package com.hedera.services.state.migration;

import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.utils.MiscUtils.forEach;
import static java.util.Comparator.comparingLong;

/**
 * Static helper to fix state distorted by two issues:
 * <ol>
 *     <li>https://github.com/hashgraph/hedera-services/issues/2359 :: Could cause the "balance" of NFTs
 *     owned by an account to be artificially inflated. </li>
 *     <li>https://github.com/hashgraph/hedera-services/issues/2360 :: Could cause an immutable contract's
 *     admin key to have a number other than the id of the contract.</li>
 * </ol>
 *
 * So this helper first scans all NFTs and computes the actual numbers of NFTs owned by each account
 * (both total and by token type). With these counts in hand, it fixes any inflated numbers in the
 * {@code accounts} and {@code tokenRels} maps via {@link MerkleMap#getForModify(Object)}.
 *
 * Then the helper scans all accounts for immutable contracts whose admin key has the wrong entity number.
 * It accumulates any such contract numbers in a list, and then fixes them via
 * {@link MerkleMap#getForModify(Object)}.
 */
public final class Release0190Rationalization {
	private static final Logger log = LogManager.getLogger(Release0190Rationalization.class);

	private static final long UNUSABLE_NUM = -1;

	public static void rationalizeState(
			final MerkleMap<EntityNum, MerkleToken> tokens,
			final MerkleMap<EntityNum, MerkleAccount> accounts,
			final MerkleMap<EntityNumPair, MerkleUniqueToken> nfts,
			final MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels
	) {
		fixNftCounts(tokens, accounts, nfts, tokenRels);
		fixContractIdKeys(accounts);
	}

	static void fixNftCounts(
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
			final var tokenTypeKey = id.getHiPhi();
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
				.sorted(comparingLong(entry -> entry.getKey().getValue()))
				.forEach(entry -> {
					final var key = entry.getKey();
					final var expected = entry.getValue();
					final var rel = tokenRels.get(key);
					if (rel == null) {
						log.error(
								"Missing (0.0.{}, 0.0.{}) account/token rel claimed to include {} NFTs",
								key.hi(), key.lo(), expected);
					} else {
						final var actual = rel.getBalance();
						if (actual != expected) {
							log.warn(
									"(0.0.{}, 0.0.{}) account/token rel " +
											"claimed to include {} NFTs, is actually {}---fixing",
									key.hi(), key.lo(), actual, expected);
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

	static void fixContractIdKeys(final MerkleMap<EntityNum, MerkleAccount> accounts) {
		final List<EntityNum> misKeyedContracts = new ArrayList<>();
		forEach(accounts, (id, account) -> {
			if (account.isSmartContract()) {
				final var key = account.getAccountKey();
				if (key instanceof JContractIDKey) {
					final var actual = ((JContractIDKey) key).getContractNum();
					final var expected = id.longValue();
					if (actual != expected) {
						log.warn(
								"Contract 0.0.{} has id key 0.0.{}---fixing",
								expected, actual);
						misKeyedContracts.add(id);
					}
				}
			}
		});

		misKeyedContracts.sort(comparingLong(EntityNum::longValue));
		misKeyedContracts.forEach(id -> {
			final var mutableContract = accounts.getForModify(id);
			final var correctKey = new JContractIDKey(0, 0, id.longValue());
			mutableContract.setAccountKey(correctKey);
		});
	}
}
