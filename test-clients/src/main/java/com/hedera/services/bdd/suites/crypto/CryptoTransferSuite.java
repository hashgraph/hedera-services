package com.hedera.services.bdd.suites.crypto;

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

import static com.hedera.services.bdd.spec.HapiPropertySource.asTopicString;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;

import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.*;

import static com.hedera.services.bdd.spec.keys.ControlForKey.*;

import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;

public class CryptoTransferSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoTransferSuite.class);

	public static void main(String... args) {
		new CryptoTransferSuite().runSuiteAsync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						vanillaTransferSucceeds(),
						complexKeyAcctPaysForOwnTransfer(),
						twoComplexKeysRequired(),
						specialAccountsBalanceCheck(),
						transferToTopicReturnsInvalidAccountId(),
				}
		);
	}

	private HapiApiSpec transferToTopicReturnsInvalidAccountId() {
		AtomicReference<String> invalidAccountId = new AtomicReference<>();

		return defaultHapiSpec("TransferToTopicReturnsInvalidAccountId")
				.given(
						tokenCreate("token"),
						createTopic("something"),
						withOpContext((spec, opLog) -> {
							var topicId = spec.registry().getTopicID("something");
							invalidAccountId.set(asTopicString(topicId));
						})
				).when().then(
						cryptoTransfer(spec -> tinyBarsFromTo(DEFAULT_PAYER, invalidAccountId.get(), 1L).apply((spec)))
								.hasKnownStatus(INVALID_ACCOUNT_ID),
						cryptoTransfer(moving(1, "token")
								.between(spec -> DEFAULT_PAYER, spec -> invalidAccountId.get()))
								.hasKnownStatus(INVALID_ACCOUNT_ID)
				);
	}

	private HapiApiSpec complexKeyAcctPaysForOwnTransfer() {
		SigControl ENOUGH_UNIQUE_SIGS = KeyShape.threshSigs(2,
				KeyShape.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, ON),
				KeyShape.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
		String NODE = HapiSpecSetup.getDefaultInstance().defaultNodeName();

		return defaultHapiSpec("ComplexKeyAcctPaysForOwnTransfer")
				.given(
						UtilVerbs.newKeyNamed("complexKey").shape(ENOUGH_UNIQUE_SIGS),
						cryptoCreate("payer").key("complexKey").balance(1_000_000_000L)
				).when().then(
						cryptoTransfer(
								tinyBarsFromTo("payer", NODE, 1_000_000L)
						).payingWith("payer").numPayerSigs(14)
				);
	}

	private HapiApiSpec twoComplexKeysRequired() {
		SigControl PAYER_SHAPE = threshOf(2, threshOf(1, 7), threshOf(3, 7));
		SigControl RECEIVER_SHAPE = KeyShape.threshSigs(3, threshOf(2, 2), threshOf(3, 5), ON);

		SigControl payerSigs = KeyShape.threshSigs(2,
				KeyShape.threshSigs(1, ON, OFF, OFF, OFF, OFF, OFF, OFF),
				KeyShape.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
		SigControl receiverSigs = KeyShape.threshSigs(3,
				KeyShape.threshSigs(2, ON, ON),
				KeyShape.threshSigs(3, OFF, OFF, ON, ON, ON),
				ON);

		return defaultHapiSpec("TwoComplexKeysRequired")
				.given(
						UtilVerbs.newKeyNamed("payerKey").shape(PAYER_SHAPE),
						UtilVerbs.newKeyNamed("receiverKey").shape(RECEIVER_SHAPE),
						cryptoCreate("payer").key("payerKey").balance(100_000_000_000L),
						cryptoCreate("receiver")
								.receiverSigRequired(true)
								.key("receiverKey")
								.payingWith("payer")
				).when().then(
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, "receiver", 1_000L)
						).payingWith("payer").sigControl(
								forKey("payer", payerSigs),
								forKey("receiver", receiverSigs)
						).hasKnownStatus(SUCCESS)
				);
	}

	private HapiApiSpec specialAccountsBalanceCheck() {
		return defaultHapiSpec("SpecialAccountsBalanceCheck")
				.given().when().then(
						IntStream.concat(IntStream.range(1, 101), IntStream.range(900, 1001))
								.mapToObj(i -> getAccountBalance("0.0." + i).logged())
								.toArray(n -> new HapiSpecOperation[n])
				);
	}

	private HapiApiSpec vanillaTransferSucceeds() {
		long initialBalance = HapiSpecSetup.getDefaultInstance().defaultBalance();

		return defaultHapiSpec("VanillaTransferSucceeds")
				.given(
						UtilVerbs.inParallel(
								cryptoCreate("payer"),
								cryptoCreate("payeeSigReq").receiverSigRequired(true),
								cryptoCreate("payeeNoSigReq")
						)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("payer", "payeeSigReq", 1_000L),
								tinyBarsFromTo("payer", "payeeNoSigReq", 2_000L)
						).via("transferTxn")
				).then(
						getAccountInfo("payer").has(accountWith().balance(initialBalance - 3_000L)),
						getAccountInfo("payeeSigReq").has(accountWith().balance(initialBalance + 1_000L)),
						getAccountInfo("payeeNoSigReq").has(accountWith().balance(initialBalance + 2_000L))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
