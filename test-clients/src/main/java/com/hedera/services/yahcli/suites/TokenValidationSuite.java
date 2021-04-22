package com.hedera.services.yahcli.suites;

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
import com.hedera.services.bdd.spec.persistence.SpecKey;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.persistence.SpecKey.adminKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.freezeKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.kycKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.supplyKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.wipeKeyFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.MUTABLE_SCHEDULE;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.PAYER;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.RECEIVER;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.TOKEN;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.TREASURY;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.checkBoxed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;

public class TokenValidationSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenValidationSuite.class);

	private final Map<String, String> specConfig;

	public TokenValidationSuite(Map<String, String> specConfig) {
		this.specConfig = specConfig;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				validateTokens(),
		});
	}

	private HapiApiSpec validateTokens() {
		AtomicLong initialTreasuryBalance = new AtomicLong();
		return customHapiSpec("validateTokens").withProperties(specConfig)
				.given(
						getTokenInfo(TOKEN)
								.payingWith(PAYER)
								.hasName("Hedera Post-Update Validation Token")
								.hasSymbol("TACOCAT")
								.hasTreasury(TREASURY)
								.hasFreezeDefault(Unfrozen)
								.hasKycDefault(Revoked)
								.hasWipeKey(TOKEN)
								.hasSupplyKey(TOKEN)
								.hasFreezeKey(TOKEN)
								.hasAdminKey(TOKEN)
								.hasKycKey(TOKEN),
						getAccountBalance(TREASURY)
								.payingWith(PAYER)
								.savingTokenBalance(TOKEN, initialTreasuryBalance::set),
						logIt(checkBoxed("Token entities look good"))
				).when(
						tokenDissociate(RECEIVER, TOKEN)
								.payingWith(PAYER)
								.hasKnownStatusFrom(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, SUCCESS),
						tokenAssociate(RECEIVER, TOKEN)
								.payingWith(PAYER),
						tokenFreeze(TOKEN, RECEIVER)
								.payingWith(PAYER),
						cryptoTransfer(moving(1, TOKEN).between(TREASURY, RECEIVER))
								.payingWith(PAYER)
								.hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
						tokenUnfreeze(TOKEN, RECEIVER)
								.payingWith(PAYER),
						cryptoTransfer(moving(1, TOKEN).between(TREASURY, RECEIVER))
								.payingWith(PAYER)
								.hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN),
						grantTokenKyc(TOKEN, RECEIVER)
								.payingWith(PAYER),
						mintToken(TOKEN, 2)
								.payingWith(PAYER),
						cryptoTransfer(moving(1, TOKEN).between(TREASURY, RECEIVER))
								.payingWith(PAYER),
						logIt(checkBoxed("Token management looks good"))
				).then(
						getAccountBalance(RECEIVER)
								.payingWith(PAYER)
								.hasTokenBalance(TOKEN, 1L),
						sourcing(() -> getAccountBalance(TREASURY)
								.payingWith(PAYER)
								.hasTokenBalance(TOKEN, 1L + initialTreasuryBalance.get())),
						wipeTokenAccount(TOKEN, RECEIVER, 1)
								.payingWith(PAYER),
						burnToken(TOKEN, 1L)
								.payingWith(PAYER),
						getAccountBalance(RECEIVER)
								.payingWith(PAYER)
								.hasTokenBalance(TOKEN, 0L),
						sourcing(() -> getAccountBalance(TREASURY)
								.payingWith(PAYER)
								.hasTokenBalance(TOKEN, initialTreasuryBalance.get())),
						logIt(checkBoxed("Token balance changes looks good"))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
