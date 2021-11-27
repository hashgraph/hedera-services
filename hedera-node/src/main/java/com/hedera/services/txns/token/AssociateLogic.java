package com.hedera.services.txns.token;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.TokenID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Singleton
public class AssociateLogic {
	private final TypedTokenStore tokenStore;
	private final AccountStore accountStore;
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public AssociateLogic(final TypedTokenStore tokenStore,
						  final AccountStore accountStore,
						  final GlobalDynamicProperties dynamicProperties) {
		this.tokenStore = tokenStore;
		this.accountStore = accountStore;
		this.dynamicProperties = dynamicProperties;
	}

	public void associate(Id accountId, List<TokenID> tokensList) {
		final var tokenIds = tokensList
				.stream()
				.map(id -> new Id(id.getShardNum(), id.getRealmNum(), id.getTokenNum()))
				.collect(toList());

		/* Load the models */
		final var account = accountStore.loadAccount(accountId);
		final var tokens = tokenIds.stream().map(tokenStore::loadToken).collect(toList());

		/* Associate and commit the changes */
		account.associateWith(tokens, dynamicProperties.maxTokensPerAccount(), false);

		accountStore.commitAccount(account);

		tokens.forEach(token -> tokenStore
				.commitTokenRelationships(List.of(token.newRelationshipWith(account, false))));
	}
}
