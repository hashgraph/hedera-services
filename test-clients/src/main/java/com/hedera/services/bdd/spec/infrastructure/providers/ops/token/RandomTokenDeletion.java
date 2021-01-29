package com.hedera.services.bdd.spec.infrastructure.providers.ops.token;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.Optional;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

public class RandomTokenDeletion implements OpProvider {
	private final RegistrySourcedNameProvider<TokenID> tokens;

	private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
			TOKEN_IS_IMMUTABLE,
			TOKEN_WAS_DELETED
	);

	public RandomTokenDeletion(RegistrySourcedNameProvider<TokenID> tokens) {
		this.tokens = tokens;
	}

	@Override
	public Optional<HapiSpecOperation> get() {
		var target = tokens.getQualifying();
		if (target.isEmpty()) {
			return Optional.empty();
		}

		var op = tokenDelete(target.get())
				.hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
				.hasKnownStatusFrom(permissibleOutcomes);
		return Optional.of(op);
	}
}
