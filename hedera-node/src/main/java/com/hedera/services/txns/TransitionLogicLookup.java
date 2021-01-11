package com.hedera.services.txns;

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

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Provides logic to identify what {@link TransitionLogic} applies to the
 * active node and transaction context.
 *
 * @author Michael Tinker
 */
public class TransitionLogicLookup {
	private final Function<HederaFunctionality, List<TransitionLogic>> transitions;

	public TransitionLogicLookup(Function<HederaFunctionality, List<TransitionLogic>> transitions) {
		this.transitions = transitions;
	}

	/**
	 * Returns the {@link TransitionLogic}, if any, relevant to the given txn.
	 *
	 * @param txn the txn to find logic for.
	 * @return relevant transition logic, if it exists.
	 */
	public Optional<TransitionLogic> lookupFor(HederaFunctionality function, TransactionBody txn) {
		return Optional.ofNullable(transitions.apply(function))
				.flatMap(transitions -> from(transitions, txn));
	}

	private Optional<TransitionLogic> from(List<TransitionLogic> transitions, TransactionBody txn) {
		for (TransitionLogic candidate : transitions) {
			if (candidate.applicability().test(txn)) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}
}
