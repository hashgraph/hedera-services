package com.hedera.services.tokens;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.BackingAccounts;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fcmap.FCMap;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.hedera.services.state.merkle.MerkleEntityId.fromTokenId;
import static com.hedera.services.tokens.TokenCreationResult.failure;
import static com.hedera.services.tokens.TokenCreationResult.success;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DIVISIBILITY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_FLOAT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static java.util.stream.IntStream.range;

/**
 * Provides a managing store for arbitrary tokens.
 *
 * @author Michael Tinker
 */
public class HederaTokenStore implements TokenStore {
	static final TokenID NO_PENDING_ID = TokenID.getDefaultInstance();

	private final EntityIdSource ids;
	private final GlobalDynamicProperties properties;
	private final BackingAccounts<AccountID, MerkleAccount> accounts;
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;

	TokenID pendingId = NO_PENDING_ID;
	MerkleToken pendingCreation;

	public HederaTokenStore(
			EntityIdSource ids,
			GlobalDynamicProperties properties,
			BackingAccounts<AccountID, MerkleAccount> accounts,
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens
	) {
		this.ids = ids;
		this.tokens = tokens;
		this.accounts = accounts;
		this.properties = properties;
	}

	@Override
	public ResponseCodeEnum relationshipStatus(MerkleAccount account, TokenID id) {
		if (account.numTokenRelationships() >= properties.maxTokensPerAccount()) {
			return account.hasRelationshipWith(id) ? OK : TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
		}
		return OK;
	}

	@Override
	public TokenCreationResult createProvisionally(TokenCreation request, AccountID sponsor) {
		var adminKey = asUsableFcKey(request.getAdminKey());
		if (adminKey.isEmpty())	{
			return failure(INVALID_ADMIN_KEY);
		}

		var validity = symbolCheck(request.getSymbol());
		if (validity != OK) {
			return failure(validity);
		}
		validity = treasuryCheck(request.getTreasury());
		if (validity != OK) {
			return failure(validity);
		}
		validity = floatAndDivisibilityCheck(request.getFloat(), request.getDivisibility());
		if (validity != OK) {
			return failure(validity);
		}
		validity = freezeSemanticsCheck(request);
		if (validity != OK) {
			return failure(validity);
		}

		var created = ids.newTokenId(sponsor);
		return success(created);
	}

	private ResponseCodeEnum freezeSemanticsCheck(TokenCreation request) {
		var candidate = asUsableFcKey(request.getFreezeKey());
		if (candidate.isEmpty() && request.getFreezeDefault()) {
			return TOKEN_HAS_NO_FREEZE_KEY;
		}
		return OK;
	}

	private ResponseCodeEnum floatAndDivisibilityCheck(long tokenFloat, int divisibility) {
		if (tokenFloat < 1) {
			return INVALID_TOKEN_FLOAT;
		}
		if (tokenFloat * divisibility <= 0) {
			return INVALID_TOKEN_DIVISIBILITY;
		}
		return OK;
	}

	private ResponseCodeEnum symbolCheck(String symbol) {
		if (symbol.length() < 1 || symbol.length() > properties.maxTokenSymbolLength()) {
			return INVALID_TOKEN_SYMBOL;
		}
		return range(0, symbol.length()).mapToObj(symbol::charAt).allMatch(Character::isAlphabetic)
				? OK
				: INVALID_TOKEN_SYMBOL;
	}

	private ResponseCodeEnum treasuryCheck(AccountID id) {
		if (!accounts.contains(id) || accounts.getRef(id).isDeleted()) {
			return INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
		}
		return OK;
	}

	@Override
	public Optional<MerkleToken> lookup(TokenID id) {
		return Optional.ofNullable(tokens.get().get(fromTokenId(id)));
	}

	@Override
	public void commitCreation() {
		throwIfNoCreationPending();

		tokens.get().put(fromTokenId(pendingId), pendingCreation);
		resetPendingCreation();
	}

	@Override
	public void rollbackCreation() {
		throwIfNoCreationPending();

		ids.reclaimLastId();
		resetPendingCreation();
	}

	private void resetPendingCreation() {
		pendingId = NO_PENDING_ID;
		pendingCreation = null;
	}

	private void throwIfNoCreationPending() {
		if (pendingId == NO_PENDING_ID) {
			throw new IllegalStateException("No pending token creation!");
		}
	}
}
