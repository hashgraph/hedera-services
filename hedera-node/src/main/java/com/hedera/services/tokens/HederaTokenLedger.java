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
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreate;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.fcmap.FCMap;

import java.util.function.Supplier;

import static com.hedera.services.tokens.TokenCreationResult.failure;
import static com.hedera.services.tokens.TokenCreationResult.success;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;

/**
 * Provides a ledger for arbitrary tokens.
 *
 * @author Michael Tinker
 */
public class HederaTokenLedger implements TokenLedger {
	private final EntityIdSource ids;
	private final GlobalDynamicProperties properties;
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;

	public HederaTokenLedger(
			EntityIdSource ids,
			GlobalDynamicProperties properties,
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens
	) {
		this.ids = ids;
		this.tokens = tokens;
		this.properties = properties;
	}

	@Override
	public TokenCreationResult create(TokenCreation request, AccountID sponsor) {
		var adminKey = asUsableFcKey(request.getAdminKey());
		if (adminKey.isEmpty())	{
			return failure(INVALID_ADMIN_KEY);
		}

		var created = ids.newTokenId(sponsor);
		return success(created);
	}
}
