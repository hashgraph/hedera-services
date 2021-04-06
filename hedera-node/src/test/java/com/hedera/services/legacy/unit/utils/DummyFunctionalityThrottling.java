package com.hedera.services.legacy.unit.utils;

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
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hederahashgraph.api.proto.java.HederaFunctionality;

import java.util.Collections;
import java.util.List;

public class DummyFunctionalityThrottling {
	public static FunctionalityThrottling throttlingAlways(boolean shouldThrottle) {
		return new FunctionalityThrottling() {
			@Override
			public boolean shouldThrottle(HederaFunctionality function) {
				return shouldThrottle;
			}

			@Override
			public void rebuildFor(ThrottleDefinitions defs) {
			}

			@Override
			public List<DeterministicThrottle> activeThrottlesFor(HederaFunctionality function) {
				return Collections.emptyList();
			}

			@Override
			public List<DeterministicThrottle> allActiveThrottles() {
				return Collections.emptyList();
			}
		};
	}
}
