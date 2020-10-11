package com.hedera.services.bdd.suites.contract;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class SmartContractSelfDestructSpec  extends HapiApiSuite  {
	private static final Logger log = LogManager.getLogger(SmartContractSelfDestructSpec.class);

	final String PATH_TO_PAY_TEST_SELF_DESTRUCT_BYTECODE = "src/main/resource/PayTestSelfDestruct.bin";

	private static final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String SC_GET_BALANCE = "{\"constant\":true,\"inputs\":[],\"name\":\"getBalance\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	private static final String SC_DEPOSIT = "{\"constant\":false,\"inputs\":[{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"deposit\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";
	private static final String SC_KILL_ME = "{\"constant\":false,\"inputs\":[{\"name\":\"beneficiary\",\"type\":\"address\"}],\"name\":\"killMe\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	private static long localCallGas;
	private static long contractDuration;


	public static void main(String... args) {
		new org.ethereum.crypto.HashUtil();

		new SmartContractSelfDestructSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				payTestSelfDestructCall(),
		});
	}

	HapiApiSpec payTestSelfDestructCall() {

		return defaultHapiSpec("payTestSelfDestructCall")
				.given(
						cryptoCreate("payer").balance( 10_000_000_000_000L).logged(),
						cryptoCreate("receiver").balance( 1_000L),
						fileCreate("bytecode")
								.path(PATH_TO_PAY_TEST_SELF_DESTRUCT_BYTECODE),
						contractCreate("payTestSelfDestruct")
								.bytecode("bytecode")

				).when(
						withOpContext((spec, opLog) -> {

							var subop1 = contractCall("payTestSelfDestruct", SC_DEPOSIT, 1_000L)
									.payingWith("payer")
									.gas(300_000L)
									.via("deposit")
									.sending(1_000L);

                            var subop2 = contractCall("payTestSelfDestruct", SC_GET_BALANCE)
									 .payingWith("payer")
									 .gas(300_000L)
									 .via("getBalance");

							AccountID contractAccountId = asId("payTestSelfDestruct", spec);
							var subop3 = contractCall("payTestSelfDestruct", SC_KILL_ME, contractAccountId.getAccountNum() )
									.payingWith("payer")
									.gas(300_000L)
									.hasKnownStatus(OBTAINER_SAME_CONTRACT_ID);

							var subop4 = contractCall("payTestSelfDestruct", SC_KILL_ME, 999_999L)
									.payingWith("payer")
									.gas(300_000L)
									.hasKnownStatus(INVALID_SOLIDITY_ADDRESS);

							AccountID receiverAccountId = asId("receiver", spec);
							var subop5 = contractCall("payTestSelfDestruct", SC_KILL_ME, receiverAccountId.getAccountNum())
									.payingWith("payer")
									.gas(300_000L)
									.via("selfDestruct")
									.hasKnownStatus(SUCCESS);

							CustomSpecAssert.allRunFor(spec, subop1, subop2,subop3, subop4, subop5);

						})

					).then(
						getTxnRecord("deposit"),
						getTxnRecord("getBalance")
								.hasPriority(recordWith().contractCallResult(
										resultWith().resultThruAbi(
												SC_GET_BALANCE,
												isLiteralResult(new Object[] { BigInteger.valueOf(1_000L) })))),
						getAccountBalance("receiver")
								.hasTinyBars(2_000L)
				);

	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
