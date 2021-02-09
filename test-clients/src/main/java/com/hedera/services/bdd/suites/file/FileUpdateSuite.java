package com.hedera.services.bdd.suites.file;

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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

/**
 * NOTE: 1. This test suite covers the test08UpdateFile() test scenarios from the legacy FileServiceIT test class after
 * the FileServiceIT class is removed since all other test scenarios in this class are already covered by test suites
 * under com.hedera.services.legacy.regression.suites.file and com.hedera.services.legacy.regression.suites.crpto.
 *
 * 2. While this class now provides minimal coverage for proto's FileUpdate transaction, we shall add more positive and
 * negative test scenarios to cover FileUpdate, such as missing (partial) keys for update, for update of expirationTime,
 * for modifying keys field, etc.
 *
 * We'll come back to add all missing test scenarios for this and other test suites once we are done with cleaning up
 * old test cases.
 */
public class FileUpdateSuite extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(FileUpdateSuite.class);

	public static void main(String... args) {
		new FileUpdateSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
    	        vanillaUpdateSucceeds(),
				updateFeesCompatibleWithCreates(),
				apiPermissionsChangeDynamically(),
		});
	}

	private HapiApiSpec apiPermissionsChangeDynamically() {
		return defaultHapiSpec("ApiPermissionsChangeDynamically")
				.given(
						cryptoCreate("civilian"),
						getFileContents(API_PERMISSIONS).logged(),
						tokenCreate("poc").payingWith("civilian")
				).when(
						fileUpdate(API_PERMISSIONS)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.erasingProps(Set.of("tokenCreate")),
						getFileContents(API_PERMISSIONS).logged()
				).then(
						tokenCreate("poc").payingWith("civilian").hasPrecheck(NOT_SUPPORTED),
						fileUpdate(API_PERMISSIONS)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("tokenCreate", "0-*")),
						tokenCreate("secondPoc").payingWith("civilian")
				);
	}

	private HapiApiSpec updateFeesCompatibleWithCreates() {
		long origLifetime = 100_000_000;
		final byte[] old2k = TxnUtils.randomUtf8Bytes(TxnUtils.BYTES_4K / 2);
		final byte[] new4k = TxnUtils.randomUtf8Bytes(TxnUtils.BYTES_4K);
		final byte[] new2k = TxnUtils.randomUtf8Bytes(TxnUtils.BYTES_4K / 2);

		return defaultHapiSpec("UpdateFeesCompatibleWithCreates")
				.given(
						fileCreate("test")
								.contents(old2k)
								.lifetime(origLifetime)
								.via("create")
				).when(
						fileUpdate("test")
								.contents(new4k)
								.extendingExpiryBy(0)
								.via("updateTo4"),
						fileUpdate("test")
								.contents(new2k)
								.extendingExpiryBy(0)
								.via("updateTo2"),
						fileUpdate("test")
								.extendingExpiryBy(origLifetime)
								.via("extend"),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("maxFileSize", "1025"))
								.via("special"),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("maxFileSize", "1024"))
				).then(
						UtilVerbs.withOpContext((spec, opLog) -> {
							var createOp = getTxnRecord("create");
							var to4kOp = getTxnRecord("updateTo4");
							var to2kOp = getTxnRecord("updateTo2");
							var extensionOp = getTxnRecord("extend");
							var specialOp = getTxnRecord("special");
							allRunFor(spec, createOp, to4kOp, to2kOp, extensionOp, specialOp);
							var createFee = createOp.getResponseRecord().getTransactionFee();
							opLog.info("Creation : " + createFee);
							opLog.info("New 4k   : " + to4kOp.getResponseRecord().getTransactionFee()
									+ " (" + (to4kOp.getResponseRecord().getTransactionFee() - createFee) + ")");
							opLog.info("New 2k   : " + to2kOp.getResponseRecord().getTransactionFee()
									+ " (" + (to2kOp.getResponseRecord().getTransactionFee() - createFee) + ")");
							opLog.info("Extension: " + extensionOp.getResponseRecord().getTransactionFee()
									+ " (" + (extensionOp.getResponseRecord().getTransactionFee() - createFee) + ")");
							opLog.info("Special: " + specialOp.getResponseRecord().getTransactionFee());
						})
				);
	}

	private HapiApiSpec vanillaUpdateSucceeds() {
		final byte[] old4K = TxnUtils.randomUtf8Bytes(TxnUtils.BYTES_4K);
		final byte[] new4k = TxnUtils.randomUtf8Bytes(TxnUtils.BYTES_4K);
		String firstMemo = "Originally";
		String secondMemo = "Subsequently";

		return defaultHapiSpec("VanillaUpdateSucceeds")
				.given(
						fileCreate("test")
								.entityMemo(firstMemo)
								.contents(old4K)
				).when(
						fileUpdate("test")
								.entityMemo(secondMemo)
								.contents(new4k)
				).then(
						getFileContents("test").hasContents(ignore -> new4k),
						getFileInfo("test").hasMemo(secondMemo)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
