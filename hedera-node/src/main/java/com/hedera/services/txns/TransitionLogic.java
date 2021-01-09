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

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Defines a type that can perform a specific kind of state transition
 * within the active node and transaction context, under two conditions:
 * 	 <ol>
 * 	     <li> Its {@code applicability} predicate evaluates to {@code true}
 * 	 	against the active transaction. </li>
 * 	    <li> Its {@code syntaxCheck} function evaluates to {@code OK}
 * 	    against the active transaction.</li>
 * 	 </ol>
 *
 * The context injects the {@link TransitionLogicLookup} with all
 * implementations of this type, so it is simple for the {@link ProcessLogic}
 * to find the right state transition after it validates that signing,
 * fee, and other generic prerequisites have been met.
 *
 * <b>NOTE:</b>	There is no strict contract on whether the syntax check
 * requires consensus to have been reached. However, it is recommended to
 * make the syntax check evaluate only conditions which are known
 * pre-consensus. Ultimately we may move all syntax checking outside the
 * {@link ProcessLogic}.
 *
 * @author Michael Tinker
 */
public interface TransitionLogic {
	Function<TransactionBody, ResponseCodeEnum> SYNTAX_RUBBER_STAMP = ignore -> OK;

	/**
	 * Mutates the active state based on the active node and transaction context.
	 *
	 * @throws RuntimeException if the txn <i>semantics</i> were invalid.
	 */
	void doStateTransition();

	/**
	 * Provides the test for applicability of this transition logic.
	 *
 	 * @return an applicability predicate.
	 */
	Predicate<TransactionBody> applicability();

	/**
	 * Provides the validator for an applicable txn.
	 *
	 * @return a syntax check functional.
	 */
	default Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_RUBBER_STAMP;
	}
}
