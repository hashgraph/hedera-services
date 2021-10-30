package com.hedera.services.state.logic;

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

import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Screens transactions based on the capacity of the system to handle the gasLimit of the transaction
 */
@Singleton
public class ThrottleScreen {

	private final NetworkCtxManager networkCtxManager;

	@Inject
	public ThrottleScreen(
			NetworkCtxManager networkCtxManager
	) {
		this.networkCtxManager = networkCtxManager;
	}

	/**
	 * Applies screening to the transaction accessor
	 * @param accessor - the transaction accessor
	 * @return - {@link ResponseCodeEnum#OK} if the system has enough capacity to handle the transaction
	 * 	 * {@link ResponseCodeEnum#CONSENSUS_GAS_EXHAUSTED} if the transaction should be throttled
	 */
	public ResponseCodeEnum applyTo(TxnAccessor accessor) {
		return networkCtxManager.prepareForIncorporating(accessor);
	}
}
