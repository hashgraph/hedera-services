package com.hedera.services.store;

/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.hedera.services.store.tokens.HederaTokenStore;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Utility class which encapsulates the operations related to the knownTreasuries map in {@link HederaTokenStore}.
 * 
 * @author Yoan Sredkov
 */
@Singleton
public class KnownTreasuriesDelegator {
	
	private final LegacyTreasuryAdder adder;
	private final LegacyTreasuryRemover remover;
	private final LegacyTreasuryChecker checker;

	@Inject
	public KnownTreasuriesDelegator(
			final LegacyTreasuryAdder adder,
			final LegacyTreasuryRemover remover,
			final LegacyTreasuryChecker checker
	) {
		this.adder = adder;
		this.remover = remover;
		this.checker = checker;
	}

	public boolean performCheck(AccountID id) {
		return checker.perform(id);
	}
	
	public void performRemoval(final AccountID aId, final  TokenID tId) {
		remover.perform(aId, tId);
	}
	
	public void performInsertion(final AccountID aId, final  TokenID tId) {
		adder.perform(aId, tId);
	}

	@FunctionalInterface
	public interface LegacyTreasuryAdder {
		void perform(final AccountID aId, final TokenID tId);
	}

	@FunctionalInterface
	public interface LegacyTreasuryRemover {
		void perform(final AccountID aId, final TokenID tId);
	}

	@FunctionalInterface
	public interface LegacyTreasuryChecker {
		boolean perform(AccountID aId);
	}
}
