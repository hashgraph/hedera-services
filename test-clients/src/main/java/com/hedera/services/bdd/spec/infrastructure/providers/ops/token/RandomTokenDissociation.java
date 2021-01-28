package com.hedera.services.bdd.spec.infrastructure.providers.ops.token;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.listeners.TokenAccountRegistryRel;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.Optional;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

public class RandomTokenDissociation implements OpProvider {
	private final RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels;

	public RandomTokenDissociation(RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels) {
		this.tokenRels = tokenRels;
	}

	private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
			ACCOUNT_IS_TREASURY,
			ACCOUNT_FROZEN_FOR_TOKEN,
			TOKEN_NOT_ASSOCIATED_TO_ACCOUNT,
			TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES
	);

	@Override
	public Optional<HapiSpecOperation> get() {
		var relToDissociate = tokenRels.getQualifying();
		if (relToDissociate.isEmpty()) {
			return Optional.empty();
		}

		var rel = relToDissociate.get();
		var divider = rel.indexOf("|");
		var account = rel.substring(0, divider);
		var token = rel.substring(divider + 1);
		var op = tokenDissociate(account, token)
				.hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
				.hasKnownStatusFrom(permissibleOutcomes);
		return Optional.of(op);
	}
}
