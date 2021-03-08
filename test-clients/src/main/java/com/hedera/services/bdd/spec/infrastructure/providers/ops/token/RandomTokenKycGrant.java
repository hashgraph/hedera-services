package com.hedera.services.bdd.spec.infrastructure.providers.ops.token;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.listeners.TokenAccountRegistryRel;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.Optional;

import static com.hedera.services.bdd.spec.infrastructure.providers.ops.token.RandomTokenDissociation.explicit;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

public class RandomTokenKycGrant implements OpProvider {
	private final RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels;

	private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
			TOKEN_WAS_DELETED,
			TOKEN_HAS_NO_KYC_KEY,
			TOKEN_NOT_ASSOCIATED_TO_ACCOUNT
	);

	public RandomTokenKycGrant(RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels) {
		this.tokenRels = tokenRels;
	}

	@Override
	public Optional<HapiSpecOperation> get() {
		var relToGrant = tokenRels.getQualifying();
		if (relToGrant.isEmpty()) {
			return Optional.empty();
		}

		var implicitRel = relToGrant.get();
		var rel = explicit(implicitRel);
		var op = grantTokenKyc(rel.getRight(), rel.getLeft())
				.hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
				.hasKnownStatusFrom(permissibleOutcomes);
		return Optional.of(op);
	}
}
