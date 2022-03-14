package com.hedera.services.bdd.suites.contract.traceability;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.assertions.StateChange;
import com.hedera.services.bdd.spec.assertions.StorageChange;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCreate;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ContractTraceabilitySuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractTraceabilitySuite.class);

	private final String contractA = "contractA";
	private final String contractB = "contractB";
	private final String contractC = "contractC";
	private final String traceabilityTxn = "nestedtxn";

	public static void main(String... args) {
		new ContractTraceabilitySuite().runSuiteSync();
	}

	@Override
	public boolean canRunAsync() {
		return false;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[]{
						traceabilityE2EScenario1(),
						traceabilityE2EScenario2(),
						traceabilityE2EScenario3(),
						traceabilityE2EScenario4(),
						traceabilityE2EScenario5(),
						traceabilityE2EScenario6(),
						traceabilityE2EScenario7(),
						traceabilityE2EScenario8(),
						traceabilityE2EScenario9(),
						traceabilityE2EScenario10(),
						traceabilityE2EScenario11()
				}
		);
	}

	private HapiApiSpec traceabilityE2EScenario1() {
		return defaultHapiSpec("traceabilityE2EScenario1")
				.given(
						setup(
								ContractResources.TRACEABILITY_RECURSIVE_CALLS,
								createContractWithSlotValues(contractA, 55, 2, 2),
								createContractWithSlotValues(contractB, 0, 0, 12),
								createContractWithSlotValues(contractC, 0, 11, 0)
						)
				).when(
						executeScenario(ContractResources.TRACEABILITY_EET_1, SUCCESS)
				).then(
						assertStateChanges(
								StateChange.stateChangeFor(contractA)
										.withStorageChanges(
												StorageChange.onlyRead(
														formattedAssertionValue(0),
														formattedAssertionValue(55)),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(2),
														formattedAssertionValue(55))
										),
								StateChange.stateChangeFor(contractB)
										.withStorageChanges(
												StorageChange.readAndWritten(
														formattedAssertionValue(2),
														formattedAssertionValue(12),
														formattedAssertionValue(143))
										),
								StateChange.stateChangeFor(contractC)
										.withStorageChanges(
												StorageChange.readAndWritten(
														formattedAssertionValue(0),
														formattedAssertionValue(0),
														formattedAssertionValue(0)),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(11),
														formattedAssertionValue(0))
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec traceabilityE2EScenario2() {
		return defaultHapiSpec("traceabilityE2EScenario2")
				.given(setup(ContractResources.TRACEABILITY_RECURSIVE_CALLS,
								createContractWithSlotValues(contractA, 0, 0, 0),
								createContractWithSlotValues(contractB, 0, 0, 99),
								createContractWithSlotValues(contractC, 0, 88, 0)
						)
				).when(
						executeScenario(ContractResources.TRACEABILITY_EET_2, SUCCESS)
				).then(
						assertStateChanges(
								StateChange.stateChangeFor(contractA)
										.withStorageChanges(
												StorageChange.onlyRead(
														formattedAssertionValue(0),
														formattedAssertionValue(0)
												),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(0),
														formattedAssertionValue(55))
										),
								StateChange.stateChangeFor(contractB)
										.withStorageChanges(
												StorageChange.readAndWritten(
														formattedAssertionValue(0),
														formattedAssertionValue(0),
														formattedAssertionValue(100)),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(0),
														formattedAssertionValue(0)),
												StorageChange.readAndWritten(
														formattedAssertionValue(2),
														formattedAssertionValue(99),
														formattedAssertionValue(143))
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec traceabilityE2EScenario3() {
		return defaultHapiSpec("traceabilityE2EScenario3")
				.given(
						setup(
								ContractResources.TRACEABILITY_RECURSIVE_CALLS,
								createContractWithSlotValues(contractA, 55, 2, 2),
								createContractWithSlotValues(contractB, 0, 0, 12),
								createContractWithSlotValues(contractC, 0, 11, 0)
						)
				).when(
						executeScenario(ContractResources.TRACEABILITY_EET_3, SUCCESS)
				).then(
						assertStateChanges(
								StateChange.stateChangeFor(contractA)
										.withStorageChanges(
												StorageChange.onlyRead(
														formattedAssertionValue(0),
														formattedAssertionValue(55)),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(2),
														formattedAssertionValue(55252)),
												StorageChange.readAndWritten(
														formattedAssertionValue(2),
														formattedAssertionValue(2),
														formattedAssertionValue(524))
										),
								StateChange.stateChangeFor(contractC)
										.withStorageChanges(
												StorageChange.readAndWritten(
														formattedAssertionValue(0),
														formattedAssertionValue(0),
														formattedAssertionValue(54)),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(11),
														formattedAssertionValue(0))
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec traceabilityE2EScenario4() {
		return defaultHapiSpec("traceabilityE2EScenario4")
				.given(
						setup(
								ContractResources.TRACEABILITY_RECURSIVE_CALLS,
								createContractWithSlotValues(contractA, 2, 3, 4),
								createContractWithSlotValues(contractB, 0, 0, 0),
								createContractWithSlotValues(contractC, 0, 0, 0)
						)
				).when(
						executeScenario(ContractResources.TRACEABILITY_EET_4, SUCCESS)
				).then(
						assertStateChanges(
								StateChange.stateChangeFor(contractA)
										.withStorageChanges(
												StorageChange.readAndWritten(
														formattedAssertionValue(0),
														formattedAssertionValue(2),
														formattedAssertionValue(55)
												),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(3),
														formattedAssertionValue(4)),
												StorageChange.onlyRead(
														formattedAssertionValue(2),
														formattedAssertionValue(4))
										)

						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec traceabilityE2EScenario5() {
		return defaultHapiSpec("traceabilityE2EScenario5")
				.given(
						setup(
								ContractResources.TRACEABILITY_RECURSIVE_CALLS,
								createContractWithSlotValues(contractA, 55, 2, 2),
								createContractWithSlotValues(contractB, 0, 0, 12),
								createContractWithSlotValues(contractC, 4, 1, 0)
						)

				).when(
						executeScenario(ContractResources.TRACEABILITY_EET_5, SUCCESS)
				).then(
						assertStateChanges(
								StateChange.stateChangeFor(contractA)
										.withStorageChanges(
												StorageChange.onlyRead(
														formattedAssertionValue(0),
														formattedAssertionValue(55)),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(2),
														formattedAssertionValue(55252))
										),
								StateChange.stateChangeFor(contractB)
										.withStorageChanges(
												StorageChange.readAndWritten(
														formattedAssertionValue(2),
														formattedAssertionValue(12),
														formattedAssertionValue(524))
										),
								StateChange.stateChangeFor(contractC)
										.withStorageChanges(
												StorageChange.onlyRead(
														formattedAssertionValue(0),
														formattedAssertionValue(4)),
												StorageChange.onlyRead(
														formattedAssertionValue(1),
														formattedAssertionValue(1))
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec traceabilityE2EScenario6() {
		return defaultHapiSpec("traceabilityE2EScenario6")
				.given(
						setup(
								ContractResources.TRACEABILITY_RECURSIVE_CALLS,
								createContractWithSlotValues(contractA, 2, 3, 4),
								createContractWithSlotValues(contractB, 0, 0, 3),
								createContractWithSlotValues(contractC, 0, 1, 0)
						)

				).when(
						executeScenario(ContractResources.TRACEABILITY_EET_6, SUCCESS)
				).then(
						assertStateChanges(
								StateChange.stateChangeFor(contractA)
										.withStorageChanges(
												StorageChange.onlyRead(
														formattedAssertionValue(0),
														formattedAssertionValue(2)
												),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(3),
														formattedAssertionValue(4)),
												StorageChange.readAndWritten(
														formattedAssertionValue(2),
														formattedAssertionValue(4),
														formattedAssertionValue(5))
										),
								StateChange.stateChangeFor(contractC)
										.withStorageChanges(
												StorageChange.onlyRead(
														formattedAssertionValue(0),
														formattedAssertionValue(0)
												),
												StorageChange.onlyRead(
														formattedAssertionValue(1),
														formattedAssertionValue(1))
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec traceabilityE2EScenario7() {
		return defaultHapiSpec("traceabilityE2EScenario7")
				.given(
						setup(
								ContractResources.TRACEABILITY_RECURSIVE_CALLS_CALLCODE,
								createContractWithSlotValues(contractA, 55, 2, 2),
								createContractWithSlotValues(contractB, 0, 0, 12),
								createContractWithSlotValues(contractC, 4, 1, 0)
						)

				).when(
						executeScenario(ContractResources.TRACEABILITY_EET_7, SUCCESS)
				).then(
						assertStateChanges(
								StateChange.stateChangeFor(contractA)
										.withStorageChanges(
												StorageChange.onlyRead(
														formattedAssertionValue(0),
														formattedAssertionValue(55)),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(2),
														formattedAssertionValue(55252))
										),
								StateChange.stateChangeFor(contractB)
										.withStorageChanges(
												StorageChange.readAndWritten(
														formattedAssertionValue(0),
														formattedAssertionValue(0),
														formattedAssertionValue(54)),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(0),
														formattedAssertionValue(0)),
												StorageChange.readAndWritten(
														formattedAssertionValue(2),
														formattedAssertionValue(12),
														formattedAssertionValue(524))
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec traceabilityE2EScenario8() {
		return defaultHapiSpec("traceabilityE2EScenario8")
				.given(
						setup(
								ContractResources.TRACEABILITY_RECURSIVE_CALLS_CALLCODE,
								createContractWithSlotValues(contractA, 55, 2, 2),
								createContractWithSlotValues(contractB, 0, 0, 12),
								createContractWithSlotValues(contractC, 4, 1, 0)
						)
				).when(
						executeScenario(ContractResources.TRACEABILITY_EET_8, SUCCESS)
				).then(
						assertStateChanges(
								StateChange.stateChangeFor(contractA)
										.withStorageChanges(
												StorageChange.readAndWritten(
														formattedAssertionValue(0),
														formattedAssertionValue(55),
														formattedAssertionValue(55)),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(2),
														formattedAssertionValue(55252)),
												StorageChange.readAndWritten(
														formattedAssertionValue(2),
														formattedAssertionValue(2),
														formattedAssertionValue(524))
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec traceabilityE2EScenario9() {
		return defaultHapiSpec("traceabilityE2EScenario9")
				.given(
						setup(
								ContractResources.TRACEABILITY_RECURSIVE_CALLS,
								createContractWithSlotValues(contractA, 55, 2, 2),
								createContractWithSlotValues(contractB, 0, 0, 12),
								createContractWithSlotValues(contractC, 0, 1, 0)
						)
				).when(
						executeScenario(ContractResources.TRACEABILITY_EET_9, CONTRACT_REVERT_EXECUTED)
				).then(
						assertStateChanges(
								StateChange.stateChangeFor(contractA)
										.withStorageChanges(
												StorageChange.onlyRead(
														formattedAssertionValue(0),
														formattedAssertionValue(55)),
												StorageChange.onlyRead(
														formattedAssertionValue(1),
														formattedAssertionValue(2))
										),
								StateChange.stateChangeFor(contractB)
										.withStorageChanges(
												StorageChange.onlyRead(
														formattedAssertionValue(2),
														formattedAssertionValue(12))
										),
								StateChange.stateChangeFor(contractC)
										.withStorageChanges(
												StorageChange.onlyRead(
														formattedAssertionValue(0),
														formattedAssertionValue(0)),
												StorageChange.onlyRead(
														formattedAssertionValue(1),
														formattedAssertionValue(1))
										)

						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec traceabilityE2EScenario10() {
		return defaultHapiSpec("traceabilityE2EScenario10")
				.given(
						setup(
								ContractResources.TRACEABILITY_RECURSIVE_CALLS,
								createContractWithSlotValues(contractA, 2, 3, 4),
								createContractWithSlotValues(contractB, 0, 0, 3),
								createContractWithSlotValues(contractC, 0, 1, 0)
						)
				).when(
						executeScenario(ContractResources.TRACEABILITY_EET_10, SUCCESS)
				).then(
						assertStateChanges(
								StateChange.stateChangeFor(contractA)
										.withStorageChanges(
												StorageChange.onlyRead(
														formattedAssertionValue(0),
														formattedAssertionValue(2)
												),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(3),
														formattedAssertionValue(4))
										),
								StateChange.stateChangeFor(contractB)
										.withStorageChanges(
												StorageChange.readAndWritten(
														formattedAssertionValue(2),
														formattedAssertionValue(3),
														formattedAssertionValue(5)
												)
										),
								StateChange.stateChangeFor(contractC)
										.withStorageChanges(
												StorageChange.onlyRead(
														formattedAssertionValue(0),
														formattedAssertionValue(0)
												),
												StorageChange.onlyRead(
														formattedAssertionValue(1),
														formattedAssertionValue(1))
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec traceabilityE2EScenario11() {
		return defaultHapiSpec("traceabilityE2EScenario11")
				.given(
						setup(
								ContractResources.TRACEABILITY_RECURSIVE_CALLS,
								createContractWithSlotValues(contractA, 2, 3, 4),
								createContractWithSlotValues(contractB, 0, 0, 3),
								createContractWithSlotValues(contractC, 0, 1, 0)
						)
				).when(
						executeScenario(ContractResources.TRACEABILITY_EET_11, SUCCESS)
				).then(
						assertStateChanges(
								StateChange.stateChangeFor(contractA)
										.withStorageChanges(
												StorageChange.onlyRead(
														formattedAssertionValue(0),
														formattedAssertionValue(2)
												),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(3),
														formattedAssertionValue(4))
										),
								StateChange.stateChangeFor(contractC)
										.withStorageChanges(
												StorageChange.readAndWritten(
														formattedAssertionValue(0),
														formattedAssertionValue(0),
														formattedAssertionValue(123)
												),
												StorageChange.readAndWritten(
														formattedAssertionValue(1),
														formattedAssertionValue(1),
														formattedAssertionValue(0))
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiSpecOperation[] setup(final String contractBytecode,
									  final HapiContractCreate contractA,
									  final HapiContractCreate contractB,
									  final HapiContractCreate contractC) {
		return new HapiSpecOperation[]{
				UtilVerbs.overriding("contracts.enableTraceability", "true"),
				cryptoCreate("account").balance(10 * ONE_MILLION_HBARS),
				fileCreate("bytecode").payingWith("account"),
				updateLargeFile("account", "bytecode",
						extractByteCode(contractBytecode)),
				contractA,
				contractB,
				contractC
		};
	}

	private HapiContractCreate createContractWithSlotValues(final String contract,
															final int slot0,
															final int slot1,
															final int slot2) {
		return contractCreate(contract, ContractResources.TRACEABILITY_CONSTRUCTOR, slot0, slot1, slot2)
				.bytecode("bytecode")
				.gas(300_000);
	}


	private HapiSpecOperation executeScenario(final String scenario, final ResponseCodeEnum expectedExecutionStatus) {
		return withOpContext(
				(spec, opLog) -> allRunFor(spec,
						contractCall(contractA, scenario,
								AssociatePrecompileSuite.getNestedContractAddress(contractB, spec),
								AssociatePrecompileSuite.getNestedContractAddress(contractC, spec))
								.gas(1000000)
								.via(traceabilityTxn)
								.hasKnownStatus(expectedExecutionStatus)
				));
	}

	private CustomSpecAssert assertStateChanges(final StateChange... stateChanges) {
		return withOpContext(
				(spec, opLog) -> allRunFor(
						spec,
						getTxnRecord(traceabilityTxn)
								.hasPriority(
										recordWith()
												.contractCallResult(resultWith().stateChanges(stateChanges)))
								.logged()
				)
		);
	}

	@NotNull
	private ByteString formattedAssertionValue(long value) {
		return ByteString.copyFrom(Bytes.wrap(UInt256.valueOf(value)).trimLeadingZeros().toArrayUnsafe());
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
