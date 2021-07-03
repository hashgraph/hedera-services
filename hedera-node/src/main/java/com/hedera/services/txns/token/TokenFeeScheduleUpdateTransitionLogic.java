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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.function.Predicate;

public class TokenFeeScheduleUpdateTransitionLogic implements TransitionLogic {

	private final TypedTokenStore tokenStore;
	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final GlobalDynamicProperties dynamicProperties;

	public TokenFeeScheduleUpdateTransitionLogic(final TypedTokenStore tokenStore, final TransactionContext txnCtx,
			final OptionValidator validator, final GlobalDynamicProperties dynamicProperties) {
		this.tokenStore = tokenStore; // we can change this to HederaTokenStore for simplicity and refactor later.
		this.txnCtx = txnCtx;
		this.validator = validator;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public void doStateTransition() {
		
		/* If want to use Model objects  instead of grpc types*/
		// covert from grpc types
		// load the token
		// do businessLogic
		// persist the token
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return null;
	}
}
