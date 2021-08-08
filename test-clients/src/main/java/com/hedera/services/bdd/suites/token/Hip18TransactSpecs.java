package com.hedera.services.bdd.suites.token;

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
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.OptionalLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFeeTemp;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class Hip18TransactSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(Hip18TransactSpecs.class);

	private static final String SUPPLY_KEY = "antique";

	private static final String TOKEN_TREASURY = "treasury";
	private static final String ALICE = "Alice";
	private static final String BOB = "Bob";
	private static final String CLAIRE = "Claire";
	private static final String DEBBIE = "Debbie";
	private static final String EDGAR = "Edgar";
	private static final String FERN = "Fern";

	private static final String TOKEN_WITH_FRACTIONAL_FEE = "TokenWithFractionalFee";
	private static final String TOKEN_WITH_HBAR_FEE = "TokenWithHbarFee";
	private static final String SIMPLE_HTS_FEE_TOKEN = "SimpleHtsFeeToken";
	private static final String COMMISSION_PAYMENT_TOKEN = "commissionPaymentToken";
	private static final String TOKEN_WITH_NESTED_FEE = "TokenWithNestedFee";
	private static final String FEE_TOKEN = "FeeToken";
	private static final String TOKEN_WITH_HTS_FEE = "TokenWithHtsFee";
	private static final String TOP_LEVEL_TOKEN = "TopLevelToken";

	private static final String FIRST_COLLECTOR_FOR_TOP_LEVEL = "AFeeCollector";
	private static final String SECOND_COLLECTOR_FOR_TOP_LEVEL = "BFeeCollector";
	private static final String TREASURY_FOR_TOKEN = "TokenTreasury";
	private static final String COLLECTOR_FOR_TOKEN = "AnotherTokenTreasury";
	private static final String TREASURY_FOR_TOP_LEVEL_COLLECTION = "TokenTreasury";
	private static final String TREASURY_FOR_NESTED_COLLECTION = "NestedTokenTreasury";
	private static final String TREASURY_FOR_TOP_LEVEL = "TokenTreasury";
	private static final String COLLECTOR_FOR_TOP_LEVEL = "FeeCollector";
	private static final String NON_TREASURY = "nonTreasury";

	private static final String TXN_FROM_TREASURY = "txnFromTreasury";
	private static final String TXN_FROM_BOB = "txnFromBob";
	private static final String TXN_FROM_ALICE = "txnFromAlice";
	private static final String TXN_FROM_CLAIRE = "txnFromClaire";
	private static final String TXN_FROM_DEBBIE = "txnFromDebbie";
	private static final String TXN_FROM_EDGAR = "txnFromEdgar";
	private static final String TXN_FROM_NON_TREASURY = "txnFromNonTreasury";
	private static final String TXN_FROM_COLLECTOR = "txnFromCollector";
	
	public static void main(String... args) {
		new Hip18TransactSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						fixedHbarCaseStudy(),
						fractionalChargeFeeToReceiver(),
						fractionalChargeFeeToSenderNotEnough(),
						fractionalChargeFeeToSenderHappy(),
						simpleHtsFeeCaseStudy(),
						nestedHbarCaseStudy(),
						nestedFractionalCaseStudy(),
						nestedHtsCaseStudy(),
						treasuriesAreExemptFromAllCustomFees(),
						collectorsAreExemptFromTheirOwnFeesButNotOthers(),
				}
		);
	}

	public HapiApiSpec fixedHbarCaseStudy() {
		return defaultHapiSpec("FixedHbarCaseStudy")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(ALICE).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(BOB),
						cryptoCreate(TREASURY_FOR_TOKEN).balance(ONE_HUNDRED_HBARS),
						tokenCreate(TOKEN_WITH_HBAR_FEE)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0L)
								.treasury(TREASURY_FOR_TOKEN)
								.withCustom(fixedHbarFee(ONE_HBAR, TREASURY_FOR_TOKEN)),
						mintToken(TOKEN_WITH_HBAR_FEE, List.of(ByteString.copyFromUtf8("First!"))),
						mintToken(TOKEN_WITH_HBAR_FEE, List.of(ByteString.copyFromUtf8("Second!"))),
						tokenAssociate(ALICE, TOKEN_WITH_HBAR_FEE),
						tokenAssociate(BOB, TOKEN_WITH_HBAR_FEE),
						cryptoTransfer(movingUnique(TOKEN_WITH_HBAR_FEE, 2L).between(TREASURY_FOR_TOKEN, ALICE))
								.payingWith(GENESIS)
								.fee(ONE_HBAR)
								.via(TXN_FROM_TREASURY)
				).when(
						cryptoTransfer(
								movingUnique(TOKEN_WITH_HBAR_FEE, 2L).between(ALICE, BOB)
						)
								.payingWith(GENESIS)
								.fee(ONE_HBAR)
								.via(TXN_FROM_ALICE)
				).then(
						getTxnRecord(TXN_FROM_TREASURY)
								.hasNftTransfer(TOKEN_WITH_HBAR_FEE, TREASURY_FOR_TOKEN, ALICE, 2L),
						getTxnRecord(TXN_FROM_ALICE)
								.hasNftTransfer(TOKEN_WITH_HBAR_FEE, ALICE, BOB, 2L)
								.hasAssessedCustomFee(HBAR_TOKEN_SENTINEL, TREASURY_FOR_TOKEN, ONE_HBAR)
								.hasHbarAmount(TREASURY_FOR_TOKEN, ONE_HBAR)
								.hasHbarAmount(ALICE, -ONE_HBAR),
						getAccountBalance(BOB)
								.hasTokenBalance(TOKEN_WITH_HBAR_FEE, 1L),
						getAccountBalance(ALICE)
								.hasTokenBalance(TOKEN_WITH_HBAR_FEE, 0L)
								.hasTinyBars(ONE_HUNDRED_HBARS - ONE_HBAR),
						getAccountBalance(TREASURY_FOR_TOKEN)
								.hasTokenBalance(TOKEN_WITH_HBAR_FEE, 1L)
								.hasTinyBars(ONE_HUNDRED_HBARS + ONE_HBAR)
				);
	}

	public HapiApiSpec fractionalChargeFeeToSenderHappy() {
		return defaultHapiSpec("fractionalChargeFeeToSenderHappy")
				.given(
						cryptoCreate(ALICE),
						cryptoCreate(BOB),
						cryptoCreate(TREASURY_FOR_TOKEN),
						cryptoCreate(COLLECTOR_FOR_TOKEN),
						tokenCreate(TOKEN_WITH_FRACTIONAL_FEE)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TREASURY_FOR_TOKEN)
								.withCustom(fractionalFeeTemp(1L, 100L, 1L,
										OptionalLong.of(5L), true, TREASURY_FOR_TOKEN)),

						tokenAssociate(ALICE, TOKEN_WITH_FRACTIONAL_FEE),
						tokenAssociate(COLLECTOR_FOR_TOKEN, TOKEN_WITH_FRACTIONAL_FEE),
						tokenAssociate(BOB, TOKEN_WITH_FRACTIONAL_FEE),
						cryptoTransfer(moving(1_000_000L, TOKEN_WITH_FRACTIONAL_FEE).between(TREASURY_FOR_TOKEN, BOB))
								.payingWith(TREASURY_FOR_TOKEN)
								.fee(ONE_HBAR)
								.via(TXN_FROM_TREASURY)
				).when(
						cryptoTransfer(
								moving(1_000L, TOKEN_WITH_FRACTIONAL_FEE).between(BOB, ALICE)
						)
								.payingWith(BOB)
								.fee(ONE_HBAR)
								.via(TXN_FROM_BOB)
						.hasKnownStatus(SUCCESS)
				).then(
						getTxnRecord(TXN_FROM_TREASURY).logged()
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, BOB, 1_000_000L)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_TOKEN, -1_000_000L),
						getTxnRecord(TXN_FROM_BOB)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, BOB, -1_005L)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, ALICE, 1000L)
								.hasAssessedCustomFee(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_TOKEN, 5L)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_TOKEN, 5L),
						getAccountBalance(ALICE)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE, 1_000L),
						getAccountBalance(BOB)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE, 1_000_000L - 1_005L),
						getAccountBalance(TREASURY_FOR_TOKEN)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE, Long.MAX_VALUE - 1_000_000L + 5L)
				);
	}


	public HapiApiSpec fractionalChargeFeeToSenderNotEnough() {
		return defaultHapiSpec("fractionalChargeFeeToSenderNotEnough")
				.given(
						cryptoCreate(ALICE),
						cryptoCreate(BOB),
						cryptoCreate(TREASURY_FOR_TOKEN),
						cryptoCreate(COLLECTOR_FOR_TOKEN),
						tokenCreate(TOKEN_WITH_FRACTIONAL_FEE)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TREASURY_FOR_TOKEN)
								.withCustom(fractionalFeeTemp(1L, 100L, 1L,
										OptionalLong.of(5L), true, TREASURY_FOR_TOKEN)),

						tokenAssociate(ALICE, TOKEN_WITH_FRACTIONAL_FEE),
						tokenAssociate(COLLECTOR_FOR_TOKEN, TOKEN_WITH_FRACTIONAL_FEE),
						tokenAssociate(BOB, TOKEN_WITH_FRACTIONAL_FEE),
						cryptoTransfer(moving(1_000L, TOKEN_WITH_FRACTIONAL_FEE).between(TREASURY_FOR_TOKEN, BOB))
								.payingWith(TREASURY_FOR_TOKEN)
								.fee(ONE_HBAR)
								.via(TXN_FROM_TREASURY)
				).when(
						cryptoTransfer(
								moving(1_000L, TOKEN_WITH_FRACTIONAL_FEE).between(BOB, ALICE)
						)
								.payingWith(BOB)
								.fee(ONE_HBAR)
								.via(TXN_FROM_BOB)
								.hasKnownStatus(INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE)
				).then(
						getTxnRecord(TXN_FROM_TREASURY).logged()
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, BOB, 1_000L)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_TOKEN, -1_000L),
						getAccountBalance(ALICE)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE, 0L),
						getAccountBalance(BOB)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE,  1_000L),
						getAccountBalance(TREASURY_FOR_TOKEN)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE, Long.MAX_VALUE - 1_000L)
				);
	}



	public HapiApiSpec fractionalChargeFeeToSenderHappyPath() {
		return defaultHapiSpec("fractionalChargeFeeToSender")
				.given(
						cryptoCreate(ALICE),
						cryptoCreate(BOB),
						cryptoCreate(TREASURY_FOR_TOKEN),
						cryptoCreate(COLLECTOR_FOR_TOKEN),
						tokenCreate(TOKEN_WITH_FRACTIONAL_FEE)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TREASURY_FOR_TOKEN)
								.withCustom(fractionalFeeTemp(1L, 100L, 1L,
										OptionalLong.of(5L), true, TREASURY_FOR_TOKEN)),

						tokenAssociate(ALICE, TOKEN_WITH_FRACTIONAL_FEE),
						tokenAssociate(COLLECTOR_FOR_TOKEN, TOKEN_WITH_FRACTIONAL_FEE),
						tokenAssociate(BOB, TOKEN_WITH_FRACTIONAL_FEE),
						cryptoTransfer(moving(1_000L, TOKEN_WITH_FRACTIONAL_FEE).between(TREASURY_FOR_TOKEN, BOB))
								.payingWith(TREASURY_FOR_TOKEN)
								.fee(ONE_HBAR)
								.via(TXN_FROM_TREASURY)
				).when(
						cryptoTransfer(
								moving(1_000L, TOKEN_WITH_FRACTIONAL_FEE).between(BOB, ALICE)
						)
								.payingWith(BOB)
								.fee(ONE_HBAR)
								.via(TXN_FROM_BOB)
								.hasKnownStatus(INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE)
				).then(
						getTxnRecord(TXN_FROM_TREASURY).logged()
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, BOB, 1_000L)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_TOKEN, -1_000L),
						getTxnRecord(TXN_FROM_BOB)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, BOB, -1_000L)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, ALICE, 1000L)
								.hasAssessedCustomFee(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_TOKEN, 5L)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_TOKEN, 5L),
						getAccountBalance(ALICE)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE, 0L),
						getAccountBalance(BOB)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE,  1_000L),
						getAccountBalance(TREASURY_FOR_TOKEN)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE, Long.MAX_VALUE - 1_000L)
				);
	}



	public HapiApiSpec fractionalChargeFeeToReceiver() {
		return defaultHapiSpec("fractionalChargeFeeToReceiver")
				.given(
						cryptoCreate(ALICE),
						cryptoCreate(BOB),
						cryptoCreate(TREASURY_FOR_TOKEN),
						cryptoCreate(COLLECTOR_FOR_TOKEN),
						tokenCreate(TOKEN_WITH_FRACTIONAL_FEE)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TREASURY_FOR_TOKEN)
								.withCustom(fractionalFeeTemp(1L, 100L, 1L, OptionalLong.of(5L), false,
										TREASURY_FOR_TOKEN)),


						tokenAssociate(ALICE, TOKEN_WITH_FRACTIONAL_FEE),
						tokenAssociate(COLLECTOR_FOR_TOKEN, TOKEN_WITH_FRACTIONAL_FEE),
						tokenAssociate(BOB, TOKEN_WITH_FRACTIONAL_FEE),
						cryptoTransfer(moving(1_000_000L, TOKEN_WITH_FRACTIONAL_FEE).between(TREASURY_FOR_TOKEN, BOB))
								.payingWith(TREASURY_FOR_TOKEN)
								.fee(ONE_HBAR)
								.via(TXN_FROM_TREASURY)
				).when(
						cryptoTransfer(
								moving(1_000L, TOKEN_WITH_FRACTIONAL_FEE).between(BOB, ALICE)
						)
								.payingWith(BOB)
								.fee(ONE_HBAR)
								.via(TXN_FROM_BOB)
								.hasKnownStatus(SUCCESS)
				).then(
						getTxnRecord(TXN_FROM_TREASURY).logged()
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, BOB, 1_000_000L)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_TOKEN, -1_000_000L),
						getTxnRecord(TXN_FROM_BOB)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, BOB, -1_000L)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, ALICE, 995L)
								.hasAssessedCustomFee(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_TOKEN, 5L)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_TOKEN, 5L),
						getAccountBalance(ALICE)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE, 995L),
						getAccountBalance(BOB)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE, 1_000_000L - 1_000L),
						getAccountBalance(TREASURY_FOR_TOKEN)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE, Long.MAX_VALUE - 1_000_000L + 5L)
				);
	}


	public HapiApiSpec simpleHtsFeeCaseStudy() {
		return defaultHapiSpec("SimpleHtsFeeCaseStudy")
				.given(
						cryptoCreate(CLAIRE),
						cryptoCreate(DEBBIE),
						cryptoCreate(TREASURY_FOR_TOKEN),
						tokenCreate(COMMISSION_PAYMENT_TOKEN)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TREASURY_FOR_TOKEN),
						tokenCreate(SIMPLE_HTS_FEE_TOKEN)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TREASURY_FOR_TOKEN)
								.withCustom(fixedHtsFee(2L, COMMISSION_PAYMENT_TOKEN, TREASURY_FOR_TOKEN)),
						tokenAssociate(CLAIRE, List.of(SIMPLE_HTS_FEE_TOKEN, COMMISSION_PAYMENT_TOKEN)),
						tokenAssociate(DEBBIE, SIMPLE_HTS_FEE_TOKEN),
						cryptoTransfer(
								moving(1_000L, COMMISSION_PAYMENT_TOKEN).between(TREASURY_FOR_TOKEN, CLAIRE),
								moving(1_000L, SIMPLE_HTS_FEE_TOKEN).between(TREASURY_FOR_TOKEN, CLAIRE)
						)
								.payingWith(TREASURY_FOR_TOKEN)
								.fee(ONE_HBAR)
								.via(TXN_FROM_TREASURY)
				).when(
						cryptoTransfer(
								moving(100L, SIMPLE_HTS_FEE_TOKEN).between(CLAIRE, DEBBIE)
						)
								//.payingWith(claire)
								.fee(ONE_HBAR)
								.via(TXN_FROM_CLAIRE)
				).then(
						getTxnRecord(TXN_FROM_TREASURY)
								.hasTokenAmount(COMMISSION_PAYMENT_TOKEN, CLAIRE, 1_000L)
								.hasTokenAmount(COMMISSION_PAYMENT_TOKEN, TREASURY_FOR_TOKEN, -1_000L)
								.hasTokenAmount(SIMPLE_HTS_FEE_TOKEN, CLAIRE, 1_000L)
								.hasTokenAmount(SIMPLE_HTS_FEE_TOKEN, TREASURY_FOR_TOKEN, -1_000L),
						getTxnRecord(TXN_FROM_CLAIRE)
								.hasTokenAmount(SIMPLE_HTS_FEE_TOKEN, DEBBIE, 100L)
								.hasTokenAmount(SIMPLE_HTS_FEE_TOKEN, CLAIRE, -100L)
								.hasAssessedCustomFee(COMMISSION_PAYMENT_TOKEN, TREASURY_FOR_TOKEN, 2L)
								.hasTokenAmount(COMMISSION_PAYMENT_TOKEN, TREASURY_FOR_TOKEN, 2L)
								.hasTokenAmount(COMMISSION_PAYMENT_TOKEN, CLAIRE, -2L),
						getAccountBalance(DEBBIE)
								.hasTokenBalance(SIMPLE_HTS_FEE_TOKEN, 100L),
						getAccountBalance(CLAIRE)
								.hasTokenBalance(SIMPLE_HTS_FEE_TOKEN, 1_000L - 100L)
								.hasTokenBalance(COMMISSION_PAYMENT_TOKEN, 1_000L - 2L),
						getAccountBalance(TREASURY_FOR_TOKEN)
								.hasTokenBalance(SIMPLE_HTS_FEE_TOKEN, Long.MAX_VALUE - 1_000L)
								.hasTokenBalance(COMMISSION_PAYMENT_TOKEN, Long.MAX_VALUE - 1_000L + 2L)
				);
	}

	public HapiApiSpec nestedHbarCaseStudy() {
		return defaultHapiSpec("NestedHbarCaseStudy")
				.given(
						cryptoCreate(DEBBIE).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(EDGAR),
						cryptoCreate(TREASURY_FOR_TOP_LEVEL_COLLECTION),
						cryptoCreate(TREASURY_FOR_NESTED_COLLECTION).balance(ONE_HUNDRED_HBARS),
						tokenCreate(TOKEN_WITH_HBAR_FEE)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TREASURY_FOR_NESTED_COLLECTION)
								.withCustom(fixedHbarFee(ONE_HBAR, TREASURY_FOR_NESTED_COLLECTION)),
						tokenAssociate(TREASURY_FOR_TOP_LEVEL_COLLECTION, TOKEN_WITH_HBAR_FEE),
						tokenCreate(TOKEN_WITH_NESTED_FEE)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TREASURY_FOR_TOP_LEVEL_COLLECTION)
								.withCustom(fixedHtsFee(1L, TOKEN_WITH_HBAR_FEE, TREASURY_FOR_TOP_LEVEL_COLLECTION)),
						tokenAssociate(DEBBIE, List.of(TOKEN_WITH_HBAR_FEE, TOKEN_WITH_NESTED_FEE)),
						tokenAssociate(EDGAR, TOKEN_WITH_NESTED_FEE),
						cryptoTransfer(
								moving(1_000L, TOKEN_WITH_HBAR_FEE)
										.between(TREASURY_FOR_NESTED_COLLECTION, DEBBIE),
								moving(1_000L, TOKEN_WITH_NESTED_FEE)
										.between(TREASURY_FOR_TOP_LEVEL_COLLECTION, DEBBIE)
						)
								.payingWith(GENESIS)
								.fee(ONE_HBAR)
								.via(TXN_FROM_TREASURY)
				).when(
						cryptoTransfer(
								moving(1L, TOKEN_WITH_NESTED_FEE).between(DEBBIE, EDGAR)
						)
								.payingWith(GENESIS)
								.fee(ONE_HBAR)
								.via(TXN_FROM_DEBBIE)
				).then(
						getTxnRecord(TXN_FROM_TREASURY)
								.hasTokenAmount(TOKEN_WITH_HBAR_FEE, DEBBIE, 1_000L)
								.hasTokenAmount(TOKEN_WITH_HBAR_FEE, TREASURY_FOR_NESTED_COLLECTION, -1_000L)
								.hasTokenAmount(TOKEN_WITH_NESTED_FEE, DEBBIE, 1_000L)
								.hasTokenAmount(TOKEN_WITH_NESTED_FEE, TREASURY_FOR_TOP_LEVEL_COLLECTION, -1_000L),
						getTxnRecord(TXN_FROM_DEBBIE)
								.hasTokenAmount(TOKEN_WITH_NESTED_FEE, EDGAR, 1L)
								.hasTokenAmount(TOKEN_WITH_NESTED_FEE, DEBBIE, -1L)
								.hasAssessedCustomFee(TOKEN_WITH_HBAR_FEE, TREASURY_FOR_TOP_LEVEL_COLLECTION, 1L)
								.hasTokenAmount(TOKEN_WITH_HBAR_FEE, TREASURY_FOR_TOP_LEVEL_COLLECTION, 1L)
								.hasTokenAmount(TOKEN_WITH_HBAR_FEE, DEBBIE, -1L)
								.hasAssessedCustomFee(HBAR_TOKEN_SENTINEL, TREASURY_FOR_NESTED_COLLECTION, ONE_HBAR)
								.hasHbarAmount(TREASURY_FOR_NESTED_COLLECTION, ONE_HBAR)
								.hasHbarAmount(DEBBIE, -ONE_HBAR),
						getAccountBalance(EDGAR)
								.hasTokenBalance(TOKEN_WITH_NESTED_FEE, 1L),
						getAccountBalance(DEBBIE)
								.hasTinyBars(ONE_HUNDRED_HBARS - ONE_HBAR)
								.hasTokenBalance(TOKEN_WITH_HBAR_FEE, 1_000L - 1L)
								.hasTokenBalance(TOKEN_WITH_NESTED_FEE, 1_000L - 1L),
						getAccountBalance(TREASURY_FOR_TOP_LEVEL_COLLECTION)
								.hasTokenBalance(TOKEN_WITH_NESTED_FEE, Long.MAX_VALUE - 1_000L)
								.hasTokenBalance(TOKEN_WITH_HBAR_FEE, 1L),
						getAccountBalance(TREASURY_FOR_NESTED_COLLECTION)
								.hasTinyBars(ONE_HUNDRED_HBARS + ONE_HBAR)
								.hasTokenBalance(TOKEN_WITH_HBAR_FEE, Long.MAX_VALUE - 1_000L)
				);
	}

	public HapiApiSpec nestedFractionalCaseStudy() {
		return defaultHapiSpec("NestedFractionalCaseStudy")
				.given(
						cryptoCreate(EDGAR),
						cryptoCreate(FERN),
						cryptoCreate(TREASURY_FOR_TOP_LEVEL_COLLECTION),
						cryptoCreate(TREASURY_FOR_NESTED_COLLECTION),
						tokenCreate(TOKEN_WITH_FRACTIONAL_FEE)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TREASURY_FOR_NESTED_COLLECTION)
								.withCustom(fractionalFee(1L, 100L, 1L, OptionalLong.of(5L),
										false,
										TREASURY_FOR_NESTED_COLLECTION)),
						tokenAssociate(TREASURY_FOR_TOP_LEVEL_COLLECTION, TOKEN_WITH_FRACTIONAL_FEE),
						tokenCreate(TOKEN_WITH_NESTED_FEE)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TREASURY_FOR_TOP_LEVEL_COLLECTION)
								.withCustom(fixedHtsFee(50L, TOKEN_WITH_FRACTIONAL_FEE,
										TREASURY_FOR_TOP_LEVEL_COLLECTION)),
						tokenAssociate(EDGAR, List.of(TOKEN_WITH_FRACTIONAL_FEE, TOKEN_WITH_NESTED_FEE)),
						tokenAssociate(FERN, TOKEN_WITH_NESTED_FEE),
						cryptoTransfer(
								moving(1_000L, TOKEN_WITH_FRACTIONAL_FEE)
										.between(TREASURY_FOR_NESTED_COLLECTION, EDGAR),
								moving(1_000L, TOKEN_WITH_NESTED_FEE)
										.between(TREASURY_FOR_TOP_LEVEL_COLLECTION, EDGAR)
						)
								.payingWith(TREASURY_FOR_NESTED_COLLECTION)
								.fee(ONE_HBAR)
								.via(TXN_FROM_TREASURY)
				).when(
						cryptoTransfer(
								moving(10L, TOKEN_WITH_NESTED_FEE).between(EDGAR, FERN)
						)
								.payingWith(EDGAR)
								.fee(ONE_HBAR)
								.via(TXN_FROM_EDGAR)
				).then(
						getTxnRecord(TXN_FROM_TREASURY)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, EDGAR, 1_000L)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_NESTED_COLLECTION, -1_000L)
								.hasTokenAmount(TOKEN_WITH_NESTED_FEE, EDGAR, 1_000L)
								.hasTokenAmount(TOKEN_WITH_NESTED_FEE, TREASURY_FOR_TOP_LEVEL_COLLECTION, -1_000L),
						getTxnRecord(TXN_FROM_EDGAR)
								.hasTokenAmount(TOKEN_WITH_NESTED_FEE, FERN, 10L)
								.hasTokenAmount(TOKEN_WITH_NESTED_FEE, EDGAR, -10L)
								.hasAssessedCustomFee(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_TOP_LEVEL_COLLECTION, 50L)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_TOP_LEVEL_COLLECTION, 49L)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, EDGAR, -50L)
								.hasAssessedCustomFee(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_NESTED_COLLECTION, 1L)
								.hasTokenAmount(TOKEN_WITH_FRACTIONAL_FEE, TREASURY_FOR_NESTED_COLLECTION, 1L),
						getAccountBalance(FERN)
								.hasTokenBalance(TOKEN_WITH_NESTED_FEE, 10L),
						getAccountBalance(EDGAR)
								.hasTokenBalance(TOKEN_WITH_NESTED_FEE, 1_000L - 10L)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE, 1_000L - 50L),
						getAccountBalance(TREASURY_FOR_TOP_LEVEL_COLLECTION)
								.hasTokenBalance(TOKEN_WITH_NESTED_FEE, Long.MAX_VALUE - 1_000L)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE, 49L),
						getAccountBalance(TREASURY_FOR_NESTED_COLLECTION)
								.hasTokenBalance(TOKEN_WITH_FRACTIONAL_FEE, Long.MAX_VALUE - 1_000L + 1L)
				);
	}

	public HapiApiSpec nestedHtsCaseStudy() {
		return defaultHapiSpec("NestedHtsCaseStudy")
				.given(
						cryptoCreate(DEBBIE),
						cryptoCreate(EDGAR),
						cryptoCreate(TREASURY_FOR_TOP_LEVEL_COLLECTION),
						cryptoCreate(TREASURY_FOR_NESTED_COLLECTION),
						tokenCreate(FEE_TOKEN)
								.treasury(DEFAULT_PAYER)
								.initialSupply(Long.MAX_VALUE),
						tokenAssociate(TREASURY_FOR_NESTED_COLLECTION, FEE_TOKEN),
						tokenCreate(TOKEN_WITH_HTS_FEE)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TREASURY_FOR_NESTED_COLLECTION)
								.withCustom(fixedHtsFee(1L, FEE_TOKEN, TREASURY_FOR_NESTED_COLLECTION)),
						tokenAssociate(TREASURY_FOR_TOP_LEVEL_COLLECTION, TOKEN_WITH_HTS_FEE),
						tokenCreate(TOKEN_WITH_NESTED_FEE)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TREASURY_FOR_TOP_LEVEL_COLLECTION)
								.withCustom(fixedHtsFee(1L, TOKEN_WITH_HTS_FEE, TREASURY_FOR_TOP_LEVEL_COLLECTION)),
						tokenAssociate(DEBBIE, List.of(FEE_TOKEN, TOKEN_WITH_HTS_FEE, TOKEN_WITH_NESTED_FEE)),
						tokenAssociate(EDGAR, TOKEN_WITH_NESTED_FEE),
						cryptoTransfer(
								moving(1_000L, FEE_TOKEN)
										.between(DEFAULT_PAYER, DEBBIE),
								moving(1_000L, TOKEN_WITH_HTS_FEE)
										.between(TREASURY_FOR_NESTED_COLLECTION, DEBBIE),
								moving(1_000L, TOKEN_WITH_NESTED_FEE)
										.between(TREASURY_FOR_TOP_LEVEL_COLLECTION, DEBBIE)
						)
								.payingWith(TREASURY_FOR_NESTED_COLLECTION)
								.fee(ONE_HBAR)
								.via(TXN_FROM_TREASURY)
				).when(
						cryptoTransfer(
								moving(1L, TOKEN_WITH_NESTED_FEE).between(DEBBIE, EDGAR)
						)
								.payingWith(DEBBIE)
								.fee(ONE_HBAR)
								.via(TXN_FROM_DEBBIE)
				).then(
						getTxnRecord(TXN_FROM_TREASURY)
								.hasTokenAmount(FEE_TOKEN, DEBBIE, 1_000L)
								.hasTokenAmount(FEE_TOKEN, DEFAULT_PAYER, -1_000L)
								.hasTokenAmount(TOKEN_WITH_HTS_FEE, DEBBIE, 1_000L)
								.hasTokenAmount(TOKEN_WITH_HTS_FEE, TREASURY_FOR_NESTED_COLLECTION, -1_000L)
								.hasTokenAmount(TOKEN_WITH_NESTED_FEE, DEBBIE, 1_000L)
								.hasTokenAmount(TOKEN_WITH_NESTED_FEE, TREASURY_FOR_TOP_LEVEL_COLLECTION, -1_000L),
						getTxnRecord(TXN_FROM_DEBBIE)
								.hasTokenAmount(TOKEN_WITH_NESTED_FEE, EDGAR, 1L)
								.hasTokenAmount(TOKEN_WITH_NESTED_FEE, DEBBIE, -1L)
								.hasAssessedCustomFee(TOKEN_WITH_HTS_FEE, TREASURY_FOR_TOP_LEVEL_COLLECTION, 1L)
								.hasTokenAmount(TOKEN_WITH_HTS_FEE, TREASURY_FOR_TOP_LEVEL_COLLECTION, 1L)
								.hasTokenAmount(TOKEN_WITH_HTS_FEE, DEBBIE, -1L)
								.hasAssessedCustomFee(FEE_TOKEN, TREASURY_FOR_NESTED_COLLECTION, 1L)
								.hasTokenAmount(FEE_TOKEN, TREASURY_FOR_NESTED_COLLECTION, 1L)
								.hasTokenAmount(FEE_TOKEN, DEBBIE, -1L),
						getAccountBalance(EDGAR)
								.hasTokenBalance(TOKEN_WITH_NESTED_FEE, 1L),
						getAccountBalance(DEBBIE)
								.hasTokenBalance(FEE_TOKEN, 1_000L - 1L)
								.hasTokenBalance(TOKEN_WITH_HTS_FEE, 1_000L - 1L)
								.hasTokenBalance(TOKEN_WITH_NESTED_FEE, 1_000L - 1L),
						getAccountBalance(TREASURY_FOR_TOP_LEVEL_COLLECTION)
								.hasTokenBalance(TOKEN_WITH_HTS_FEE, 1L)
								.hasTokenBalance(TOKEN_WITH_NESTED_FEE, Long.MAX_VALUE - 1_000L),
						getAccountBalance(TREASURY_FOR_NESTED_COLLECTION)
								.hasTokenBalance(FEE_TOKEN, 1L)
								.hasTokenBalance(TOKEN_WITH_HTS_FEE, Long.MAX_VALUE - 1_000L),
						getAccountBalance(DEFAULT_PAYER)
								.hasTokenBalance(FEE_TOKEN, Long.MAX_VALUE - 1_000L)
				);
	}

	public HapiApiSpec treasuriesAreExemptFromAllCustomFees() {
		return defaultHapiSpec("TreasuriesAreExemptFromAllFees")
				.given(
						cryptoCreate(EDGAR),
						cryptoCreate(NON_TREASURY),
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(TREASURY_FOR_TOP_LEVEL),
						cryptoCreate(COLLECTOR_FOR_TOP_LEVEL).balance(0L),
						tokenCreate(FEE_TOKEN)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(COLLECTOR_FOR_TOP_LEVEL, FEE_TOKEN),
						tokenAssociate(TREASURY_FOR_TOP_LEVEL, FEE_TOKEN),
						tokenCreate(TOP_LEVEL_TOKEN)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TREASURY_FOR_TOP_LEVEL)
								.withCustom(fixedHbarFee(ONE_HBAR, COLLECTOR_FOR_TOP_LEVEL))
								.withCustom(fixedHtsFee(50L, FEE_TOKEN, COLLECTOR_FOR_TOP_LEVEL))
								.withCustom(fractionalFee(1L, 10L, 5L, OptionalLong.of(50L),
										false, COLLECTOR_FOR_TOP_LEVEL))
								.signedBy(DEFAULT_PAYER, TREASURY_FOR_TOP_LEVEL, COLLECTOR_FOR_TOP_LEVEL),
						tokenAssociate(NON_TREASURY, List.of(TOP_LEVEL_TOKEN, FEE_TOKEN)),
						tokenAssociate(EDGAR, TOP_LEVEL_TOKEN),
						cryptoTransfer(
								moving(2_000L, FEE_TOKEN)
										.distributing(TOKEN_TREASURY, TREASURY_FOR_TOP_LEVEL, NON_TREASURY)
						).payingWith(TOKEN_TREASURY).fee(ONE_HBAR)
				).when(
						cryptoTransfer(
								moving(1_000L, TOP_LEVEL_TOKEN)
										.between(TREASURY_FOR_TOP_LEVEL, NON_TREASURY)
						)
								.payingWith(TREASURY_FOR_TOP_LEVEL)
								.fee(ONE_HBAR)
								.via(TXN_FROM_TREASURY)
				).then(
						getTxnRecord(TXN_FROM_TREASURY)
								.hasTokenAmount(TOP_LEVEL_TOKEN, NON_TREASURY, 1_000L)
								.hasTokenAmount(TOP_LEVEL_TOKEN, TREASURY_FOR_TOP_LEVEL, -1_000L)
								.hasAssessedCustomFeesSize(0),
						getAccountBalance(COLLECTOR_FOR_TOP_LEVEL)
								.hasTinyBars(0L)
								.hasTokenBalance(FEE_TOKEN, 0L)
								.hasTokenBalance(TOP_LEVEL_TOKEN, 0L),
						getAccountBalance(TREASURY_FOR_TOP_LEVEL)
								.hasTokenBalance(TOP_LEVEL_TOKEN, Long.MAX_VALUE - 1_000L)
								.hasTokenBalance(FEE_TOKEN, 1_000L),
						getAccountBalance(NON_TREASURY)
								.hasTokenBalance(TOP_LEVEL_TOKEN, 1_000L)
								.hasTokenBalance(FEE_TOKEN, 1_000L),
						/* Now we perform the same transfer from a non-treasury and see all three fees charged */
						cryptoTransfer(
								moving(1_000L, TOP_LEVEL_TOKEN)
										.between(NON_TREASURY, EDGAR)
						)
								.payingWith(TOKEN_TREASURY)
								.fee(ONE_HBAR)
								.via(TXN_FROM_NON_TREASURY),
						getTxnRecord(TXN_FROM_NON_TREASURY)
								.hasAssessedCustomFeesSize(3)
								.hasTokenAmount(TOP_LEVEL_TOKEN, EDGAR, 1_000L - 50L)
								.hasTokenAmount(TOP_LEVEL_TOKEN, NON_TREASURY, -1_000L)
								.hasAssessedCustomFee(TOP_LEVEL_TOKEN, COLLECTOR_FOR_TOP_LEVEL, 50L)
								.hasTokenAmount(TOP_LEVEL_TOKEN, COLLECTOR_FOR_TOP_LEVEL, 50L)
								.hasAssessedCustomFee(HBAR_TOKEN_SENTINEL, COLLECTOR_FOR_TOP_LEVEL, ONE_HBAR)
								.hasHbarAmount(COLLECTOR_FOR_TOP_LEVEL, ONE_HBAR)
								.hasHbarAmount(NON_TREASURY, -ONE_HBAR)
								.hasAssessedCustomFee(FEE_TOKEN, COLLECTOR_FOR_TOP_LEVEL, 50L)
								.hasTokenAmount(FEE_TOKEN, COLLECTOR_FOR_TOP_LEVEL, 50L)
								.hasTokenAmount(FEE_TOKEN, NON_TREASURY, -50L),
						getAccountBalance(COLLECTOR_FOR_TOP_LEVEL)
								.hasTinyBars(ONE_HBAR)
								.hasTokenBalance(FEE_TOKEN, 50L)
								.hasTokenBalance(TOP_LEVEL_TOKEN, 50L),
						getAccountBalance(EDGAR)
								.hasTokenBalance(TOP_LEVEL_TOKEN, 1_000L - 50L),
						getAccountBalance(NON_TREASURY)
								.hasTokenBalance(TOP_LEVEL_TOKEN, 1_000L - 1_000L)
								.hasTokenBalance(FEE_TOKEN, 1_000L - 50L)
				);
	}

	public HapiApiSpec collectorsAreExemptFromTheirOwnFeesButNotOthers() {
		return defaultHapiSpec("CollectorsAreExemptFromTheirOwnFeesButNotOthers")
				.given(
						cryptoCreate(EDGAR),
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(TREASURY_FOR_TOP_LEVEL),
						cryptoCreate(FIRST_COLLECTOR_FOR_TOP_LEVEL).balance(10 * ONE_HBAR),
						cryptoCreate(SECOND_COLLECTOR_FOR_TOP_LEVEL).balance(10 * ONE_HBAR),
						tokenCreate(TOP_LEVEL_TOKEN)
								.initialSupply(Long.MAX_VALUE)
								.treasury(TREASURY_FOR_TOP_LEVEL)
								.withCustom(fixedHbarFee(ONE_HBAR, FIRST_COLLECTOR_FOR_TOP_LEVEL))
								.withCustom(fixedHbarFee(2 * ONE_HBAR, SECOND_COLLECTOR_FOR_TOP_LEVEL))
								.withCustom(fractionalFee(1L, 20L, 0L, OptionalLong.of(0L),
										false, FIRST_COLLECTOR_FOR_TOP_LEVEL))
								.withCustom(fractionalFee(1L, 10L, 0L, OptionalLong.of(0L),
										false, SECOND_COLLECTOR_FOR_TOP_LEVEL))
								.signedBy(DEFAULT_PAYER, TREASURY_FOR_TOP_LEVEL, FIRST_COLLECTOR_FOR_TOP_LEVEL,
										SECOND_COLLECTOR_FOR_TOP_LEVEL),
						tokenAssociate(EDGAR, TOP_LEVEL_TOKEN),
						cryptoTransfer(moving(2_000L, TOP_LEVEL_TOKEN)
								.distributing(TREASURY_FOR_TOP_LEVEL, FIRST_COLLECTOR_FOR_TOP_LEVEL,
										SECOND_COLLECTOR_FOR_TOP_LEVEL))
				).when(
						cryptoTransfer(
								moving(1_000L, TOP_LEVEL_TOKEN)
										.between(FIRST_COLLECTOR_FOR_TOP_LEVEL, EDGAR)
						)
								.payingWith(FIRST_COLLECTOR_FOR_TOP_LEVEL)
								.fee(ONE_HBAR)
								.via(TXN_FROM_COLLECTOR)
				).then(
						getTxnRecord(TXN_FROM_COLLECTOR)
								.hasAssessedCustomFeesSize(2)
								.hasTokenAmount(TOP_LEVEL_TOKEN, EDGAR, 1_000L - 100L)
								.hasTokenAmount(TOP_LEVEL_TOKEN, FIRST_COLLECTOR_FOR_TOP_LEVEL, -1_000L)
								.hasAssessedCustomFee(TOP_LEVEL_TOKEN, SECOND_COLLECTOR_FOR_TOP_LEVEL, 100L)
								.hasTokenAmount(TOP_LEVEL_TOKEN, SECOND_COLLECTOR_FOR_TOP_LEVEL, 100L)
								.hasAssessedCustomFee(HBAR_TOKEN_SENTINEL, SECOND_COLLECTOR_FOR_TOP_LEVEL, 2 * ONE_HBAR)
								.hasHbarAmount(SECOND_COLLECTOR_FOR_TOP_LEVEL, 2 * ONE_HBAR),
						getAccountBalance(FIRST_COLLECTOR_FOR_TOP_LEVEL)
								.hasTokenBalance(TOP_LEVEL_TOKEN, 1_000L - 1_000L),
						getAccountBalance(SECOND_COLLECTOR_FOR_TOP_LEVEL)
								.hasTinyBars((10 + 2) * ONE_HBAR)
								.hasTokenBalance(TOP_LEVEL_TOKEN, 1_000L + 100L),
						getAccountBalance(EDGAR)
								.hasTokenBalance(TOP_LEVEL_TOKEN, 1_000L - 100L)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
