package com.hedera.services.bdd.suites.contract;

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
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateTransferListForBalances;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;

public class SmartContractPaySpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SmartContractPaySpec.class);

	final String PATH_TO_PAY_RECEIVABLE_BYTECODE = "src/main/resource/PayReceivableAmount.bin";

	private static final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String SC_GET_BALANCE = "{\"constant\":true,\"inputs\":[],\"name\":\"getBalance\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	private static final String SC_DEPOSIT = "{\"constant\":false,\"inputs\":[{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"deposit\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";
	private static final String SC_GET_BALANCE_OF = "{\"constant\":true,\"inputs\":[{\"name\":\"accToCheck\",\"type\":\"address\"}],\"name\":\"getBalanceOf\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	private static final String SC_SEND_FUNDS = "{\"constant\":false,\"inputs\":[{\"name\":\"receiver\",\"type\":\"address\"},{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"sendFunds\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static void main(String... args) {
		new org.ethereum.crypto.HashUtil();

		new SmartContractPaySpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				smartContractPaySpec()
		});
	}

	HapiApiSpec smartContractPaySpec() {

		return defaultHapiSpec("smartContractPaySpec")
				.given(
						cryptoCreate("payer")
								.balance( 10_000_000_000_000L),
						cryptoCreate("receiver")
								.balance( 0L),
						fileCreate("payReceivableCode")
								.path(PATH_TO_PAY_RECEIVABLE_BYTECODE)
				).when(
						contractCreate("payReceivable")
								.payingWith("payer")
								.gas(300_000L)
								.bytecode("payReceivableCode")
								.via("payReceivableTxn")

				).then(
						assertionsHold((spec, ctxLog) -> {

							var subop1 = getContractInfo("payReceivable")
									.nodePayment(10L)
									.saveToRegistry("payReceivableKey");

							var subop2 = getAccountInfo("receiver")
									.saveToRegistry("receiverAccountInfoKey");
							CustomSpecAssert.allRunFor(spec, subop1, subop2);

							ContractGetInfoResponse.ContractInfo payReceivableContratInfo = spec.registry().getContractInfo("payReceivableKey");
							CryptoGetInfoResponse.AccountInfo receiverAccountInfo = spec.registry().getAccountInfo("receiverAccountInfoKey");
							String receiverAcctAddress = receiverAccountInfo.getContractAccountID();
							ctxLog.info("Receiver balance before {}", receiverAccountInfo.getBalance());

							var subop3 = contractCall("payReceivable", SC_DEPOSIT, 10_000L)
									.payingWith("payer")
									.gas(300_000L)
									.via("depositTxn")
									.sending(10_000L);


							var subop4 = contractCall("payReceivable", SC_SEND_FUNDS, receiverAcctAddress, 10_000L)
									.sending(10_000L)
									.via("sendFundTxn")
									.payingWith("payer");

							// Not sure why this doesn't work here.
							// var subop5 = validateTransferListForBalances("sendFundTxn", List.of(  "payer", "receiver", NODE,  "payReceivable"));

							var subop5 = getTxnRecord("sendFundTxn")
									.saveTxnRecordToRegistry("sendFundTxnRecord");

							CustomSpecAssert.allRunFor(spec,  subop3, subop4, subop5);
							TransactionRecord txnRecord = spec.registry().getTransactionRecord("sendFundTxnRecord");

							// validate transfer list
							List<AccountAmount> expectedTransfers = new ArrayList<>(3);
							AccountAmount receiverTransfer = AccountAmount.newBuilder().setAccountID(receiverAccountInfo.getAccountID()).setAmount(10_000L).build();
							expectedTransfers.add(receiverTransfer);
							AccountAmount contractTransfer = AccountAmount.newBuilder().setAccountID(payReceivableContratInfo.getAccountID()).setAmount(15_000L).build();
							expectedTransfers.add(contractTransfer);

							TransferList transferList = txnRecord.getTransferList();
							Assert.assertNotNull(transferList);
							Assert.assertNotNull(transferList.getAccountAmountsList());
							assert(transferList.getAccountAmountsList().containsAll(expectedTransfers));
							long amountSum =  sumAmountsInTransferList(transferList.getAccountAmountsList());
							Assert.assertEquals(0, amountSum);

							// check final receiver balance
							var subop6 = getAccountInfo("receiver")
									.saveToRegistry("receiverAccountInfoKey");
							CustomSpecAssert.allRunFor(spec, subop6);

							receiverAccountInfo = spec.registry().getAccountInfo("receiverAccountInfoKey");
							ctxLog.info("Receiver balance {}", receiverAccountInfo.getBalance());
							Assert.assertEquals(
									"Receiver balance at the end: ",
									10000, receiverAccountInfo.getBalance());
						})
				);
	}

	private long sumAmountsInTransferList(List<AccountAmount> transferList) {
		long sumToReturn = 0L;
		for(AccountAmount currAccAmount :transferList) {
			sumToReturn +=  currAccAmount.getAmount();
		}
		return sumToReturn;
	}
	@Override
	protected Logger getResultsLogger() {
		return log;
	}


}
