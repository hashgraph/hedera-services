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

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;


/**
 * Defines a type able to manage arbitrary tokens.
 *
 * @author Michael Tinker
 */
public interface TokenStore {
	void setLedger(TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger);

	boolean exists(TokenID id);
	MerkleToken get(TokenID id);

	ResponseCodeEnum freeze(AccountID aId, TokenID tId);
	ResponseCodeEnum unfreeze(AccountID aId, TokenID tId);
	ResponseCodeEnum adjustBalance(AccountID aId, TokenID tId, long adjustment);

	TokenCreationResult createProvisionally(TokenCreation request, AccountID sponsor);
	void commitCreation();
	void rollbackCreation();
	boolean isCreationPending();
}
