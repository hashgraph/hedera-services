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

import com.hederahashgraph.api.proto.java.FeeComponents;

import java.util.function.ToLongFunction;

public enum UsableResource {
	CONSTANT {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getConstant;
		}
	},
	BPT {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getBpt;
		}
	},
	VPT {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getVpt;
		}
	},
	RBH {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getRbh;
		}
	},
	SBH {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getSbh;
		}
	},
	GAS {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getGas;
		}
	},
	BPR {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getBpr;
		}
	},
	SBPR {
		@Override
		public ToLongFunction<FeeComponents> getter() {
			return FeeComponents::getSbpr;
		}
	};

	public abstract ToLongFunction<FeeComponents> getter();
}
