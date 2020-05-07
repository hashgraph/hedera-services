package com.hedera.services.bdd.suites.file.positive;

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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static org.junit.Assert.assertEquals;

public class CreateSuccessSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CreateSuccessSpec.class);

	public static void main(String... args) {
		new CreateSuccessSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						targetsAppear(),
				}
		);
	}

	private HapiApiSpec targetsAppear() {
		var lifetime = 100_000L;
		var approxExpiry = Instant.now().getEpochSecond() + lifetime;
		var contents = "SOMETHING".getBytes();
		var newWacl = listOf(SIMPLE, listOf(3), threshOf(1, 3));
		var newWaclSigs = newWacl.signedWith(
				sigs(
						ON,
						sigs(ON, ON, ON),
						sigs(OFF, OFF, ON)));

		return defaultHapiSpec("targetsAppear")
				.given(
						UtilVerbs.newKeyNamed("newWacl").shape(newWacl)
				).when(
						fileCreate("file")
								.contents(contents)
								.key("newWacl")
								.lifetime(lifetime)
								.signedBy(GENESIS, "newWacl")
								.sigControl(ControlForKey.forKey("newWacl", newWaclSigs))
				).then(
						QueryVerbs.getFileInfo("file")
								.hasDeleted(false)
								.hasWacl("newWacl")
								.hasExpiryPassing(expiry -> Math.abs(approxExpiry - expiry) < 3),
						QueryVerbs.getFileContents("file")
								.hasByteStringContents(ignore -> ByteString.copyFrom(contents))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
