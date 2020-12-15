package com.hedera.services.bdd.suites.misc;

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
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.*;
import static com.hedera.services.bdd.spec.keys.KeyShape.*;
import static com.hedera.services.bdd.spec.HapiApiSpec.*;

public class GuidedTourRemoteSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(GuidedTourRemoteSuite.class);

	public static void main(String... args) {
		new GuidedTourRemoteSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				guidedTour()
		);
	}

	private List<HapiApiSpec> guidedTour() {
		return Arrays.asList(
//				transferChangesBalance()
//				updateWithInvalidKeyFailsInPrecheck()
//				updateWithInvalidatedKeyFailsInHandle()
//				topLevelHederaKeyMustBeActive()
//				topLevelListBehavesAsRevocationService()
//				balanceLookupContractWorks()
		);
	}

	private HapiApiSpec balanceLookupContractWorks() {
		final long ACTUAL_BALANCE = 1_234L;

		return customHapiSpec("BalanceLookupContractWorks")
				.withProperties(Map.of("host", "34.74.191.8"))
				.given(
						cryptoCreate("targetAccount").balance(ACTUAL_BALANCE),
						fileCreate("bytecode").path(ContractResources.BALANCE_LOOKUP_BYTECODE_PATH),
						contractCreate("balanceLookup").bytecode("bytecode")
				).when().then(
						/* This contract (c.f. src/main/resource/solidity/BalanceLookup.sol) assumes
						   a shard and realm of 0; accepts just the sequence number of an account. */
						contractCallLocal(
								"balanceLookup",
								ContractResources.BALANCE_LOOKUP_ABI,
								spec -> new Object[] {
										spec.registry().getAccountID("targetAccount").getAccountNum()
								}
						).has(
								resultWith().resultThruAbi(
										ContractResources.BALANCE_LOOKUP_ABI,
										isLiteralResult(new Object[] { BigInteger.valueOf(ACTUAL_BALANCE) })
								)
						)
				);

	}

	private HapiApiSpec topLevelHederaKeyMustBeActive() {
		KeyShape waclShape = listOf(SIMPLE, threshOf(2, 3));
		SigControl updateSigControl = waclShape.signedWith(sigs(ON, sigs(ON, OFF, OFF)));

		return customHapiSpec("TopLevelListBehavesAsRevocationService")
				.withProperties(Map.of("host", "34.74.191.8"))
				.given().when().then(
						fileCreate("target")
								.waclShape(waclShape)
								.sigControl(ControlForKey.forKey("target", updateSigControl))
								.hasKnownStatus(INVALID_SIGNATURE)
				);
	}

	/* Feature is pending; top-level KeyList should allow deletion with an active
	   signature for any ONE of its child keys active. (I.e. a top-level KeyList
	   behaves as a revocation service.)

	   NOTE: KeyLists lower in the key hierarchy still require all child keys
	   to have active signatures.
	 */
	private HapiApiSpec topLevelListBehavesAsRevocationService() {
		KeyShape waclShape = listOf(SIMPLE, threshOf(2, 3));
		SigControl deleteSigControl = waclShape.signedWith(sigs(OFF, sigs(ON, ON, OFF)));

		return customFailingHapiSpec("TopLevelListBehavesAsRevocationService")
				.withProperties(Map.of("host", "34.74.191.8"))
				.given(
						fileCreate("target").waclShape(waclShape)
				).when().then(
						fileDelete("target")
								.sigControl(ControlForKey.forKey("target", deleteSigControl))
				);
	}

	private HapiApiSpec updateWithInvalidatedKeyFailsInHandle() {
		return customHapiSpec("UpdateWithInvalidatedKeyFailsIHandle")
				.withProperties(Map.of("host", "34.74.191.8"))
				.given(
						newKeyNamed("oldKey"),
						newKeyNamed("newKey"),
						cryptoCreate("target")
								.key("oldKey")
				) .when(
						cryptoUpdate("target")
								.key("newKey")
								.deferStatusResolution()
				).then(
						cryptoUpdate("target")
								.signedBy(GENESIS, "oldKey")
								.receiverSigRequired(true)
								.hasPrecheck(OK)
								.hasKnownStatus(INVALID_SIGNATURE)
				);
	}

	private HapiApiSpec updateWithInvalidKeyFailsInPrecheck() {
		KeyShape keyShape = listOf(3);

		return HapiApiSpec.customHapiSpec("UpdateWithInvalidKeyFailsInPrecheck")
				.withProperties(Map.of("host", "34.74.191.8"))
				.given(
						newKeyNamed("invalidPayerKey").shape(keyShape)
				) .when().then(
						cryptoUpdate(SYSTEM_ADMIN)
								.receiverSigRequired(true)
								.signedBy("invalidPayerKey")
								.hasPrecheck(INVALID_SIGNATURE)
				);
	}

	private HapiApiSpec transferChangesBalance() {
		final long AMOUNT = 1_000L;

		return HapiApiSpec.customHapiSpec("TransferChangesBalance")
				.withProperties(Map.of("host", "34.74.191.8"))
				.given(
						cryptoCreate("targetAccount").balance(0L)
				) .when(
						cryptoTransfer(
							tinyBarsFromTo(GENESIS, "targetAccount", AMOUNT)
						)
				).then(
						getAccountBalance("targetAccount").hasTinyBars(AMOUNT),
						getAccountInfo("targetAccount").logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
