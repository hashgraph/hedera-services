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

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenManagement;
import com.hederahashgraph.api.proto.java.TokenRef;

import java.util.function.Consumer;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_REF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;


/**
 * Defines a type able to manage arbitrary tokens.
 *
 * @author Michael Tinker
 */
public interface TokenStore {
	TokenID MISSING_TOKEN = TokenID.getDefaultInstance();
	Consumer<MerkleToken> DELETION = token -> token.setDeleted(true);

	void setLedger(TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger);
	void setHederaLedger(HederaLedger ledger);

	void apply(TokenID id, Consumer<MerkleToken> change);
	boolean exists(TokenID id);
	boolean symbolExists(String symbol);
	TokenID lookup(String symbol);
	MerkleToken get(TokenID id);

	ResponseCodeEnum burn(TokenID tId, long amount);
	ResponseCodeEnum mint(TokenID tId, long amount);
	ResponseCodeEnum wipe(AccountID aId, TokenID tId, boolean skipKeyCheck);
	ResponseCodeEnum freeze(AccountID aId, TokenID tId);
	ResponseCodeEnum update(TokenManagement changes);
	ResponseCodeEnum unfreeze(AccountID aId, TokenID tId);
	ResponseCodeEnum grantKyc(AccountID aId, TokenID tId);
	ResponseCodeEnum revokeKyc(AccountID aId, TokenID tId);
	ResponseCodeEnum adjustBalance(AccountID aId, TokenID tId, long adjustment);

	TokenCreationResult createProvisionally(TokenCreation request, AccountID sponsor);
	void commitCreation();
	void rollbackCreation();
	boolean isCreationPending();

	default TokenID resolve(TokenRef ref) {
		String symbol;
		TokenID id;
		if (ref.hasTokenId()) {
			return exists(id = ref.getTokenId()) ? id : MISSING_TOKEN;
		} else {
			return symbolExists(symbol = ref.getSymbol()) ? lookup(symbol) : MISSING_TOKEN;
		}
	}

	default ResponseCodeEnum delete(TokenRef ref) {
		var id = resolve(ref);
		if (id == MISSING_TOKEN) {
			return INVALID_TOKEN_REF;
		}
		apply(id, DELETION);
		return OK;
	}
}
