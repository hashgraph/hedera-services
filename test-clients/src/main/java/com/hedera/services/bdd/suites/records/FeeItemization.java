package com.hedera.services.bdd.suites.records;

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

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;

import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.math.BigInteger;
import java.util.List;
import java.util.function.BiConsumer;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static java.util.stream.Collectors.toList;

public class FeeItemization extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FeeItemization.class);

	public static void main(String... args) {
		new FeeItemization().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						transferWithUninvolvedPayerItemizesFees(),
				}
		);
	}

	private BiConsumer<TransactionRecord, Logger> conciseAdjustments = (record, opLog) ->
			opLog.info(readableTransferList(record.getTransferList()));

	private HapiApiSpec transferWithUninvolvedPayerItemizesFees() {
		return defaultHapiSpec("TransferWithUninvolvedPayerItemizesFees")
				.given(
						cryptoCreate("sender").sendThreshold(0L),
						cryptoCreate("receiver").receiveThreshold(0L),
						cryptoCreate("payer").sendThreshold(0L)
				).when(
						cryptoTransfer(tinyBarsFromTo("sender", "receiver", 100_000))
								.payingWith("payer")
								.memo("---")
								.via("txn"),
						cryptoTransfer(tinyBarsFromTo("sender", "receiver", 100_000))
								.payingWith("payer")
								.memo("-IF")
								.via("txnRequestingItemizedFees")
				).then(
						getTxnRecord("txn").loggedWith(conciseAdjustments),
						getTxnRecord("txnRequestingItemizedFees").loggedWith(conciseAdjustments)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	public static String readableTransferList(TransferList accountAmounts) {
		return accountAmounts.getAccountAmountsList()
				.stream()
				.map(aa -> String.format(
						"%s %s %s%s",
						readableAccountId(aa.getAccountID()),
						aa.getAmount() < 0 ? "->" : "<-",
						aa.getAmount() < 0 ? "-" : "+",
						BigInteger.valueOf(aa.getAmount()).abs().toString()))
				.collect(toList())
				.toString();
	}

	public static String readableAccountId(AccountID id) {
		return String.format("%d.%d.%d", id.getShardNum(), id.getRealmNum(), id.getAccountNum());
	}
}

