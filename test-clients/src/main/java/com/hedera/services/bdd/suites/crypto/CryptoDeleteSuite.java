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

import java.util.List;

import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenTransact;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenTransact;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.including;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;

public class CryptoDeleteSuite extends HapiApiSuite {
	static final Logger log = LogManager.getLogger(CryptoDeleteSuite.class);
	private static final long TOKEN_INITIAL_SUPPLY = 500;

	public static void main(String... args) {
		new CryptoDeleteSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				fundsTransferOnDelete(),
				cannotDeleteAccountsWithNonzeroTokenBalances(),
				cannotDeleteAlreadyDeletedAccount(),
				cannotDeleteAccountWithSameBeneficiary(),
				cannotDeleteTreasuryAccount(),
		});
	}


	private HapiApiSpec fundsTransferOnDelete() {
		long B = HapiSpecSetup.getDefaultInstance().defaultBalance();

		return defaultHapiSpec("FundsTransferOnDelete")
				.given(
						cryptoCreate("toBeDeleted"),
						cryptoCreate("transferAccount").balance(0L)
				).when(
						cryptoDelete("toBeDeleted")
								.transfer("transferAccount")
								.via("deleteTxn")
				).then(
						getAccountInfo("transferAccount")
								.has(accountWith().balance(B)),
						getTxnRecord("deleteTxn")
								.hasPriority(recordWith().transfers(including(
										tinyBarsFromTo("toBeDeleted", "transferAccount", B)))));
	}

	private HapiApiSpec cannotDeleteAccountsWithNonzeroTokenBalances() {
		return defaultHapiSpec("CannotDeleteAccountsWithNonzeroTokenBalances")
				.given(
						cryptoCreate("toBeDeleted"),
						cryptoCreate("transferAccount"),
						cryptoCreate(TOKEN_TREASURY)
				).when(
						tokenCreate("misc")
								.initialSupply(TOKEN_INITIAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						tokenAssociate("toBeDeleted", "misc"),
						tokenTransact(HapiTokenTransact.TokenMovement
								.moving(TOKEN_INITIAL_SUPPLY, "misc")
								.between(TOKEN_TREASURY, "toBeDeleted"))
				).then(
						cryptoDelete("toBeDeleted")
								.transfer("transferAccount")
								.hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
				);
	}

	private HapiApiSpec cannotDeleteAlreadyDeletedAccount() {
		return defaultHapiSpec("CannotDeleteAlreadyDeletedAccount")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("toBeDeleted"),
						cryptoCreate("transferAccount")
				)
				.when(
						cryptoDelete("toBeDeleted")
								.transfer("transferAccount")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						cryptoDelete("toBeDeleted")
								.transfer("transferAccount")
								.hasKnownStatus(ACCOUNT_DELETED)
				);
	}

	private HapiApiSpec cannotDeleteAccountWithSameBeneficiary() {
		return defaultHapiSpec("CannotDeleteAccountWithSameBeneficiary")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("toBeDeleted"),
						cryptoCreate("transferAccount")
				)
				.when()
				.then(
						cryptoDelete("toBeDeleted")
								.transfer("toBeDeleted")
								.hasPrecheck(TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT)
						);
	}

	private HapiApiSpec cannotDeleteTreasuryAccount() {
		return defaultHapiSpec("CannotDeleteTreasuryAccount")
				.given(
						cryptoCreate("treasury"),
						cryptoCreate("transferAccount")
				)
				.when(
						tokenCreate("toBeTransferred")
								.initialSupply(TOKEN_INITIAL_SUPPLY)
								.treasury("treasury")
				)
				.then(
						cryptoDelete("treasury")
								.transfer("transferAccount")
								.hasKnownStatus(ACCOUNT_IS_TREASURY)
						);
	}
}
