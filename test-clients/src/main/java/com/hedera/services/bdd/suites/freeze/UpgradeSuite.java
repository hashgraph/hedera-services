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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeAbort;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.prepareUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.telemetryUpgrade;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_START_TIME_MUST_BE_FUTURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_UPDATE_FILE_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_UPGRADE_IN_PROGRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_FREEZE_IS_SCHEDULED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_UPGRADE_HAS_BEEN_PREPARED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PREPARED_UPDATE_FILE_IS_IMMUTABLE;

public class UpgradeSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(UpgradeSuite.class);

	private final String pragmatism = "Think of the children!";
	private final String poeticUpgradeLoc = "testfiles/poeticUpgrade.zip";

	public static void main(String... args) {
		new UpgradeSuite().runSuiteSync();
	}

	private final byte[] poeticUpgradeHash;
	private final byte[] notEvenASha384Hash = "abcdefgh".getBytes(StandardCharsets.UTF_8);

	private final String standardUpdateFile = "0.0.150";
	private final String standardTelemetryFile = "0.0.159";

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
//						precheckRejectsUnknownFreezeType(),
//						freezeOnlyPrecheckRejectsInvalid(),
//						freezeUpgradeValidationRejectsInvalid(),
//						freezeAbortValidationRejectsInvalid(),
						prepareUpgradeValidationRejectsInvalid(),
//						telemetryUpgradeValidationRejectsInvalid(),

						/* Happy paths */
//						canFreezeUpgradeWithPreparedUpgrade(),
				}
		);
	}

	private HapiApiSpec precheckRejectsUnknownFreezeType() {
		return defaultHapiSpec("PrejectRejectsUnknownFreezeType")
				.given().when().then(
						freeze().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY)
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
						fileUpdate(standardUpdateFile)
								.signedBy(FREEZE_ADMIN)
								.contents(pragmatism)
								.payingWith(FREEZE_ADMIN),
						cryptoTransfer(tinyBarsFromTo(GENESIS, FREEZE_ADMIN, ONE_HUNDRED_HBARS)),
						prepareUpgrade()
								.withUpdateFile("0.0.149")
								.havingHash(poeticUpgradeHash)
								.hasPrecheck(FREEZE_UPDATE_FILE_DOES_NOT_EXIST),
						prepareUpgrade()
								.withUpdateFile(standardUpdateFile)
								.havingHash(notEvenASha384Hash)
								.hasPrecheck(FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH),
						prepareUpgrade()
								.withUpdateFile(standardUpdateFile)
								.havingHash(poeticUpgradeHash)
								.hasKnownStatus(FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH)
				).when(
						fileUpdate(standardUpdateFile)
								.signedBy(FREEZE_ADMIN)
								.contents(pragmatism)
								.payingWith(FREEZE_ADMIN)
				).then(
						prepareUpgrade()
								.withUpdateFile(standardUpdateFile)
								.havingHash(poeticUpgradeHash)
								.hasKnownStatus(FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH),
						fileUpdate(standardUpdateFile)
								.signedBy(FREEZE_ADMIN)
								.path(poeticUpgradeLoc)
								.payingWith(FREEZE_ADMIN),
						prepareUpgrade()
								.withUpdateFile(standardUpdateFile)
								.havingHash(poeticUpgradeHash),
						prepareUpgrade()
								.withUpdateFile(standardUpdateFile)
								.havingHash(poeticUpgradeHash)
								.hasKnownStatus(FREEZE_UPGRADE_IN_PROGRESS),
						freezeOnly().startingIn(60).minutes()
								.hasKnownStatus(FREEZE_UPGRADE_IN_PROGRESS),
						telemetryUpgrade()
								.withUpdateFile(standardUpdateFile)
								.havingHash(poeticUpgradeHash)
								.startingIn(60)
								.minutes()
								.hasKnownStatus(FREEZE_UPGRADE_IN_PROGRESS),
						fileUpdate(standardUpdateFile)
								.signedBy(FREEZE_ADMIN)
								.path(poeticUpgradeLoc)
								.payingWith(FREEZE_ADMIN)
								.hasKnownStatus(PREPARED_UPDATE_FILE_IS_IMMUTABLE),
						fileAppend(standardUpdateFile)
								.signedBy(FREEZE_ADMIN)
								.path(poeticUpgradeLoc)
								.payingWith(FREEZE_ADMIN)
								.hasKnownStatus(PREPARED_UPDATE_FILE_IS_IMMUTABLE),
						freezeAbort()
				);
	}

	private HapiApiSpec telemetryUpgradeValidationRejectsInvalid() {
		return defaultHapiSpec("TelemetryUpgradeValidationRejectsInvalid")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FREEZE_ADMIN, ONE_HUNDRED_HBARS)),
						telemetryUpgrade().startingIn(-60).minutes()
								.hasPrecheck(FREEZE_START_TIME_MUST_BE_FUTURE),
						telemetryUpgrade().startingIn(3).minutes().withUpdateFile("0.0.149")
								.hasPrecheck(FREEZE_UPDATE_FILE_DOES_NOT_EXIST),
						telemetryUpgrade().startingIn(3).minutes().withUpdateFile(standardTelemetryFile)
								.hasPrecheck(FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH)
				).when(
						fileUpdate(standardTelemetryFile)
								.signedBy(FREEZE_ADMIN)
								.contents(pragmatism)
								.payingWith(FREEZE_ADMIN)
				).then(
						telemetryUpgrade().startingIn(3).minutes()
								.withUpdateFile(standardTelemetryFile)
								.havingHash(poeticUpgradeHash)
								.hasKnownStatus(FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH)
				);
	}

	private HapiApiSpec canFreezeUpgradeWithPreparedUpgrade() {
		return defaultHapiSpec("CanFreezeUpgradeWithPreparedUpgrade")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FREEZE_ADMIN, ONE_HUNDRED_HBARS)),
						fileUpdate(standardUpdateFile)
								.signedBy(FREEZE_ADMIN)
								.path(poeticUpgradeLoc)
								.payingWith(FREEZE_ADMIN)
				).when(
						prepareUpgrade()
								.withUpdateFile(standardUpdateFile)
								.havingHash(poeticUpgradeHash)
				).then(
						freezeUpgrade().startingIn(60).minutes(),
						freezeAbort()
				);
	}
}
