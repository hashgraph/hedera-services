package com.hedera.services.bdd.suites.freeze;


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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.swirlds.common.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeAbort;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.prepareUpgrade;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_START_TIME_MUST_BE_FUTURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_UPDATE_FILE_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_FREEZE_IS_SCHEDULED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_UPGRADE_HAS_BEEN_PREPARED;

public class UpgradeSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(UpgradeSuite.class);

	private final String pragmatism = "Think of the children!";
	private final String poeticUpgradeLoc = "testfiles/poeticUpgrade.zip";

	public static void main(String... args) {
		new UpgradeSuite().runSuiteSync();
	}

	private final byte[] poeticUpgradeHash;

	private final String canonicalUpdateFile = "0.0.150";

	public UpgradeSuite() {
		try {
			final var sha384 = MessageDigest.getInstance("SHA-384");
			poeticUpgradeHash = sha384.digest(Files.readAllBytes(Paths.get(poeticUpgradeLoc)));
			log.info("Poetic upgrade hash: " + CommonUtils.hex(poeticUpgradeHash));
		} catch (NoSuchAlgorithmException | IOException e) {
			throw new IllegalStateException("UpgradeSuite environment is unsuitable", e);
		}
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						/* Negative tests */
						freezeOnlyPrecheckRejectsInvalid(),
						freezeUpgradeValidationRejectsInvalid(),
						freezeAbortValidationRejectsInvalid(),
						freezeUpgradeValidationRejectsInvalid(),
						prepareUpgradeValidationRejectsInvalid(),

						/* Happy paths */
						canFreezeUpgradeWithPreparedUpgrade(),
				}
		);
	}

	private HapiApiSpec freezeOnlyPrecheckRejectsInvalid() {
		return defaultHapiSpec("freezeOnlyPrecheckRejectsInvalid")
				.given().when().then(
						freezeOnly().withRejectedStartHr().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeOnly().withRejectedStartMin().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeOnly().withRejectedEndHr().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeOnly().withRejectedEndMin().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeOnly().startingIn(-60).minutes().hasPrecheck(FREEZE_START_TIME_MUST_BE_FUTURE)
				);
	}

	private HapiApiSpec freezeUpgradeValidationRejectsInvalid() {
		return defaultHapiSpec("freezeUpgradeValidationRejectsInvalid")
				.given().when().then(
						freezeUpgrade().withRejectedStartHr().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeUpgrade().withRejectedStartMin().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeUpgrade().withRejectedEndHr().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeUpgrade().withRejectedEndMin().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeUpgrade().startingIn(-60).minutes().hasPrecheck(FREEZE_START_TIME_MUST_BE_FUTURE),
						freezeUpgrade().startingIn(2).minutes().hasKnownStatus(NO_UPGRADE_HAS_BEEN_PREPARED)
				);
	}

	private HapiApiSpec freezeAbortValidationRejectsInvalid() {
		return defaultHapiSpec("FreezeAbortValidationRejectsInvalid")
				.given().when().then(
						freezeAbort().hasKnownStatus(NO_FREEZE_IS_SCHEDULED)
				);
	}

	private HapiApiSpec prepareUpgradeValidationRejectsInvalid() {
		return defaultHapiSpec("freezeUpgradeValidationRejectsInvalid")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FREEZE_ADMIN, ONE_HUNDRED_HBARS)),
						prepareUpgrade().withUpdateFile("0.0.149")
								.hasPrecheck(FREEZE_UPDATE_FILE_DOES_NOT_EXIST),
						prepareUpgrade().withUpdateFile(canonicalUpdateFile)
								.hasPrecheck(FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH)
				).when(
						fileUpdate(canonicalUpdateFile)
								.signedBy(FREEZE_ADMIN)
								.contents(pragmatism)
								.payingWith(FREEZE_ADMIN)
				).then(
						prepareUpgrade()
								.withUpdateFile(canonicalUpdateFile)
								.havingHash(poeticUpgradeHash)
								.hasKnownStatus(FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH)
				);
	}

	private HapiApiSpec canFreezeUpgradeWithPreparedUpgrade() {
		return defaultHapiSpec("CanFreezeUpgradeWithPreparedUpgrade")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FREEZE_ADMIN, ONE_HUNDRED_HBARS)),
						fileUpdate(canonicalUpdateFile)
								.signedBy(FREEZE_ADMIN)
								.path(poeticUpgradeLoc)
								.payingWith(FREEZE_ADMIN)
				).when(
						prepareUpgrade()
								.withUpdateFile(canonicalUpdateFile)
								.havingHash(poeticUpgradeHash)
				).then(
						freezeUpgrade().startingIn(60).minutes(),
						freezeAbort()
				);
	}
}
