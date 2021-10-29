package com.hedera.services.bdd.suites.contract;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ADD_NTH_FIB_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CURRENT_FIB_SLOTS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.FIBONACCI_PLUS_CONSTRUCTOR_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.FIBONACCI_PLUS_PATH;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;

public class SmartContract2_0Tests extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SmartContract2_0Tests.class);
	private static final long GAS_TO_OFFER = 300_000L;

	public static void main(String args[]) {
		new SmartContract2_0Tests().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				updateSemanticsWork(),
				thresholdAdminKeySemanticsWork(),
				deleteSemanticsWork(),
				contractCallLocalMaxCallSizeIsIgnored()
		);
	}

	private HapiApiSpec updateSemanticsWork() {
		final var adminKey = "adminKey";
		final var fiboPlus = "fiboPlus";
		final String bytecode = "bytecode";
		return defaultHapiSpec("UpdateSemanticsWork")
				.given(
						newKeyNamed(adminKey),
						newKeyNamed("otherKey"),
						fileCreate(bytecode)
								.path(FIBONACCI_PLUS_PATH)
								.noLogging()
				)
				.when(
						contractCreate(fiboPlus, FIBONACCI_PLUS_CONSTRUCTOR_ABI, 15)
								.bytecode(bytecode)
								.adminKey(adminKey)
								.balance(0L)
								.gas(GAS_TO_OFFER)
				)
				.then(
						contractUpdate(fiboPlus)
								.newMemo("failedToUpdate")
								.signedBy("otherKey")
								.hasPrecheck(INVALID_SIGNATURE),
						contractUpdate(fiboPlus)
								.newMemo("UpdateWorked"),
						getContractInfo(fiboPlus).has(contractWith().memo("UpdateWorked"))
				);
	}

	private HapiApiSpec thresholdAdminKeySemanticsWork() {
		KeyShape shape = threshOf(1, 2);
		SigControl validSig = shape.signedWith(sigs(ON, OFF));
		SigControl invalidSig = shape.signedWith(sigs(OFF, OFF));
		final var adminKey = "adminKey";
		final var fiboPlus = "fiboPlus";
		final String bytecode = "bytecode";
		return defaultHapiSpec("ThresholdAdminKeySemanticsWork")
				.given(
						newKeyNamed(adminKey).shape(shape),
						fileCreate(bytecode)
								.path(FIBONACCI_PLUS_PATH)
								.noLogging()
				)
				.when(
						contractCreate(fiboPlus, FIBONACCI_PLUS_CONSTRUCTOR_ABI, 15)
								.bytecode(bytecode)
								.adminKey(adminKey)
								.balance(0L)
								.gas(GAS_TO_OFFER)
				)
				.then(
						contractUpdate(fiboPlus)
								.newMemo("failedToUpdate")
								.sigControl(forKey(fiboPlus, invalidSig))
								.hasKnownStatus(INVALID_SIGNATURE),
						contractUpdate(fiboPlus)
								.sigControl(forKey(fiboPlus, validSig))
								.newMemo("UpdateWorked"),
						getContractInfo(fiboPlus).has(contractWith().memo("UpdateWorked"))
				);
	}

	private HapiApiSpec deleteSemanticsWork() {
		final var adminKey = "adminKey";
		final var fiboPlus1 = "fiboPlus1";
		final var fiboPlus2 = "fiboPlus2";
		final String bytecode = "bytecode";
		return defaultHapiSpec("DeleteSemanticsWork")
				.given(
						newKeyNamed(adminKey),
						newKeyNamed("otherKey"),
						fileCreate(bytecode)
								.path(FIBONACCI_PLUS_PATH)
								.noLogging()
				)
				.when(
						contractCreate(fiboPlus1, FIBONACCI_PLUS_CONSTRUCTOR_ABI, 15)
								.bytecode(bytecode)
								.adminKey(adminKey)
								.balance(0L)
								.gas(GAS_TO_OFFER),
						contractCreate(fiboPlus2, FIBONACCI_PLUS_CONSTRUCTOR_ABI, 16)
								.omitAdminKey()
								.bytecode(bytecode)
								.balance(0L)
								.gas(GAS_TO_OFFER)
				)
				.then(
						contractDelete(fiboPlus2)
								.hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT),
						contractDelete(fiboPlus1)
								.signedBy("otherKey")
								.hasPrecheck(INVALID_SIGNATURE)
				);
	}

	private HapiApiSpec contractCallLocalMaxCallSizeIsIgnored() {
		final String bytecode = "bytecode";
		final var fiboPlus = "fiboPlus";
		final var targets = new int[] {3, 4, 5, 1, 7, 9};
		return defaultHapiSpec("ContractCallLocalMaxCallSizeIsIgnored")
				.given(
						newKeyNamed("adminKey"),
						fileCreate(bytecode)
								.path(FIBONACCI_PLUS_PATH)
								.noLogging(),
						contractCreate(fiboPlus, FIBONACCI_PLUS_CONSTRUCTOR_ABI, 32)
								.bytecode(bytecode)
								.adminKey("adminKey")
								.balance(0L)
								.gas(GAS_TO_OFFER)
				)
				.when(
						contractCall(fiboPlus, ADD_NTH_FIB_ABI, targets, 5)
								.noLogging()
								.gas(GAS_TO_OFFER)
				)
				.then(
						contractCallLocal(fiboPlus, CURRENT_FIB_SLOTS_ABI)
								.payingWith(GENESIS)
								.nodePayment(ONE_HBAR)
								.maxResultSize(0L)
								.gas(GAS_TO_OFFER)
				);
	}
}
