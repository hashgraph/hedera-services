package com.hedera.services.bdd.suites.crypto;

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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTopicString;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class CryptoTransferSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoTransferSuite.class);

	public static void main(String... args) {
		new CryptoTransferSuite().runSuiteAsync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						eh(),
//						transferWithMissingAccountGetsInvalidAccountId(),
//						vanillaTransferSucceeds(),
//						complexKeyAcctPaysForOwnTransfer(),
//						twoComplexKeysRequired(),
//						specialAccountsBalanceCheck(),
//						transferToTopicReturnsInvalidAccountId(),
//						tokenTransferFeesScaleAsExpected(),
//						okToSetInvalidPaymentHeaderForCostAnswer(),
//						baseCryptoTransferFeeChargedAsExpected(),
//						autoAssociationSuite()
				}
		);
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	private HapiApiSpec autoAssociationSuite() {
		final String tokenA = "tokenA";
		final String tokenB = "tokenB";
		final String firstUser = "firstUser";
		final String secondUser = "secondUser";
		final String treasury = "treasury";
		final String tokenAcreateTxn = "tokenACreate";
		final String tokenBcreateTxn = "tokenBCreate";
		final String transferToFU = "transferToFU";
		final String transferToSU = "transferToSU";

		return defaultHapiSpec("AutoAssociationSuite")
				.given(
						cryptoCreate(treasury)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(firstUser)
								.balance(ONE_HBAR)
								.maxAutomaticTokenAssociations(1),
						cryptoCreate(secondUser)
								.balance(ONE_HBAR)
								.maxAutomaticTokenAssociations(2)
				)
				.when(
						tokenCreate(tokenA)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasury)
								.via(tokenAcreateTxn),
						getTxnRecord(tokenAcreateTxn)
								.hasNewTokenAssociation(tokenA, treasury)
								.logged(),
						tokenCreate(tokenB)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasury)
								.via(tokenBcreateTxn),
						getTxnRecord(tokenBcreateTxn)
								.hasNewTokenAssociation(tokenB, treasury)
								.logged(),
						cryptoTransfer(moving(1, tokenA).between(treasury, firstUser))
								.via(transferToFU),
						getTxnRecord(transferToFU)
								.hasNewTokenAssociation(tokenA, firstUser)
								.logged(),
						cryptoTransfer(moving(1, tokenB).between(treasury, secondUser))
								.via(transferToSU),
						getTxnRecord(transferToSU)
								.hasNewTokenAssociation(tokenB, secondUser)
								.logged()
				)
				.then(
						cryptoTransfer(moving(1, tokenB).between(treasury, firstUser))
								.hasKnownStatus(NO_REMAINING_AUTOMATIC_ASSOCIATIONS)
								.via("failedTransfer"),
						getAccountInfo(firstUser)
								.hasAlreadyUsedAutomaticAssociations(1)
								.hasMaxAutomaticAssociations(1)
								.logged(),
						getAccountInfo(secondUser)
								.hasAlreadyUsedAutomaticAssociations(1)
								.hasMaxAutomaticAssociations(2)
								.logged(),
						cryptoTransfer(moving(1, tokenA).between(treasury, secondUser)),
						getAccountInfo(secondUser)
								.hasAlreadyUsedAutomaticAssociations(2)
								.hasMaxAutomaticAssociations(2)
								.logged(),
						cryptoTransfer(moving(1, tokenA).between(firstUser, treasury)),
						tokenDissociate(firstUser, tokenA),
						cryptoTransfer(moving(1, tokenB).between(treasury, firstUser))
				);
	}

	private HapiApiSpec baseCryptoTransferFeeChargedAsExpected() {
		final var expectedHbarXferPriceUsd = 0.0001;
		final var expectedHtsXferPriceUsd = 0.001;
		final var expectedNftXferPriceUsd = 0.001;
		final var expectedHtsXferWithCustomFeePriceUsd = 0.002;
		final var expectedNftXferWithCustomFeePriceUsd = 0.002;
		final var transferAmount = 1L;
		final var customFeeCollector = "customFeeCollector";
		final var sender = "sender";
		final var nonTreasurySender = "nonTreasurySender";
		final var receiver = "receiver";
		final var hbarXferTxn = "hbarXferTxn";
		final var fungibleToken = "fungibleToken";
		final var fungibleTokenWithCustomFee = "fungibleTokenWithCustomFee";
		final var htsXferTxn = "htsXferTxn";
		final var htsXferTxnWithCustomFee = "htsXferTxnWithCustomFee";
		final var nonFungibleToken = "nonFungibleToken";
		final var nonFungibleTokenWithCustomFee = "nonFungibleTokenWithCustomFee";
		final var nftXferTxn = "nftXferTxn";
		final var nftXferTxnWithCustomFee = "nftXferTxnWithCustomFee";
		final var supplyKey = "supplyKey";

		return defaultHapiSpec("BaseCryptoTransferIsChargedAsExpected")
				.given(
						cryptoCreate(nonTreasurySender).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(sender).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(receiver),
						cryptoCreate(customFeeCollector),
						tokenCreate(fungibleToken)
								.treasury(sender)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(100L),
						tokenCreate(fungibleTokenWithCustomFee)
								.treasury(sender)
								.tokenType(FUNGIBLE_COMMON)
								.withCustom(fixedHbarFee(transferAmount, customFeeCollector))
								.initialSupply(100L),
						tokenAssociate(receiver, fungibleToken, fungibleTokenWithCustomFee),
						newKeyNamed(supplyKey),
						tokenCreate(nonFungibleToken)
								.initialSupply(0)
								.supplyKey(supplyKey)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(sender),
						tokenCreate(nonFungibleTokenWithCustomFee)
								.initialSupply(0)
								.supplyKey(supplyKey)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.withCustom(fixedHbarFee(transferAmount, customFeeCollector))
								.treasury(sender),
						tokenAssociate(nonTreasurySender,
								List.of(fungibleTokenWithCustomFee, nonFungibleTokenWithCustomFee)),
						mintToken(nonFungibleToken, List.of(ByteString.copyFromUtf8("memo1"))),
						mintToken(nonFungibleTokenWithCustomFee, List.of(ByteString.copyFromUtf8("memo2"))),
						tokenAssociate(receiver, nonFungibleToken, nonFungibleTokenWithCustomFee),
						cryptoTransfer(movingUnique(nonFungibleTokenWithCustomFee, 1)
								.between(sender, nonTreasurySender))
								.payingWith(sender),
						cryptoTransfer(moving(1, fungibleTokenWithCustomFee).between(sender, nonTreasurySender))
								.payingWith(sender)
				)
				.when(
						cryptoTransfer(tinyBarsFromTo(sender, receiver, 100L))
								.payingWith(sender)
								.blankMemo()
								.via(hbarXferTxn),
						cryptoTransfer(moving(1, fungibleToken).between(sender, receiver))
								.blankMemo()
								.payingWith(sender)
								.via(htsXferTxn),
						cryptoTransfer(movingUnique(nonFungibleToken, 1).between(sender, receiver))
								.blankMemo()
								.payingWith(sender)
								.via(nftXferTxn),
						cryptoTransfer(moving(1, fungibleTokenWithCustomFee)
								.between(nonTreasurySender, receiver)
						)
								.blankMemo()
								.fee(ONE_HBAR)
								.payingWith(nonTreasurySender)
								.via(htsXferTxnWithCustomFee),
						cryptoTransfer(movingUnique(nonFungibleTokenWithCustomFee, 1)
								.between(nonTreasurySender, receiver)
						)
								.blankMemo()
								.fee(ONE_HBAR)
								.payingWith(nonTreasurySender)
								.via(nftXferTxnWithCustomFee)
				)
				.then(
						validateChargedUsdWithin(hbarXferTxn, expectedHbarXferPriceUsd, 0.01),
						validateChargedUsdWithin(htsXferTxn, expectedHtsXferPriceUsd, 0.01),
						validateChargedUsdWithin(nftXferTxn, expectedNftXferPriceUsd, 0.01),
						validateChargedUsdWithin(htsXferTxnWithCustomFee, expectedHtsXferWithCustomFeePriceUsd, 0.1),
						validateChargedUsdWithin(nftXferTxnWithCustomFee, expectedNftXferWithCustomFeePriceUsd, 0.3)
				);
	}

	private HapiApiSpec okToSetInvalidPaymentHeaderForCostAnswer() {
		return defaultHapiSpec("OkToSetInvalidPaymentHeaderForCostAnswer")
				.given(
						cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L))
								.via("misc")
				).when().then(
						getTxnRecord("misc").useEmptyTxnAsCostPayment(),
						getTxnRecord("misc").omittingAnyPaymentForCostAnswer()
				);
	}


	private HapiApiSpec tokenTransferFeesScaleAsExpected() {
		return defaultHapiSpec("TokenTransferFeesScaleAsExpected")
				.given(
						cryptoCreate("a"),
						cryptoCreate("b"),
						cryptoCreate("c").balance(0L),
						cryptoCreate("d").balance(0L),
						cryptoCreate("e").balance(0L),
						cryptoCreate("f").balance(0L),
						tokenCreate("A").treasury("a"),
						tokenCreate("B").treasury("b"),
						tokenCreate("C").treasury("c")
				).when(
						tokenAssociate("b", "A", "C"),
						tokenAssociate("c", "A", "B"),
						tokenAssociate("d", "A", "B", "C"),
						tokenAssociate("e", "A", "B", "C"),
						tokenAssociate("f", "A", "B", "C"),
						cryptoTransfer(tinyBarsFromTo("a", "b", 1))
								.via("pureCrypto")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(moving(1, "A").between("a", "b"))
								.via("oneTokenTwoAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(moving(2, "A").distributing("a", "b", "c"))
								.via("oneTokenThreeAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(moving(3, "A").distributing("a", "b", "c", "d"))
								.via("oneTokenFourAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(moving(4, "A").distributing("a", "b", "c", "d", "e"))
								.via("oneTokenFiveAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(moving(5, "A").distributing("a", "b", "c", "d", "e", "f"))
								.via("oneTokenSixAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(
								moving(1, "A").between("a", "c"),
								moving(1, "B").between("b", "d"))
								.via("twoTokensFourAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(
								moving(1, "A").between("a", "c"),
								moving(2, "B").distributing("b", "d", "e"))
								.via("twoTokensFiveAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(
								moving(1, "A").between("a", "c"),
								moving(3, "B").distributing("b", "d", "e", "f"))
								.via("twoTokensSixAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(
								moving(1, "A").between("a", "d"),
								moving(1, "B").between("b", "e"),
								moving(1, "C").between("c", "f"))
								.via("threeTokensSixAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a")
				).then(
						withOpContext((spec, opLog) -> {
							var ref = getTxnRecord("pureCrypto");
							var t1a2 = getTxnRecord("oneTokenTwoAccounts");
							var t1a3 = getTxnRecord("oneTokenThreeAccounts");
							var t1a4 = getTxnRecord("oneTokenFourAccounts");
							var t1a5 = getTxnRecord("oneTokenFiveAccounts");
							var t1a6 = getTxnRecord("oneTokenSixAccounts");
							var t2a4 = getTxnRecord("twoTokensFourAccounts");
							var t2a5 = getTxnRecord("twoTokensFiveAccounts");
							var t2a6 = getTxnRecord("twoTokensSixAccounts");
							var t3a6 = getTxnRecord("threeTokensSixAccounts");
							allRunFor(spec, ref, t1a2, t1a3, t1a4, t1a5, t1a6, t2a4, t2a5, t2a6, t3a6);

							var refFee = ref.getResponseRecord().getTransactionFee();
							var t1a2Fee = t1a2.getResponseRecord().getTransactionFee();
							var t1a3Fee = t1a3.getResponseRecord().getTransactionFee();
							var t1a4Fee = t1a4.getResponseRecord().getTransactionFee();
							var t1a5Fee = t1a5.getResponseRecord().getTransactionFee();
							var t1a6Fee = t1a6.getResponseRecord().getTransactionFee();
							var t2a4Fee = t2a4.getResponseRecord().getTransactionFee();
							var t2a5Fee = t2a5.getResponseRecord().getTransactionFee();
							var t2a6Fee = t2a6.getResponseRecord().getTransactionFee();
							var t3a6Fee = t3a6.getResponseRecord().getTransactionFee();

							var rates = spec.ratesProvider();
							opLog.info("\n0 tokens involved,\n" +
											"  2 account adjustments: {} tb, ${}\n" +
											"1 tokens involved,\n" +
											"  2 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"  3 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"  4 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"  5 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"  6 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"2 tokens involved,\n" +
											"  4 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"  5 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"  6 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"3 tokens involved,\n" +
											"  6 account adjustments: {} tb, ${} (~{}x pure crypto)\n",
									refFee, sdec(rates.toUsdWithActiveRates(refFee), 4),
									t1a2Fee, sdec(rates.toUsdWithActiveRates(t1a2Fee), 4),
									sdec((1.0 * t1a2Fee / refFee), 1),
									t1a3Fee, sdec(rates.toUsdWithActiveRates(t1a3Fee), 4),
									sdec((1.0 * t1a3Fee / refFee), 1),
									t1a4Fee, sdec(rates.toUsdWithActiveRates(t1a4Fee), 4),
									sdec((1.0 * t1a4Fee / refFee), 1),
									t1a5Fee, sdec(rates.toUsdWithActiveRates(t1a5Fee), 4),
									sdec((1.0 * t1a5Fee / refFee), 1),
									t1a6Fee, sdec(rates.toUsdWithActiveRates(t1a6Fee), 4),
									sdec((1.0 * t1a6Fee / refFee), 1),
									t2a4Fee, sdec(rates.toUsdWithActiveRates(t2a4Fee), 4),
									sdec((1.0 * t2a4Fee / refFee), 1),
									t2a5Fee, sdec(rates.toUsdWithActiveRates(t2a5Fee), 4),
									sdec((1.0 * t2a5Fee / refFee), 1),
									t2a6Fee, sdec(rates.toUsdWithActiveRates(t2a6Fee), 4),
									sdec((1.0 * t2a6Fee / refFee), 1),
									t3a6Fee, sdec(rates.toUsdWithActiveRates(t3a6Fee), 4),
									sdec((1.0 * t3a6Fee / refFee), 1));

							double pureHbarUsd = rates.toUsdWithActiveRates(refFee);
							double pureOneTokenTwoAccountsUsd = rates.toUsdWithActiveRates(t1a2Fee);
							double pureTwoTokensFourAccountsUsd = rates.toUsdWithActiveRates(t2a4Fee);
							double pureThreeTokensSixAccountsUsd = rates.toUsdWithActiveRates(t3a6Fee);
							Assertions.assertEquals(
									10.0,
									pureOneTokenTwoAccountsUsd / pureHbarUsd,
									1.0);
							Assertions.assertEquals(
									20.0,
									pureTwoTokensFourAccountsUsd / pureHbarUsd,
									2.0);
							Assertions.assertEquals(
									30.0,
									pureThreeTokensSixAccountsUsd / pureHbarUsd,
									3.0);
						})
				);
	}

	public static String sdec(double d, int numDecimals) {
		var fmt = String.format(".0%df", numDecimals);
		return String.format("%" + fmt, d);
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
						sourcing(() -> cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, invalidAccountId.get(), 1L))
								.signedBy(DEFAULT_PAYER)
								.hasKnownStatus(INVALID_ACCOUNT_ID)),
						sourcing(() -> cryptoTransfer(moving(1, "token")
								.between(DEFAULT_PAYER, invalidAccountId.get()))
								.signedBy(DEFAULT_PAYER)
								.hasKnownStatus(INVALID_ACCOUNT_ID))
				);
	}

	private HapiApiSpec complexKeyAcctPaysForOwnTransfer() {
		SigControl ENOUGH_UNIQUE_SIGS = KeyShape.threshSigs(2,
				KeyShape.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, ON),
				KeyShape.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
		String NODE = HapiSpecSetup.getDefaultInstance().defaultNodeName();

		return defaultHapiSpec("ComplexKeyAcctPaysForOwnTransfer")
				.given(
						newKeyNamed("complexKey").shape(ENOUGH_UNIQUE_SIGS),
						cryptoCreate("payer").key("complexKey").balance(1_000_000_000L)
				).when().then(
						cryptoTransfer(
								tinyBarsFromTo("payer", NODE, 1_000_000L)
						).payingWith("payer").numPayerSigs(14).fee(ONE_HUNDRED_HBARS)
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
						newKeyNamed("payerKey").shape(PAYER_SHAPE),
						newKeyNamed("receiverKey").shape(RECEIVER_SHAPE),
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
								.fee(ONE_HUNDRED_HBARS)
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

	private HapiApiSpec transferWithMissingAccountGetsInvalidAccountId() {
		return defaultHapiSpec("TransferWithMissingAccount")
				.given(
						cryptoCreate("payeeSigReq").receiverSigRequired(true)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("1.2.3", "payeeSigReq", 1_000L)
						)
								.signedBy(DEFAULT_PAYER, "payeeSigReq")
								.hasKnownStatus(INVALID_ACCOUNT_ID)
				).then(
				);
	}

	private HapiApiSpec eh() {
		return defaultHapiSpec("Eh")
				.given(
				).when(
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, ONE_MILLION_HBARS)
						).payingWith(GENESIS)
				).then(
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
