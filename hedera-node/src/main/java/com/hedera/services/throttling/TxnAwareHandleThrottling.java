package com.hedera.services.throttling;

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
import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;

import java.util.List;

public class TxnAwareHandleThrottling implements FunctionalityThrottling {
	private final TransactionContext txnCtx;
	private final TimedFunctionalityThrottling delegate;

	public TxnAwareHandleThrottling(TransactionContext txnCtx, TimedFunctionalityThrottling delegate) {
		this.txnCtx = txnCtx;
		this.delegate = delegate;
	}

	@Override
	public boolean shouldThrottle(HederaFunctionality function) {
		return delegate.shouldThrottle(function, txnCtx.consensusTime());
	}

	@Override
	public List<DeterministicThrottle> activeThrottlesFor(HederaFunctionality function) {
		return delegate.activeThrottlesFor(function);
	}

	@Override
	public List<DeterministicThrottle> allActiveThrottles() {
		return delegate.allActiveThrottles();
	}

	@Override
	public void rebuildFor(ThrottleDefinitions defs) {
		delegate.rebuildFor(defs);
	}
}
