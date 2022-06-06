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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.swirlds.common.utility.CommonUtils;
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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_UPGRADE_HAS_BEEN_PREPARED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PREPARED_UPDATE_FILE_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED;

public class UpgradeSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(UpgradeSuite.class);

	public static final String pragmatism = "Think of the children!";
	public static final String poeticUpgradeLoc = "testfiles/poeticUpgrade.zip";
	public static final String heavyPoeticUpgradeLoc = "testfiles/heavyPoeticUpgrade.zip";

	public static void main(String... args) {
		new UpgradeSuite().runSuiteSync();
	}

	private final byte[] poeticUpgrade;
	private final byte[] heavyPoeticUpgrade;
	private final byte[] poeticUpgradeHash;
	private final byte[] heavyPoeticUpgradeHash;
	private final byte[] notEvenASha384Hash = "abcdefgh".getBytes(StandardCharsets.UTF_8);

	public static final String standardUpdateFile = "0.0.150";
	public static final String standardTelemetryFile = "0.0.159";

	public UpgradeSuite() {
		try {
			final var sha384 = MessageDigest.getInstance("SHA-384");
			poeticUpgrade = Files.readAllBytes(Paths.get(poeticUpgradeLoc));
			poeticUpgradeHash = sha384.digest(poeticUpgrade);
			heavyPoeticUpgrade = Files.readAllBytes(Paths.get(heavyPoeticUpgradeLoc));
			heavyPoeticUpgradeHash = sha384.digest(heavyPoeticUpgrade);
			log.info("Poetic upgrade hash: " + CommonUtils.hex(poeticUpgradeHash));
			log.info("Heavy poetic upgrade hash: " + CommonUtils.hex(heavyPoeticUpgradeHash));
		} catch (NoSuchAlgorithmException | IOException e) {
			throw new IllegalStateException("UpgradeSuite environment is unsuitable", e);
		}
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						precheckRejectsUnknownFreezeType(),
						freezeOnlyPrecheckRejectsInvalid(),
						freezeUpgradeValidationRejectsInvalid(),
						prepareUpgradeValidationRejectsInvalid(),
						telemetryUpgradeValidationRejectsInvalid(),
						canFreezeUpgradeWithPreparedUpgrade(),
						canTelemetryUpgradeWithValid(),
						freezeAbortIsIdempotent(),
				}
		);
	}

	private HapiApiSpec precheckRejectsUnknownFreezeType() {
		return defaultHapiSpec("PrejeckRejectsUnknownFreezeType")
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
						freezeUpgrade().startingIn(2).minutes()
								.withUpdateFile(standardUpdateFile)
								.havingHash(poeticUpgradeHash)
								.hasKnownStatus(NO_UPGRADE_HAS_BEEN_PREPARED),
						freezeUpgrade().startingIn(2).minutes()
								.havingHash(poeticUpgradeHash)
								.hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeUpgrade().startingIn(2).minutes()
								.withUpdateFile(standardUpdateFile)
								.hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY)
				);
	}

	private HapiApiSpec freezeAbortIsIdempotent() {
		return defaultHapiSpec("FreezeAbortIsIdempotent")
				.given().when().then(
						freezeAbort().hasKnownStatus(SUCCESS),
						freezeAbort().hasKnownStatus(SUCCESS)
				);
	}

	private HapiApiSpec prepareUpgradeValidationRejectsInvalid() {
		return defaultHapiSpec("PrepareUpgradeValidationRejectsInvalid")
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
						getFileContents(standardUpdateFile)
								.hasByteStringContents(ignore -> ByteString.copyFrom(poeticUpgrade)),
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
						telemetryUpgrade()
								.withUpdateFile(standardTelemetryFile)
								.havingHash(poeticUpgradeHash)
								.startingIn(-60).minutes()
								.hasPrecheck(FREEZE_START_TIME_MUST_BE_FUTURE),
						telemetryUpgrade()
								.withUpdateFile("0.0.149")
								.havingHash(poeticUpgradeHash)
								.startingIn(3).minutes()
								.hasPrecheck(FREEZE_UPDATE_FILE_DOES_NOT_EXIST),
						telemetryUpgrade().startingIn(3).minutes()
								.withUpdateFile(standardTelemetryFile)
								.havingHash(notEvenASha384Hash)
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
						freezeUpgrade()
								.startingIn(60).minutes()
								.withUpdateFile(standardTelemetryFile)
								.havingHash(poeticUpgradeHash)
								.hasKnownStatus(UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED),
						freezeUpgrade()
								.startingIn(60).minutes()
								.withUpdateFile(standardUpdateFile)
								.havingHash(heavyPoeticUpgradeHash)
								.hasKnownStatus(UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED),
						freezeUpgrade()
								.startingIn(60).minutes()
								.withUpdateFile(standardUpdateFile)
								.havingHash(poeticUpgradeHash),
						freezeAbort()
				);
	}

	private HapiApiSpec canTelemetryUpgradeWithValid() {
		return defaultHapiSpec("CanTelemetryUpgradeWithValid")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FREEZE_ADMIN, ONE_HUNDRED_HBARS))
				).when(
						fileUpdate(standardUpdateFile)
								.signedBy(FREEZE_ADMIN)
								.path(heavyPoeticUpgradeLoc)
								.payingWith(FREEZE_ADMIN),
						getFileContents(standardUpdateFile)
								.hasByteStringContents(ignore -> ByteString.copyFrom(heavyPoeticUpgrade))
				).then(
						telemetryUpgrade()
								.startingIn(60).minutes()
								.withUpdateFile(standardUpdateFile)
								.havingHash(heavyPoeticUpgradeHash)
				);
	}
}
