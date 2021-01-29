package com.hedera.services.bdd.spec.utilops;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;

import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;

public class SourcedOp extends UtilOp {
	private final Supplier<HapiSpecOperation> source;

	public SourcedOp(Supplier<HapiSpecOperation> source) {
		this.source = source;
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		allRunFor(spec, source.get());
		return false;
	}
}
