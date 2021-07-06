package com.hedera.services.pricing;

/*-
 * ‌
 * Hedera Services API Fees
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

/**
 * Represents the three kinds of
 */
public enum ResourceProvider {
	NODE {
		@Override
		public String jsonKey() {
			return "nodedata";
		}

		@Override
		public int multiplier() {
			return 1;
		}
	},
	NETWORK {
		@Override
		public String jsonKey() {
			return "networkdata";
		}

		@Override
		public int multiplier() {
			return NETWORK_SIZE;
		}
	},
	SERVICE {
		@Override
		public String jsonKey() {
			return "servicedata";
		}

		@Override
		public int multiplier() {
			return NETWORK_SIZE;
		}
	};

	private static final int RELEASE_0160_NETWORK_SIZE = 20;
	private static final int NETWORK_SIZE = RELEASE_0160_NETWORK_SIZE;

	public abstract int multiplier();
	public abstract String jsonKey();
}
