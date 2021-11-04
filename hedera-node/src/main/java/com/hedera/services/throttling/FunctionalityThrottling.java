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

import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitBucketThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;

import java.util.List;

public interface FunctionalityThrottling {

	/**
	 * Verifies if the frontend/consensus throttle has enough capacity to handle the transaction
	 * @param accessor - the transaction accessor
	 * @return true if the transaction should be throttled, false if the system can handle the TX execution
	 */
	boolean shouldThrottleTxn(TxnAccessor accessor);

	boolean shouldThrottleQuery(HederaFunctionality queryFunction, Query query);

	/**
	 * Releases previously reserved gas from the consensus throttle
	 * @param value - the amount of gas to release
	 */
	void leakUnusedGasPreviouslyReserved(long value);
	void rebuildFor(ThrottleDefinitions defs);
	void applyGasConfig();
	List<DeterministicThrottle> activeThrottlesFor(HederaFunctionality function);
	List<DeterministicThrottle> allActiveThrottles();
	GasLimitDeterministicThrottle gasLimitThrottle();
}
