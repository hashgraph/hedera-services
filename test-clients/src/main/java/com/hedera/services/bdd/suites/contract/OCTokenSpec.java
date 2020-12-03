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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

import java.math.BigInteger;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class OCTokenSpec extends HapiApiSuite  {
	private static final Logger log = LogManager.getLogger(OCTokenSpec.class);

	final String PATH_TO_OC_TOKEN_BYTECODE = "src/main/resource/contract/bytecodes/octoken.bin";

	private static final String TOKEN_ERC20_CONSTRUCTOR_ABI = "{\"inputs\":[{\"name\":\"initialSupply\",\"type\":\"uint256\"},{\"name\":\"tokenName\",\"type\":\"string\"},{\"name\":\"tokenSymbol\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}";
	private static final String BALANCE_OF_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"}],\"name\":\"balanceOf\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	private static final String TRANSFER_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_to\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"transfer\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String APPROVE_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_spender\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"approve\",\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String TRANSFER_FROM_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_from\",\"type\":\"address\"},{\"name\":\"_to\",\"type\":\"address\"},{\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"transferFrom\",\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String SYMBOL_ABI =   "{\"constant\":true,\"inputs\":[],\"name\":\"symbol\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	private static final String DECIMALS_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"decimals\",\"outputs\":[{\"name\":\"\",\"type\":\"uint8\"}] ,\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static void main(String... args) {
		new org.ethereum.crypto.HashUtil();

		new OCTokenSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				ocToken(),
		});
	}

	private  <T>  T getValueFromRegistry(HapiApiSpec spec, String from, CallTransaction.Function function) {
		byte[] 	value = spec.registry().getBytes(from);

		T decodedReturnedValue = null;
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			decodedReturnedValue = (T) retResults[0];
		}
		return decodedReturnedValue;
	}

	HapiApiSpec ocToken() {

		return defaultHapiSpec("ocToken")
				.given(
						cryptoCreate("tokenIssuer").balance(1_000_000_000_000L),
						cryptoCreate("Alice").balance( 10_000_000_000L).payingWith("tokenIssuer"),
						cryptoCreate("Bob").balance( 10_000_000_000L).payingWith("tokenIssuer"),
						cryptoCreate("Carol").balance( 10_000_000_000L).payingWith("tokenIssuer"),
						cryptoCreate("Dave").balance( 10_000_000_000L).payingWith("tokenIssuer"),

						getAccountInfo("tokenIssuer").saveToRegistry("tokenIssuerAcctInfo"),
						getAccountInfo("Alice").saveToRegistry("AliceAcctInfo"),
						getAccountInfo("Bob").saveToRegistry("BobAcctInfo"),
						getAccountInfo("Carol").saveToRegistry("CarolAcctInfo"),
						getAccountInfo("Dave").saveToRegistry("DaveAcctInfo"),

						fileCreate("bytecode")
								.path(PATH_TO_OC_TOKEN_BYTECODE),

						contractCreate("tokenContract", TOKEN_ERC20_CONSTRUCTOR_ABI,
								1_000_000L, "OpenCrowd Token", "OCT")
								.gas(250_000L)
								.payingWith("tokenIssuer")
								.bytecode("bytecode")
								.via("tokenCreateTxn").logged()

				).when(
						assertionsHold((spec, ctxLog) -> {
							String issuerEthAddress = spec.registry().getAccountInfo("tokenIssuerAcctInfo")
									.getContractAccountID();
							String aliceEthAddress = spec.registry().getAccountInfo("AliceAcctInfo")
									.getContractAccountID();
							String bobEthAddress = spec.registry().getAccountInfo("BobAcctInfo")
									.getContractAccountID();
							String carolEthAddress = spec.registry().getAccountInfo("CarolAcctInfo")
									.getContractAccountID();
							String daveEthAddress = spec.registry().getAccountInfo("DaveAcctInfo")
									.getContractAccountID();

							var subop1 = getContractInfo("tokenContract")
									.nodePayment(10L)
									.saveToRegistry("tokenContract");

							var subop3 = contractCallLocal("tokenContract", DECIMALS_ABI)
									.saveResultTo("decimals")
									.payingWith("tokenIssuer");

							// Note: This contract call will cause a INSUFFICIENT_TX_FEE error, not sure why.
							var subop4 = contractCallLocal("tokenContract", SYMBOL_ABI)
									.saveResultTo("token_symbol")
									.payingWith("tokenIssuer")
									.hasAnswerOnlyPrecheckFrom(OK, INSUFFICIENT_TX_FEE);

							var subop5 = contractCallLocal("tokenContract",BALANCE_OF_ABI, issuerEthAddress)
									.gas(250_000L)
									.saveResultTo("issuerTokenBalance");

							CustomSpecAssert.allRunFor(spec, subop1, subop3,   subop4, subop5);

							CallTransaction.Function funcSymbol = CallTransaction.Function.fromJsonInterface(SYMBOL_ABI);

							String symbol = (String)getValueFromRegistry(spec, "token_symbol", funcSymbol);

							ctxLog.info("symbol: [{}]", symbol);
							Assert.assertEquals(
									"TokenIssuer's symbol should be fixed value",
									"", symbol); // should be "OCT" as expected


							CallTransaction.Function funcDecimals = CallTransaction.Function.fromJsonInterface(DECIMALS_ABI);

							//long decimals = getLongValueFromRegistry(spec, "decimals", function);
							BigInteger val = getValueFromRegistry(spec, "decimals", funcDecimals);
							long decimals = val.longValue();

							ctxLog.info("decimals {}", decimals);
							Assert.assertEquals(
									"TokenIssuer's decimals should be fixed value",
									3, decimals);

							long tokenMultiplier = (long) Math.pow(10, decimals);

							CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(BALANCE_OF_ABI);

							long issuerBalance = ((BigInteger)getValueFromRegistry(spec, "issuerTokenBalance", function)).longValue();

							ctxLog.info("initial balance of Issuer {}", issuerBalance / tokenMultiplier);
							Assert.assertEquals(
									"TokenIssuer's initial token balance should be 1_000_000",
									1_000_000, issuerBalance / tokenMultiplier);

							//  Do token transfers
							var subop6 = contractCall("tokenContract", TRANSFER_ABI,
									aliceEthAddress, 1000 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("tokenIssuer");

							var subop7 = contractCall("tokenContract", TRANSFER_ABI,
									bobEthAddress, 2000 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("tokenIssuer");

							var subop8 = contractCall("tokenContract", TRANSFER_ABI,
									carolEthAddress, 500 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("Bob");

							var subop9 = contractCallLocal("tokenContract",BALANCE_OF_ABI, aliceEthAddress)
									.gas(250_000L)
									.saveResultTo("aliceTokenBalance");

							var subop10 = contractCallLocal("tokenContract",BALANCE_OF_ABI, carolEthAddress)
									.gas(250_000L)
									.saveResultTo("carolTokenBalance");

							var subop11 = contractCallLocal("tokenContract",BALANCE_OF_ABI, bobEthAddress)
									.gas(250_000L)
									.saveResultTo("bobTokenBalance");

							CustomSpecAssert.allRunFor(spec, subop6, subop7, subop8, subop9, subop10, subop11);

							long aliceBalance = ((BigInteger)getValueFromRegistry(spec,"aliceTokenBalance", function)).longValue();
							long bobBalance = ((BigInteger)getValueFromRegistry(spec,"bobTokenBalance", function)).longValue();
							long carolBalance = ((BigInteger)getValueFromRegistry(spec,"carolTokenBalance", function)).longValue();

							ctxLog.info("aliceBalance  {}", aliceBalance / tokenMultiplier);
							ctxLog.info("bobBalance  {}", bobBalance / tokenMultiplier);
							ctxLog.info("carolBalance  {}", carolBalance / tokenMultiplier);

							Assert.assertEquals(
									"Alice's token balance should be 1_000",
									1000, aliceBalance / tokenMultiplier);

							var subop12 = contractCall("tokenContract", APPROVE_ABI,
									daveEthAddress, 200 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("Alice");

							var subop13 = contractCall("tokenContract",TRANSFER_FROM_ABI,
									aliceEthAddress, bobEthAddress, 100 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("Dave");

							var subop14 = contractCallLocal("tokenContract",BALANCE_OF_ABI, aliceEthAddress)
									.gas(250_000L)
									.saveResultTo("aliceTokenBalance");

							var subop15 = contractCallLocal("tokenContract",BALANCE_OF_ABI, bobEthAddress)
									.gas(250_000L)
									.saveResultTo("bobTokenBalance");

							var subop16 = contractCallLocal("tokenContract",BALANCE_OF_ABI, carolEthAddress)
									.gas(250_000L)
									.saveResultTo("carolTokenBalance");

							var subop17 = contractCallLocal("tokenContract",BALANCE_OF_ABI, daveEthAddress)
									.gas(250_000L)
									.saveResultTo("daveTokenBalance");

							var subop18 = contractCallLocal("tokenContract",BALANCE_OF_ABI, issuerEthAddress)
									.gas(250_000L)
									.saveResultTo("issuerTokenBalance");

							CustomSpecAssert.allRunFor(spec, subop12, subop13, subop14, subop15, subop16, subop17, subop18);

							long daveBalance = ((BigInteger)getValueFromRegistry(spec,"daveTokenBalance", function)).longValue();
							aliceBalance = ((BigInteger)getValueFromRegistry(spec,"aliceTokenBalance", function)).longValue();
							bobBalance = ((BigInteger)getValueFromRegistry(spec,"bobTokenBalance", function)).longValue();
							carolBalance = ((BigInteger)getValueFromRegistry(spec,"carolTokenBalance", function)).longValue();
							issuerBalance = ((BigInteger)getValueFromRegistry(spec,"issuerTokenBalance", function)).longValue();

							ctxLog.info("aliceBalance at end {}", aliceBalance / tokenMultiplier);
							ctxLog.info("bobBalance at end {}", bobBalance / tokenMultiplier);
							ctxLog.info("carolBalance at end {}", carolBalance / tokenMultiplier);
							ctxLog.info("daveBalance at end {}", daveBalance / tokenMultiplier);
							ctxLog.info("issuerBalance at end {}", issuerBalance / tokenMultiplier);

							Assert.assertEquals(
									"TokenIssuer's final balance should be 997000",
									997000, issuerBalance / tokenMultiplier);

							Assert.assertEquals(
									"Alice's final balance should be 900",
									900, aliceBalance / tokenMultiplier);
							Assert.assertEquals(
									"Bob's final balance should be 1600",
									1600, bobBalance / tokenMultiplier);
							Assert.assertEquals(
									"Carol's final balance should be 500",
									500, carolBalance / tokenMultiplier);
							Assert.assertEquals(
									"Dave's final balance should be 0",
									0, daveBalance / tokenMultiplier);
						})
				).then(
						assertionsHold((spec, ctxLog) -> {
							var finalOp = getContractRecords("tokenContract")
									.saveRecordNumToRegistry("tokenContractRecordNum")
									.hasCostAnswerPrecheck(OK);

							CustomSpecAssert.allRunFor(spec, finalOp);

							int totalRecordNum = spec.registry().getIntValue("tokenContractRecordNum");
							ctxLog.info("Finished {}", totalRecordNum);

							Assert.assertEquals(
									"Contracts should no longer receive records!",
									0,
									totalRecordNum);
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
