package com.hedera.services.bdd.suites.fees;

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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;

public class AllBaseTokenOpFeesSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AllBaseTokenOpFeesSuite.class);

	private static final double ALLOWED_DIFFERENCE_PERCENTAGE = 0.01;

	private static final String TREASURE_KEY = "treasureKey";
	private static final String FUNGIBLE_COMMON_TOKEN = "fungibleCommonToken";

	private static final String ADMIN_KEY = "adminKey";
	private static final String SUPPLY_KEY = "supplyKey";
	private static final String FREEZE_KEY = "freezeKey";
	private static final String WIPE_KEY = "wipeKey";
	private static final String KYC_KEY = "kycKey";

	private static final String CIVILIAN_ACCT = "civilian";
	private static final String COMMON_TOKEN = "FCType";
	private static final String UNIQUE_TOKEN = "nftType";
	private static final String BASE_TXN = "baseTxn";

	private static final double EXPECTED_TOKEN_GRANTKYC_PRICE_USD = 0.001;
	private static final double EXPECTED_TOKEN_REVOKEKYC_PRICE_USD = 0.001;
	private static final double EXPECTED_TOKEN_ASSOCIATE_PRICE_USD = 0.05;
	private static final double EXPECTED_TOKEN_DISSOCIATE_PRICE_USD = 0.05;

	public static void main(String... args) {
		new AllBaseTokenOpFeesSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(List.of(new HapiApiSpec[] {
				baseTokenGrantRevokeKycChargedAsExpected(),
				baseTokenAssociateDissociateCharedAsExpected()
				}
			)
		);
	}


	private HapiApiSpec baseTokenGrantRevokeKycChargedAsExpected() {
		return defaultHapiSpec("baseGrantKycChargedAsExpected")
				.given(
						newKeyNamed(TREASURE_KEY),
						newKeyNamed(ADMIN_KEY),
						newKeyNamed(KYC_KEY),
						cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS).key(TREASURE_KEY),
						cryptoCreate(CIVILIAN_ACCT),
						tokenCreate(FUNGIBLE_COMMON_TOKEN)
								.kycKey(TREASURE_KEY)
								.treasury(TOKEN_TREASURY)
				).when(
						grantTokenKyc(FUNGIBLE_COMMON_TOKEN, TOKEN_TREASURY)
								.payingWith(TOKEN_TREASURY)
								.blankMemo()
								.via("grantKycTxn"),
						revokeTokenKyc(FUNGIBLE_COMMON_TOKEN, TOKEN_TREASURY)
								.payingWith(TOKEN_TREASURY)
								.blankMemo()
								.via("revokeKycTxn")
				).then(
						validateChargedUsdWithin("grantKycTxn", EXPECTED_TOKEN_GRANTKYC_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE),
						validateChargedUsdWithin("revokeKycTxn", EXPECTED_TOKEN_REVOKEKYC_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE)
				);
	}

	public HapiApiSpec baseTokenAssociateDissociateCharedAsExpected() {
		return defaultHapiSpec("baseTokenAssociateDissociateCharedAsExpected")
				.given(
						newKeyNamed(TREASURE_KEY),
						cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS).key(TREASURE_KEY),
						cryptoCreate(CIVILIAN_ACCT)
								.key(TREASURE_KEY)
								.autoRenewSecs(THREE_MONTHS_IN_SECONDS),

						tokenCreate(COMMON_TOKEN)
								.treasury(TOKEN_TREASURY)
								.payingWith(TOKEN_TREASURY)
				).when(
						tokenAssociate(CIVILIAN_ACCT, COMMON_TOKEN)
								.payingWith(TOKEN_TREASURY)
								.blankMemo()
								.via("tokenAssociateTxn"),
						tokenDissociate(CIVILIAN_ACCT, COMMON_TOKEN)
								.payingWith(TOKEN_TREASURY)
								.blankMemo()
								.via("tokenDissociateTxn")
				).then(
						validateChargedUsdWithin("tokenAssociateTxn", EXPECTED_TOKEN_ASSOCIATE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE),
						validateChargedUsdWithin("tokenDissociateTxn", EXPECTED_TOKEN_DISSOCIATE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
