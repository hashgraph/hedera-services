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

import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Provides logic to identify what {@link TransitionLogic} applies to the
 * active node and transaction context.
 *
 * @author Michael Tinker
 */
public class TransitionLogicLookup {
	private final TransitionLogic[] logics;
	private final List<Predicate<TransactionBody>> relevance;

	public TransitionLogicLookup(TransitionLogic... logics) {
		this.logics = logics;
		this.relevance = Stream.of(logics).map(TransitionLogic::applicability).collect(toList());
	}

	/**
	 * Returns the {@link TransitionLogic}, if any, relevant to the given txn.
	 *
	 * @param txn the txn to find logic for.
	 * @return relevant transition logic, if it exists.
	 */
	public Optional<TransitionLogic> lookupFor(TransactionBody txn) {
		return IntStream.range(0, logics.length)
				.filter(i -> relevance.get(i).test(txn))
				.boxed()
				.findAny()
				.map(i -> logics[i]);
	}
}
