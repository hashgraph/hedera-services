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
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;

public class AutoAccountCreationSuite extends HapiApiSuite {
	private static final long initialBalance = 1000L;
	private static final ByteString aliasContent = ByteString.copyFromUtf8(
			"a479462fba67674b5a41acfb16cb6828626b61d3f389fa611005a45754130e5c749073c0b1b791596430f4a54649cc8a3f6d28147dd4099070a5c3c4811d1771");
	private static final Key validEd25519Key = Key.newBuilder().setEd25519(aliasContent).build();
	private static final ByteString valid25519Alias = validEd25519Key.toByteString();

	private static final Logger log = LogManager.getLogger(AutoAccountCreationSuite.class);

	public static void main(String... args) {
		new AutoAccountCreationSuite().runSuiteAsync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						autoAccountCreationsHappyPath(),
						autoAccountCreationBadAlias(),
						autoAccountCreationAliasAsThresholdKey(),
						autoAccountCreationAliasAsKeyList(),
						autoAccountCreationAliasAsContractID()
				}
		);
	}

	private HapiApiSpec autoAccountCreationAliasAsContractID() {
		Key contractKey = Key.newBuilder().setContractID(ContractID.newBuilder().setContractNum(100L)).build();
		final ByteString contractAlias = contractKey.toByteString();
		return defaultHapiSpec("autoAccountCreationsHappyPath")
				.given(
						UtilVerbs.inParallel(
								cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
						)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("payer", contractAlias, ONE_HUNDRED_HBARS)
						).via("transferTxn").hasPrecheck(ResponseCodeEnum.INVALID_ALIAS_KEY)
				).then(
				);
	}

	private HapiApiSpec autoAccountCreationAliasAsKeyList() {
		Key keyList = Key.newBuilder()
				.setKeyList(KeyList.newBuilder()
						.addKeys(Key.newBuilder()
								.setEd25519(ByteString.copyFrom("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes())))
						.addKeys(Key.newBuilder()
								.setECDSASecp256K1(
										ByteString.copyFrom("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes()))))
				.build();
		final ByteString keyListAlias = keyList.toByteString();
		return defaultHapiSpec("autoAccountCreationsHappyPath")
				.given(
						UtilVerbs.inParallel(
								cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
						)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("payer", keyListAlias, ONE_HUNDRED_HBARS)
						).via("transferTxn").hasPrecheck(ResponseCodeEnum.INVALID_ALIAS_KEY)
				).then();
	}

	private HapiApiSpec autoAccountCreationAliasAsThresholdKey() {
		Key threshKey = Key.newBuilder()
				.setThresholdKey(ThresholdKey.newBuilder()
						.setThreshold(2)
						.setKeys(KeyList.newBuilder()
								.addKeys(Key.newBuilder()
										.setEd25519(ByteString.copyFrom("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes())))
								.addKeys(Key.newBuilder()
										.setECDSASecp256K1(
												ByteString.copyFrom("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes())))
								.addKeys(Key.newBuilder()
										.setEd25519(
												ByteString.copyFrom("cccccccccccccccccccccccccccccccc".getBytes())))))
				.build();
		final ByteString thresholdAlias = threshKey.toByteString();
		return defaultHapiSpec("autoAccountCreationsHappyPath")
				.given(
						UtilVerbs.inParallel(
								cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
						)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("payer", thresholdAlias, ONE_HUNDRED_HBARS)
						).via("transferTxn").hasPrecheck(ResponseCodeEnum.INVALID_ALIAS_KEY)
				).then();
	}

	private HapiApiSpec autoAccountCreationBadAlias() {
		final var invalidAlias = valid25519Alias.substring(0, 10);
		return defaultHapiSpec("autoAccountCreationBadAlias")
				.given(
						UtilVerbs.inParallel(
								cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
						)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("payer", invalidAlias, ONE_HUNDRED_HBARS)
						).hasPrecheck(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxn")
				).then();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	private HapiApiSpec autoAccountCreationsHappyPath() {
		return defaultHapiSpec("autoAccountCreationsHappyPath")
				.given(
						UtilVerbs.inParallel(
								cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
						)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("payer", valid25519Alias, ONE_HUNDRED_HBARS)
						).via("transferTxn")
				).then(
						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAccountInfo("payer").has(
								accountWith().balance((initialBalance * ONE_HBAR) - ONE_HUNDRED_HBARS)),
						getAccountInfo(valid25519Alias).has(
								accountWith().expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.1))
				);
	}

}
