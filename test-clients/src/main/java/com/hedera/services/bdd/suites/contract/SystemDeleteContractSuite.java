package com.hedera.services.bdd.suites.contract;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemContractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemContractUndelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class SystemDeleteContractSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SystemDeleteContractSuite.class);
	final String PATH_TO_VALID_BYTECODE = HapiSpecSetup.getDefaultInstance().defaultContractPath();


	public static void main(String... args) {
		new SystemDeleteContractSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveSpecs(),
				negativeSpecs()
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return Arrays.asList();
	}

	List<HapiApiSpec> negativeSpecs() {
		return List.of(new HapiApiSpec[] {
				systemDeleteContractNotSupported(),
				systemDelThenUndelContractNotSupported()
		});

	}

	private HapiApiSpec systemDeleteContractNotSupported() {
		return defaultHapiSpec("systemDeleteContractNotSupported")
				.given(
						fileCreate("conFile")
								.path(PATH_TO_VALID_BYTECODE),
						contractCreate("test-contract")
								.bytecode("conFile")
								.hasKnownStatus(SUCCESS)
				).when(
				).then(
						UtilVerbs.sleepFor(1000),
						systemContractDelete("test-contract")
								.payingWith(SYSTEM_DELETE_ADMIN)
								.hasPrecheck(NOT_SUPPORTED)
				);
	}

	private HapiApiSpec systemDelThenUndelContractNotSupported() {
		return defaultHapiSpec("systemDelThenUndelContractNotSupported")
				.given(
						fileCreate("conFile")
								.path(PATH_TO_VALID_BYTECODE),
						contractCreate("test-contract")
								.bytecode("conFile")
								.hasKnownStatus(SUCCESS)
				).when(
						systemContractDelete("test-contract")
								.payingWith(SYSTEM_DELETE_ADMIN)
								.hasPrecheck(NOT_SUPPORTED)

				).then(
						systemContractUndelete("test-contract")
								.payingWith(SYSTEM_UNDELETE_ADMIN)
								.fee(0L)
								.hasPrecheckFrom(NOT_SUPPORTED),
						getContractInfo("test-contract")
								.nodePayment(1_234L)
								.hasAnswerOnlyPrecheck(OK)
								.logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
