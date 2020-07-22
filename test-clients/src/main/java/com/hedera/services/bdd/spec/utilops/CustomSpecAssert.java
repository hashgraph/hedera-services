package com.hedera.services.bdd.spec.utilops;

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

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.List;
import java.util.Optional;

public class CustomSpecAssert extends UtilOp {
	static final Logger log = LogManager.getLogger(CustomSpecAssert.class);

	public static void allRunFor(HapiApiSpec spec, List<HapiSpecOperation> ops) {
		for (HapiSpecOperation op : ops) {
			Optional<Throwable>	error = op.execFor(spec);
			if (error.isPresent()) {
				Assert.fail("Operation '" + op.toString() + "' :: " + error.get().getMessage());
				error.get().printStackTrace();
			}
		}
	}

	public static void allRunFor(HapiApiSpec spec, HapiSpecOperation... ops) {
		allRunFor(spec, List.of(ops));
	}

	@FunctionalInterface
	public interface ThrowingConsumer {
		void assertFor(HapiApiSpec spec, Logger assertLog) throws Throwable;
	}

	private final ThrowingConsumer custom;

	public CustomSpecAssert(ThrowingConsumer custom) {
		this.custom = custom;
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		custom.assertFor(spec, log);
		return false;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).toString();
	}
}
