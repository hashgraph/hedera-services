package com.hedera.services.bdd.spec.infrastructure.providers.ops.token;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;

import java.util.Optional;

public class RandomTokenFreeze implements OpProvider {
	@Override
	public Optional<HapiSpecOperation> get() {
		return Optional.empty();
	}
}
