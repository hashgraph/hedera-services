package com.hedera.services.txns.crypto.validators;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.CryptoWipeAllowance;
import com.hederahashgraph.api.proto.java.NftWipeAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenWipeAllowance;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedSerials;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_ALLOWANCES_TO_DELETE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

@Singleton
public class DeleteAllowanceChecks {
	protected final TypedTokenStore tokenStore;
	protected final AccountStore accountStore;
	protected final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> nftsMap;
	protected final GlobalDynamicProperties dynamicProperties;

	@Inject
	public DeleteAllowanceChecks(
			final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> nftsMap,
			final TypedTokenStore tokenStore,
			final AccountStore accountStore,
			final GlobalDynamicProperties dynamicProperties) {
		this.tokenStore = tokenStore;
		this.nftsMap = nftsMap;
		this.dynamicProperties = dynamicProperties;
		this.accountStore = accountStore;
	}

	public ResponseCodeEnum deleteAllowancesValidation(
			final List<CryptoWipeAllowance> cryptoAllowances,
			final List<TokenWipeAllowance> tokenAllowances,
			final List<NftWipeAllowance> nftAllowances,
			final Account payerAccount) {
		var validity = commonChecks(cryptoAllowances, tokenAllowances, nftAllowances);
		if (validity != OK) {
			return validity;
		}

		validity = validateCryptoAllowances(cryptoAllowances, payerAccount);
		if (validity != OK) {
			return validity;
		}

		validity = validateFungibleTokenAllowances(tokenAllowances, payerAccount);
		if (validity != OK) {
			return validity;
		}

		validity = validateNftAllowances(nftAllowances, payerAccount);
		if (validity != OK) {
			return validity;
		}

		return OK;
	}

	ResponseCodeEnum validateCryptoAllowances(final List<CryptoWipeAllowance> cryptoAllowances,
			final Account payerAccount) {
		final var distinctOwners = cryptoAllowances.stream()
				.map(a -> a.getOwner()).distinct().count();
		if (cryptoAllowances.size() != distinctOwners) {
			return REPEATED_ALLOWANCES_TO_DELETE;
		}

		for (var allowance : cryptoAllowances) {
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var result = fetchOwnerAccount(owner, payerAccount);
			if (result.getRight() != OK) {
				return result.getRight();
			}
		}
		return OK;
	}

	public ResponseCodeEnum validateFungibleTokenAllowances(
			final List<TokenWipeAllowance> tokenAllowancesList,
			final Account payerAccount) {
		if (tokenAllowancesList.isEmpty()) {
			return OK;
		}

		final var distinctIds = tokenAllowancesList.stream().collect(Collectors.toSet()).size();
		if (distinctIds != tokenAllowancesList.size()) {
			return REPEATED_ALLOWANCES_TO_DELETE;
		}

		for (final var allowance : tokenAllowancesList) {
			final var tokenId = allowance.getTokenId();
			var owner = Id.fromGrpcAccount(allowance.getOwner());

			final var token = tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(tokenId));
			final var fetchResult = fetchOwnerAccount(owner, payerAccount);
			if (fetchResult.getRight() != OK) {
				return fetchResult.getRight();
			}

			final var ownerAccount = fetchResult.getLeft();
			if (!token.isFungibleCommon()) {
				return NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
			}

			if (!ownerAccount.isAssociatedWith(Id.fromGrpcToken(tokenId))) {
				return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
			}
		}
		return OK;
	}

	public ResponseCodeEnum validateNftAllowances(
			final List<NftWipeAllowance> nftAllowancesList,
			final Account payerAccount) {
		if (nftAllowancesList.isEmpty()) {
			return OK;
		}
		if (repeatedAllowances(nftAllowancesList)) {
			return REPEATED_ALLOWANCES_TO_DELETE;
		}
		for (var allowance : nftAllowancesList) {
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var tokenId = allowance.getTokenId();
			final var serialNums = allowance.getSerialNumbersList();
			final var token = tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(tokenId));

			if (token.isFungibleCommon()) {
				return FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
			}

			final var fetchResult = fetchOwnerAccount(owner, payerAccount);
			if (fetchResult.getRight() != OK) {
				return fetchResult.getRight();
			}
			final var ownerAccount = fetchResult.getLeft();

			if (!ownerAccount.isAssociatedWith(Id.fromGrpcToken(tokenId))) {
				return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
			}
			final var validity = validateSerialNums(serialNums, ownerAccount, token);
			if (validity != OK) {
				return validity;
			}
		}

		return OK;
	}

	boolean repeatedAllowances(final List<NftWipeAllowance> nftAllowancesList) {
		Map<Pair, List<Long>> nftsMap = new HashMap<>();
		for (var allowance : nftAllowancesList) {
			final var key = Pair.of(allowance.getOwner(), allowance.getTokenId());
			if (nftsMap.containsKey(key)) {
				if (serialsRepeated(nftsMap.get(key), allowance.getSerialNumbersList())) {
					return true;
				} else {
					final var list = new ArrayList<Long>();
					list.addAll(nftsMap.get(key));
					list.addAll(allowance.getSerialNumbersList());
					nftsMap.put(key, list);
				}
			} else {
				nftsMap.put(key, allowance.getSerialNumbersList());
			}
		}
		return false;
	}

	boolean serialsRepeated(final List<Long> existingSerials, final List<Long> newSerials) {
		for (var serial : newSerials) {
			if (existingSerials.contains(serial)) {
				return true;
			}
		}
		return false;
	}

	ResponseCodeEnum validateSerialNums(final List<Long> serialNums,
			final Account ownerAccount,
			final Token token) {
		if (hasRepeatedSerials(serialNums)) {
			return REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
		}

		if (serialNums.isEmpty()) {
			return EMPTY_ALLOWANCES;
		}

		for (var serial : serialNums) {
			final var nftId = NftId.withDefaultShardRealm(token.getId().num(), serial);

			if (serial < 0 || serial == 0 || !nftsMap.get().containsKey(EntityNumPair.fromNftId(nftId))) {
				return INVALID_TOKEN_NFT_SERIAL_NUMBER;
			}

			final var nft = nftsMap.get().get(EntityNumPair.fromNftId(nftId));
			if (!AllowanceChecks.validOwner(nft, ownerAccount, token)) {
				return SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
			}
		}

		return OK;
	}

	ResponseCodeEnum commonChecks(
			final List<CryptoWipeAllowance> cryptoAllowances,
			final List<TokenWipeAllowance> tokenAllowances,
			final List<NftWipeAllowance> nftAllowances) {
		final var totalAllowances = cryptoAllowances.size() + tokenAllowances.size() + nftAllowances.size();
		if (totalAllowances == 0) {
			return EMPTY_ALLOWANCES;
		}
		return OK;
	}

	Pair<Account, ResponseCodeEnum> fetchOwnerAccount(Id owner, Account payerAccount) {
		if (owner.equals(Id.MISSING_ID) || owner.equals(payerAccount.getId())) {
			return Pair.of(payerAccount, OK);
		} else {
			try {
				return Pair.of(accountStore.loadAccount(owner), OK);
			} catch (InvalidTransactionException ex) {
				return Pair.of(payerAccount, INVALID_ALLOWANCE_OWNER_ID);
			}
		}
	}
}
