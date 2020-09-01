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
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.TokenScopedPropertyValue;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fcmap.FCMap;

import java.util.Optional;
import java.util.function.Supplier;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.IS_FROZEN;
import static com.hedera.services.state.merkle.MerkleEntityId.fromTokenId;
import static com.hedera.services.tokens.TokenCreationResult.failure;
import static com.hedera.services.tokens.TokenCreationResult.success;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hedera.services.utils.MiscUtils.uncheckedSha384Hash;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DIVISIBILITY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_FLOAT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
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
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;

	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger;

	TokenID pendingId = NO_PENDING_ID;
	MerkleToken pendingCreation;

	public HederaTokenStore(
			EntityIdSource ids,
			GlobalDynamicProperties properties,
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens
	) {
		this.ids = ids;
		this.tokens = tokens;
		this.properties = properties;
	}

	@Override
	public boolean isCreationPending() {
		return pendingId != NO_PENDING_ID;
	}

	@Override
	public void setLedger(TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger) {
		this.ledger = ledger;
	}

	@Override
	public boolean exists(TokenID id) {
		return pendingId.equals(id) || tokens.get().containsKey(fromTokenId(id));
	}

	@Override
	public MerkleToken get(TokenID id) {
		throwIfMissing(id);

		return pendingId.equals(id) ? pendingCreation : tokens.get().get(fromTokenId(id));
	}

	@Override
	public ResponseCodeEnum unfreeze(AccountID aId, TokenID tId) {
		return setIsFrozen(aId, tId, false);
	}

	@Override
	public ResponseCodeEnum freeze(AccountID aId, TokenID tId) {
		return setIsFrozen(aId, tId, true);
	}

	private ResponseCodeEnum setIsFrozen(
			AccountID aId,
			TokenID tId,
			boolean value
	) {
		var validity = checkExistence(aId, tId);
		if (validity != OK) {
			return validity;
		}

		var token = get(tId);
		if (token.freezeKey().isEmpty()) {
			return value ? TOKEN_HAS_NO_FREEZE_KEY : OK;
		}

		var account = ledger.getTokenRef(aId);
		if (!account.hasRelationshipWith(tId)
				&& saturated(account)
				&& token.accountsAreFrozenByDefault() != value) {
			return TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
		}

		var scopedFreeze = new TokenScopedPropertyValue(tId, token, value);
		ledger.set(aId, IS_FROZEN, scopedFreeze);
		return OK;
	}

	@Override
	public ResponseCodeEnum adjustBalance(AccountID aId, TokenID tId, long adjustment) {
		var validity = checkExistence(aId, tId);
		if (validity != OK) {
			return validity;
		}

		var account = ledger.getTokenRef(aId);
		if (!unsaturated(account) && !account.hasRelationshipWith(tId)) {
			return TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
		}

		var token = get(tId);
		validity = account.validityOfAdjustment(tId, token, adjustment);
		if (validity != OK) {
			return validity;
		}

		var scopedAdjustment = new TokenScopedPropertyValue(tId, token, adjustment);
		ledger.set(aId, BALANCE, scopedAdjustment);
		return OK;
	}

	private ResponseCodeEnum checkExistence(AccountID aId, TokenID tId) {
		var validity = ledger.exists(aId) ? OK : INVALID_ACCOUNT_ID;
		if (validity != OK) {
			return validity;
		}
		return exists(tId) ? OK : INVALID_TOKEN_ID;
	}

	private boolean unsaturated(MerkleAccount account) {
		return account.numTokenRelationships() < properties.maxTokensPerAccount();
	}

	private boolean saturated(MerkleAccount account) {
		return account.numTokenRelationships() >= properties.maxTokensPerAccount();
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
		var freezeKey = asUsableFcKey(request.getFreezeKey());
		validity = freezeSemanticsCheck(freezeKey, request.getFreezeDefault());
		if (validity != OK) {
			return failure(validity);
		}

		pendingId = ids.newTokenId(sponsor);
		pendingCreation = new MerkleToken(
			request.getFloat(),
			request.getDivisibility(),
			adminKey.get(),
				request.getSymbol(),
				request.getFreezeDefault(),
				EntityId.ofNullableAccountId(request.getTreasury()));
		freezeKey.ifPresent(pendingCreation::setFreezeKey);

		return success(pendingId);
	}

	private ResponseCodeEnum freezeSemanticsCheck(Optional<JKey> candidate, boolean freezeDefault) {
		if (candidate.isEmpty() && freezeDefault) {
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
		if (!ledger.exists(id) || (boolean)ledger.get(id, AccountProperty.IS_DELETED)) {
			return INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
		}
		return OK;
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

	private void throwIfMissing(TokenID id) {
		if (!exists(id)) {
			throw new IllegalArgumentException(String.format("No such token '%s'!", readableId(id)));
		}
	}
}
