package com.hedera.services.bdd.spec.utilops.grouping;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.UtilOp;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;

public class InBlockingOrder extends UtilOp {
	private final HapiSpecOperation[] ops;

	public InBlockingOrder(HapiSpecOperation... ops) {
		this.ops = ops;
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) {
		allRunFor(spec, ops);
		return false;
	}
}
