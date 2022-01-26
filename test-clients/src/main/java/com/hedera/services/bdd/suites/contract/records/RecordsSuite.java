package com.hedera.services.bdd.suites.contract.records;

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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;

public class RecordsSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(RecordsSuite.class);

	public static void main(String... args) {
		new RecordsSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[]{
				bigCall(),
				txRecordsContainValidTransfers(),
				invalidContract()
		});
	}

	HapiApiSpec bigCall() {
		int byteArraySize = (int) (87.5 * 1_024);

		return defaultHapiSpec("BigRecord")
				.given(
						cryptoCreate("payer").balance(10 * ONE_HUNDRED_HBARS),
						fileCreate("bytecode")
								.path(ContractResources.BIG_BIG_BYTECODE_PATH),
						contractCreate("bigBig")
								.bytecode("bytecode")
				).when(
						contractCall("bigBig", ContractResources.PICK_A_BIG_RESULT_ABI, byteArraySize)
								.payingWith("payer")
								.gas(400_000L)
								.via("bigCall")
				).then(
						getTxnRecord("bigCall")
				);
	}

	HapiApiSpec txRecordsContainValidTransfers() {
		return defaultHapiSpec("TXRecordsContainValidTransfers")
				.given(
						fileCreate("payReceivableCode")
								.path(ContractResources.PARENT_CHILD_TRANSFER_BYTECODE_PATH)
				).when(
						contractCreate("payReceivable")
								.bytecode("payReceivableCode")
								.balance(10_000L)
								.via("createTx"),
						contractCall("payReceivable",
								ContractResources.PARENT_CHILD_TRANSFER_TRANSFER_TO_CHILD_ABI, 10_000)
								.via("transferTx")
				).then(
						assertionsHold((spec, ctxLog) -> {
							var subop01 = getTxnRecord("createTx")
									.saveTxnRecordToRegistry("createTxRec");
							var subop02 = getTxnRecord("transferTx")
									.saveTxnRecordToRegistry("transferTxRec");
							CustomSpecAssert.allRunFor(spec, subop01, subop02);

							TransactionRecord createRecord = spec.registry().getTransactionRecord("createTxRec");
							var parent = createRecord.getContractCreateResult().getCreatedContractIDs(0);
							var child = createRecord.getContractCreateResult().getCreatedContractIDs(1);

							// validate transfer list
							List<AccountAmount> expectedTransfers = new ArrayList<>(2);
							AccountAmount receiverTransfer = AccountAmount.newBuilder().setAccountID(
											AccountID.newBuilder().setAccountNum(parent.getContractNum()).build())
									.setAmount(-10_000L).build();
							expectedTransfers.add(receiverTransfer);
							AccountAmount contractTransfer = AccountAmount.newBuilder().setAccountID(
											AccountID.newBuilder().setAccountNum(child.getContractNum()).build())
									.setAmount(10_000L).build();
							expectedTransfers.add(contractTransfer);

							TransactionRecord transferRecord = spec.registry().getTransactionRecord("transferTxRec");

							TransferList transferList = transferRecord.getTransferList();
							Assertions.assertNotNull(transferList);
							Assertions.assertNotNull(transferList.getAccountAmountsList());
							Assertions.assertTrue(transferList.getAccountAmountsList().containsAll(expectedTransfers));
							long amountSum = sumAmountsInTransferList(transferList.getAccountAmountsList());
							Assertions.assertEquals(0, amountSum);
						})
				);
	}

	private long sumAmountsInTransferList(List<AccountAmount> transferList) {
		long sumToReturn = 0L;
		for (AccountAmount currAccAmount : transferList) {
			sumToReturn += currAccAmount.getAmount();
		}
		return sumToReturn;
	}

	HapiApiSpec invalidContract() {
		String invalidContract = HapiSpecSetup.getDefaultInstance().invalidContractName();

		return defaultHapiSpec("InvalidContract")
				.given().when().then(
						QueryVerbs.getContractRecords(invalidContract)
								.hasCostAnswerPrecheck(INVALID_CONTRACT_ID));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
