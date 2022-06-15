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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.props.JutilPropertySource;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.autorenew.*;
import com.hedera.services.bdd.suites.compose.LocalNetworkCheck;
import com.hedera.services.bdd.suites.compose.PerpetualLocalCalls;
import com.hedera.services.bdd.suites.consensus.*;
import com.hedera.services.bdd.suites.contract.hapi.*;
import com.hedera.services.bdd.suites.contract.opcodes.*;
import com.hedera.services.bdd.suites.contract.openzeppelin.ERC1155ContractInteractions;
import com.hedera.services.bdd.suites.contract.openzeppelin.ERC20ContractInteractions;
import com.hedera.services.bdd.suites.contract.openzeppelin.ERC721ContractInteractions;
import com.hedera.services.bdd.suites.contract.precompile.*;
import com.hedera.services.bdd.suites.contract.records.LogsSuite;
import com.hedera.services.bdd.suites.contract.records.RecordsSuite;
import com.hedera.services.bdd.suites.contract.traceability.ContractTraceabilitySuite;
import com.hedera.services.bdd.suites.crypto.*;
import com.hedera.services.bdd.suites.fees.*;
import com.hedera.services.bdd.suites.file.*;
import com.hedera.services.bdd.suites.file.negative.*;
import com.hedera.services.bdd.suites.file.positive.CreateSuccessSpec;
import com.hedera.services.bdd.suites.file.positive.SysDelSysUndelSpec;
import com.hedera.services.bdd.suites.issues.*;
import com.hedera.services.bdd.suites.meta.VersionInfoSpec;
import com.hedera.services.bdd.suites.perf.AccountBalancesClientSaveLoadTest;
import com.hedera.services.bdd.suites.perf.AdjustFeeScheduleSuite;
import com.hedera.services.bdd.suites.perf.FileContractMemoPerfSuite;
import com.hedera.services.bdd.suites.perf.QueryOnlyLoadTest;
import com.hedera.services.bdd.suites.perf.contract.*;
import com.hedera.services.bdd.suites.perf.contract.opcodes.SStoreOperationLoadTest;
import com.hedera.services.bdd.suites.perf.crypto.*;
import com.hedera.services.bdd.suites.perf.file.FileExpansionLoadProvider;
import com.hedera.services.bdd.suites.perf.file.FileUpdateLoadTest;
import com.hedera.services.bdd.suites.perf.file.MixedFileOpsLoadTest;
import com.hedera.services.bdd.suites.perf.mixedops.MixedOpsMemoPerfSuite;
import com.hedera.services.bdd.suites.perf.mixedops.MixedTransferAndSubmitLoadTest;
import com.hedera.services.bdd.suites.perf.mixedops.MixedTransferCallAndSubmitLoadTest;
import com.hedera.services.bdd.suites.perf.schedule.OnePendingSigScheduledXfersLoad;
import com.hedera.services.bdd.suites.perf.schedule.ReadyToRunScheduledXfersLoad;
import com.hedera.services.bdd.suites.perf.token.*;
import com.hedera.services.bdd.suites.perf.topic.*;
import com.hedera.services.bdd.suites.records.*;
import com.hedera.services.bdd.suites.regression.SplittingThrottlesWorks;
import com.hedera.services.bdd.suites.regression.SteadyStateThrottlingCheck;
import com.hedera.services.bdd.suites.regression.UmbrellaRedux;
import com.hedera.services.bdd.suites.schedule.*;
import com.hedera.services.bdd.suites.streaming.RunTransfers;
import com.hedera.services.bdd.suites.throttling.GasLimitThrottlingSuite;
import com.hedera.services.bdd.suites.throttling.PrivilegedOpsSuite;
import com.hedera.services.bdd.suites.throttling.ThrottleDefValidationSuite;
import com.hedera.services.bdd.suites.token.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.bdd.suites.HapiApiSuite.ETH_SUFFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;


class E2EPackageRunner {

	@BeforeAll
	static void beforeAll() {
		final var portSystemProperty = System.getProperty("defaultPort");
		final var defaultPort = portSystemProperty != null ? portSystemProperty : "50211";
		final var defaultProperties = JutilPropertySource.getDefaultInstance();
		HapiApiSpec.runInCiMode(
				defaultPort + ":50212",
				defaultProperties.get("default.payer"),
				defaultProperties.get("default.node").split("\\.")[2],
				defaultProperties.get("tls"),
				defaultProperties.get("txn.proto.structure"),
				defaultProperties.get("node.selector"),
				Collections.emptyMap()
		);
	}

	@Tag("autorenew")
	@TestFactory
	Collection<DynamicContainer> autorenew() {
		return List.of(
				extractSpecsFromSuite(AccountAutoRenewalSuite::new),
				extractSpecsFromSuite(AutoRemovalCasesSuite::new),
				extractSpecsFromSuite(GracePeriodRestrictionsSuite::new),
				extractSpecsFromSuite(MacroFeesChargedSanityCheckSuite::new),
				extractSpecsFromSuite(NoGprIfNoAutoRenewSuite::new),
				extractSpecsFromSuite(TopicAutoRenewalSuite::new)
		);
	}

	@Tag("compose")
	@TestFactory
	Collection<DynamicContainer> compose() {
		return List.of(
				extractSpecsFromSuite(LocalNetworkCheck::new),
				extractSpecsFromSuite(PerpetualLocalCalls::new)
		);
	}

	@Tag("consensus")
	@TestFactory
	Collection<DynamicContainer> consensus() {
		return List.of(
				extractSpecsFromSuite(AssortedHcsOps::new),
				extractSpecsFromSuite(ChunkingSuite::new),
				extractSpecsFromSuite(SubmitMessageSuite::new),
				extractSpecsFromSuite(TopicCreateSuite::new),
				extractSpecsFromSuite(TopicDeleteSuite::new),
				extractSpecsFromSuite(TopicGetInfoSuite::new),
				extractSpecsFromSuite(TopicUpdateSuite::new)
		);
	}

	@Tag("contract")
	@Tag("contract.precompile")
	@Tag("contract.precompile.part1")
	@TestFactory
	Collection<DynamicContainer> contractPrecompile() {
		return List.of(
				extractSpecsFromSuite(AssociatePrecompileSuite::new),
				extractSpecsFromSuite(ContractBurnHTSSuite::new),
				extractSpecsFromSuite(ContractHTSSuite::new),
				extractSpecsFromSuite(ContractKeysHTSSuite::new),
				extractSpecsFromSuite(ContractMintHTSSuite::new),
				extractSpecsFromSuite(CreatePrecompileSuite::new)
		);
	}

	@Tag("contract")
	@Tag("contract.precompile")
	@Tag("contract.precompile.part1.eth")
	@TestFactory
	Collection<DynamicContainer> contractPrecompileEth() {
		return List.of(new DynamicContainer[] {
				extractSpecsFromSuiteForEth(AssociatePrecompileSuite::new),
				extractSpecsFromSuiteForEth(ContractBurnHTSSuite::new),
				extractSpecsFromSuiteForEth(ContractHTSSuite::new),
				extractSpecsFromSuiteForEth(ContractKeysHTSSuite::new),
				extractSpecsFromSuiteForEth(ContractMintHTSSuite::new),
				extractSpecsFromSuiteForEth(CreatePrecompileSuite::new)
		});
	}

	@Tag("contract")
	@Tag("contract.precompile")
	@Tag("contract.precompile.part2")
	@TestFactory
	Collection<DynamicContainer> contractPrecompile2() {
		return List.of(new DynamicContainer[] {
				extractSpecsFromSuite(CryptoTransferHTSSuite::new),
				extractSpecsFromSuite(DelegatePrecompileSuite::new),
				extractSpecsFromSuite(DissociatePrecompileSuite::new),
				extractSpecsFromSuite(DynamicGasCostSuite::new),
				extractSpecsFromSuite(MixedHTSPrecompileTestsSuite::new)
		});
	}

	@Tag("contract")
	@Tag("contract.precompile")
	@Tag("contract.precompile.part2.eth")
	@TestFactory
	Collection<DynamicContainer> contractPrecompile2Eth() {
		return List.of(new DynamicContainer[] {
				extractSpecsFromSuiteForEth(DissociatePrecompileSuite::new),
				extractSpecsFromSuiteForEth(CryptoTransferHTSSuite::new),
				extractSpecsFromSuiteForEth(DelegatePrecompileSuite::new)
		});
	}

	@Tag("contract")
	@Tag("contract.openzeppelin")
	@TestFactory
	Collection<DynamicContainer> contractOpenZeppelin() {
		return List.of(
				extractSpecsFromSuite(ERC20ContractInteractions::new),
				extractSpecsFromSuite(ERC721ContractInteractions::new),
				extractSpecsFromSuite(ERC1155ContractInteractions::new)
		);
	}

	@Tag("contract")
	@Tag("contract.openzeppelin.eth")
	@TestFactory
	Collection<DynamicContainer> contractOpenZeppelinEth() {
		return List.of(
				extractSpecsFromSuiteForEth(ERC20ContractInteractions::new),
				extractSpecsFromSuiteForEth(ERC721ContractInteractions::new),
				extractSpecsFromSuiteForEth(ERC1155ContractInteractions::new)
		);
	}

	@Tag("contract")
	@Tag("contract.records")
	@TestFactory
	Collection<DynamicContainer> contractRecords() {
		return List.of(
				extractSpecsFromSuite(LogsSuite::new),
				extractSpecsFromSuite(RecordsSuite::new)
		);
	}

	@Tag("contract")
	@Tag("contract.records.eth")
	@TestFactory
	Collection<DynamicContainer> contractRecordsEth() {
		return List.of(
				extractSpecsFromSuiteForEth(LogsSuite::new),
				extractSpecsFromSuiteForEth(RecordsSuite::new)
		);
	}

	@Tag("contract")
	@Tag("contract.opcodes")
	@TestFactory
	Collection<DynamicContainer> contractOpcodes() {
		return List.of(
				extractSpecsFromSuite(BalanceOperationSuite::new),
				extractSpecsFromSuite(CallCodeOperationSuite::new),
				extractSpecsFromSuite(CallOperationSuite::new),
				extractSpecsFromSuite(CreateOperationSuite::new),
				extractSpecsFromSuite(DelegateCallOperationSuite::new),
				extractSpecsFromSuite(ExtCodeCopyOperationSuite::new),
				extractSpecsFromSuite(ExtCodeHashOperationSuite::new),
				extractSpecsFromSuite(ExtCodeSizeOperationSuite::new),
				extractSpecsFromSuite(GlobalPropertiesSuite::new),
				extractSpecsFromSuite(SelfDestructSuite::new),
				extractSpecsFromSuite(SStoreSuite::new),
				extractSpecsFromSuite(StaticCallOperationSuite::new)
		);
	}

	@Tag("contract")
	@Tag("contract.opcodes.eth")
	@TestFactory
	Collection<DynamicContainer> contractOpcodesEth() {
		return List.of(new DynamicContainer[] {
				extractSpecsFromSuiteForEth(BalanceOperationSuite::new),
				extractSpecsFromSuiteForEth(CallCodeOperationSuite::new),
				extractSpecsFromSuiteForEth(CallOperationSuite::new),
				extractSpecsFromSuiteForEth(CreateOperationSuite::new),
				extractSpecsFromSuiteForEth(DelegateCallOperationSuite::new),
				extractSpecsFromSuiteForEth(ExtCodeCopyOperationSuite::new),
				extractSpecsFromSuiteForEth(ExtCodeHashOperationSuite::new),
				extractSpecsFromSuiteForEth(ExtCodeSizeOperationSuite::new),
				extractSpecsFromSuiteForEth(GlobalPropertiesSuite::new),
				extractSpecsFromSuiteForEth(StaticCallOperationSuite::new),
				extractSpecsFromSuiteForEth(SelfDestructSuite::new),
				extractSpecsFromSuiteForEth(SStoreSuite::new)
		});
	}

	@Tag("contract")
	@Tag("contract.hapi")
	@TestFactory
	Collection<DynamicContainer> contractHapi() {
		return List.of(new DynamicContainer[] {
				extractSpecsFromSuite(ContractCallLocalSuite::new),
				extractSpecsFromSuite(ContractCallSuite::new),
				extractSpecsFromSuite(ContractCreateSuite::new),
				extractSpecsFromSuite(ContractDeleteSuite::new),
				extractSpecsFromSuite(ContractGetBytecodeSuite::new),
				extractSpecsFromSuite(ContractGetInfoSuite::new),
				extractSpecsFromSuite(ContractMusicalChairsSuite::new),
				extractSpecsFromSuite(ContractUpdateSuite::new)
		});
	}

	@Tag("contract")
	@Tag("contract.hapi.eth")
	@TestFactory
	Collection<DynamicContainer> contractHapiEth() {
		return List.of(new DynamicContainer[]{
				extractSpecsFromSuiteForEth(ContractCallLocalSuite::new),
				extractSpecsFromSuiteForEth(ContractCallSuite::new),
				extractSpecsFromSuiteForEth(ContractCreateSuite::new),
				extractSpecsFromSuiteForEth(ContractDeleteSuite::new),
				extractSpecsFromSuiteForEth(ContractGetBytecodeSuite::new),
				extractSpecsFromSuiteForEth(ContractGetInfoSuite::new),
				extractSpecsFromSuiteForEth(ContractMusicalChairsSuite::new),
				extractSpecsFromSuiteForEth(ContractUpdateSuite::new)
		});
	}

	@Tag("contract")
	@Tag("contract.traceability")
	@TestFactory
	Collection<DynamicContainer> contractTraceability() {
		return List.of(
				extractSpecsFromSuite(ContractTraceabilitySuite::new)
		);
	}

	@Tag("contract")
	@Tag("contract.traceability.eth")
	@TestFactory
	Collection<DynamicContainer> contractTraceabilityEth() {
		return List.of(
				extractSpecsFromSuiteForEth(ContractTraceabilitySuite::new)
		);
	}

	@Tag("crypto")
	@TestFactory
	Collection<DynamicContainer> crypto() {
		return List.of(
				extractSpecsFromSuite(AutoAccountCreationSuite::new),
				extractSpecsFromSuite(AutoAccountUpdateSuite::new),
				extractSpecsFromSuite(CryptoApproveAllowanceSuite::new),
				extractSpecsFromSuite(CryptoCornerCasesSuite::new),
				extractSpecsFromSuite(CryptoCreateSuite::new),
				extractSpecsFromSuite(CryptoDeleteSuite::new),
				extractSpecsFromSuite(CryptoGetInfoRegression::new),
				extractSpecsFromSuite(CryptoGetRecordsRegression::new),
				extractSpecsFromSuite(CryptoTransferSuite::new),
				extractSpecsFromSuite(CryptoUpdateSuite::new),
				extractSpecsFromSuite(CrytoCreateSuiteWithUTF8::new),
				extractSpecsFromSuite(HelloWorldSpec::new),
				extractSpecsFromSuite(MiscCryptoSuite::new),
				extractSpecsFromSuite(QueryPaymentSuite::new),
				extractSpecsFromSuite(RandomOps::new),
				extractSpecsFromSuite(TransferWithCustomFees::new),
				extractSpecsFromSuite(TxnReceiptRegression::new),
				extractSpecsFromSuite(TxnRecordRegression::new),
				extractSpecsFromSuite(UnsupportedQueriesRegression::new)
		);
	}

	@Tag("fees")
	@TestFactory
	Collection<DynamicContainer> fees() {
		return List.of(
				extractSpecsFromSuite(AllBaseOpFeesSuite::new),
				extractSpecsFromSuite(CongestionPricingSuite::new),
				extractSpecsFromSuite(CostOfEverythingSuite::new),
				extractSpecsFromSuite(CreateAndUpdateOps::new),
				extractSpecsFromSuite(OverlappingKeysSuite::new),
				extractSpecsFromSuite(QueryPaymentExploitsSuite::new),
				extractSpecsFromSuite(SpecialAccountsAreExempted::new),
				extractSpecsFromSuite(TransferListServiceFeesSuite::new)
		);
	}

	@Tag("file")
	@TestFactory
	Collection<DynamicContainer> file() {
		return List.of(
				extractSpecsFromSuite(DiverseStateCreation::new),
				extractSpecsFromSuite(DiverseStateValidation::new),
				extractSpecsFromSuite(ExchangeRateControlSuite::new),
				extractSpecsFromSuite(FetchSystemFiles::new),
				extractSpecsFromSuite(FileAppendSuite::new),
				extractSpecsFromSuite(FileCreateSuite::new),
				extractSpecsFromSuite(FileDeleteSuite::new),
				extractSpecsFromSuite(FileUpdateSuite::new),
				extractSpecsFromSuite(PermissionSemanticsSpec::new),
				extractSpecsFromSuite(ProtectedFilesUpdateSuite::new),
				extractSpecsFromSuite(ValidateNewAddressBook::new)
		);
	}

	@Tag("file")
	@Tag("file.positive")
	@TestFactory
	Collection<DynamicContainer> filePositive() {
		return List.of(
				extractSpecsFromSuite(CreateSuccessSpec::new),
				extractSpecsFromSuite(SysDelSysUndelSpec::new)
		);
	}

	@Tag("file")
	@Tag("file.negative")
	@TestFactory
	Collection<DynamicContainer> fileNegative() {
		return List.of(
				extractSpecsFromSuite(AppendFailuresSpec::new),
				extractSpecsFromSuite(CreateFailuresSpec::new),
				extractSpecsFromSuite(DeleteFailuresSpec::new),
				extractSpecsFromSuite(QueryFailuresSpec::new),
				extractSpecsFromSuite(UpdateFailuresSpec::new)
		);
	}

	@Tag("issues")
	@TestFactory
	Collection<DynamicContainer> issues() {
		return List.of(
				extractSpecsFromSuite(Issue305Spec::new),
				extractSpecsFromSuite(Issue310Suite::new),
				extractSpecsFromSuite(Issue1648Suite::new),
				extractSpecsFromSuite(Issue1741Suite::new),
				extractSpecsFromSuite(Issue1742Suite::new),
				extractSpecsFromSuite(Issue1744Suite::new),
				extractSpecsFromSuite(Issue1758Suite::new),
				extractSpecsFromSuite(Issue1765Suite::new),
				extractSpecsFromSuite(Issue2051Spec::new),
				extractSpecsFromSuite(Issue2098Spec::new),
				extractSpecsFromSuite(Issue2143Spec::new),
				extractSpecsFromSuite(Issue2150Spec::new),
				extractSpecsFromSuite(Issue2319Spec::new)
		);
	}

	@Tag("meta")
	@TestFactory
	Collection<DynamicContainer> meta() {
		return List.of(
				extractSpecsFromSuite(VersionInfoSpec::new)
		);
	}

	@Tag("perf")
	@TestFactory
	Collection<DynamicContainer> perf() {
		return List.of(
				extractSpecsFromSuite(AccountBalancesClientSaveLoadTest::new),
				extractSpecsFromSuite(AdjustFeeScheduleSuite::new),
				extractSpecsFromSuite(FileContractMemoPerfSuite::new),
				extractSpecsFromSuite(QueryOnlyLoadTest::new)
		);
	}

	@Tag("perf")
	@Tag("perf.contract")
	@TestFactory
	Collection<DynamicContainer> perfContract() {
		return List.of(
				extractSpecsFromSuite(ContractCallLoadTest::new),
				extractSpecsFromSuite(ContractCallLocalPerfSuite::new),
				extractSpecsFromSuite(ContractCallPerfSuite::new),
				extractSpecsFromSuite(ContractPerformanceSuite::new),
				extractSpecsFromSuite(FibonacciPlusLoadProvider::new),
				extractSpecsFromSuite(MixedSmartContractOpsLoadTest::new)
		);
	}

	@Tag("perf")
	@Tag("perf.contract.opcodes")
	@TestFactory
	Collection<DynamicContainer> perfContractOpcodes() {
		return List.of(
				extractSpecsFromSuite(SStoreOperationLoadTest::new)
		);
	}

	@Tag("perf")
	@Tag("perf.crypto")
	@TestFactory
	Collection<DynamicContainer> perfCrypto() {
		return List.of(
				extractSpecsFromSuite(CryptoAllowancePerfSuite::new),
				extractSpecsFromSuite(CryptoCreatePerfSuite::new),
				extractSpecsFromSuite(CryptoTransferLoadTest::new),
				extractSpecsFromSuite(CryptoTransferLoadTestWithAutoAccounts::new),
				extractSpecsFromSuite(CryptoTransferLoadTestWithInvalidAccounts::new),
				extractSpecsFromSuite(CryptoTransferPerfSuite::new),
				extractSpecsFromSuite(CryptoTransferPerfSuiteWOpProvider::new),
				extractSpecsFromSuite(SimpleXfersAvoidingHotspot::new)
		);
	}

	@Tag("perf")
	@Tag("perf.file")
	@TestFactory
	Collection<DynamicContainer> perfFile() {
		return List.of(
				extractSpecsFromSuite(FileExpansionLoadProvider::new),
				extractSpecsFromSuite(FileUpdateLoadTest::new),
				extractSpecsFromSuite(MixedFileOpsLoadTest::new)
		);
	}

	@Tag("perf")
	@Tag("perf.mixedops")
	@TestFactory
	Collection<DynamicContainer> perfMixedOps() {
		return List.of(
				extractSpecsFromSuite(MixedFileOpsLoadTest::new),
				extractSpecsFromSuite(MixedOpsMemoPerfSuite::new),
				extractSpecsFromSuite(MixedTransferAndSubmitLoadTest::new),
				extractSpecsFromSuite(MixedTransferCallAndSubmitLoadTest::new)
		);
	}

	@Tag("perf")
	@Tag("perf.schedule")
	@TestFactory
	Collection<DynamicContainer> perfSchedule() {
		return List.of(
				extractSpecsFromSuite(OnePendingSigScheduledXfersLoad::new),
				extractSpecsFromSuite(ReadyToRunScheduledXfersLoad::new)
		);
	}

	@Tag("perf")
	@Tag("perf.token")
	@TestFactory
	Collection<DynamicContainer> perfToken() {
		return List.of(
				extractSpecsFromSuite(TokenCreatePerfSuite::new),
				extractSpecsFromSuite(TokenRelStatusChanges::new),
				extractSpecsFromSuite(TokenTransferBasicLoadTest::new),
				extractSpecsFromSuite(TokenTransfersLoadProvider::new),
				extractSpecsFromSuite(UniqueTokenStateSetup::new)
		);
	}

	@Tag("perf")
	@Tag("perf.topic")
	@TestFactory
	Collection<DynamicContainer> perfTopic() {
		return List.of(
				extractSpecsFromSuite(createTopicLoadTest::new),
				extractSpecsFromSuite(CreateTopicPerfSuite::new),
				extractSpecsFromSuite(HCSChunkingRealisticPerfSuite::new),
				extractSpecsFromSuite(SubmitMessageLoadTest::new),
				extractSpecsFromSuite(SubmitMessagePerfSuite::new)
		);
	}

	@Tag("records")
	@TestFactory
	Collection<DynamicContainer> records() {
		return List.of(
				extractSpecsFromSuite(CharacterizationSuite::new),
				extractSpecsFromSuite(ContractRecordsSanityCheckSuite::new),
				extractSpecsFromSuite(CryptoRecordsSanityCheckSuite::new),
				extractSpecsFromSuite(DuplicateManagementTest::new),
				extractSpecsFromSuite(FileRecordsSanityCheckSuite::new),
				extractSpecsFromSuite(RecordCreationSuite::new),
				extractSpecsFromSuite(SignedTransactionBytesRecordsSuite::new)
		);
	}

	@Tag("regression")
	@TestFactory
	Collection<DynamicContainer> regression() {
		return List.of(
				extractSpecsFromSuite(SplittingThrottlesWorks::new),
				extractSpecsFromSuite(SteadyStateThrottlingCheck::new),
				extractSpecsFromSuite(UmbrellaRedux::new)
		);
	}

	@Tag("schedule")
	@TestFactory
	Collection<DynamicContainer> schedule() {
		return List.of(
				extractSpecsFromSuite(ScheduleCreateSpecs::new),
				extractSpecsFromSuite(ScheduleDeleteSpecs::new),
				extractSpecsFromSuite(ScheduleExecutionSpecs::new),
				extractSpecsFromSuite(ScheduleExecutionSpecStateful::new),
				extractSpecsFromSuite(ScheduleRecordSpecs::new),
				extractSpecsFromSuite(ScheduleSignSpecs::new),
				extractSpecsFromSuite(ScheduleLongTermExecutionSpecs::new),
				extractSpecsFromSuite(ScheduleLongTermSignSpecs::new)
		);
	}

	@Tag("streaming")
	@TestFactory
	Collection<DynamicContainer> streaming() {
		return List.of(
				extractSpecsFromSuite(RunTransfers::new)
		);
	}

	@Tag("throttling")
	@TestFactory
	Collection<DynamicContainer> throttling() {
		return List.of(
				extractSpecsFromSuite(GasLimitThrottlingSuite::new),
				extractSpecsFromSuite(PrivilegedOpsSuite::new),
				extractSpecsFromSuite(ThrottleDefValidationSuite::new)
		);
	}

	@Tag("token")
	@TestFactory
	Collection<DynamicContainer> token() {
		return List.of(
				extractSpecsFromSuite(Hip17UnhappyAccountsSuite::new),
				extractSpecsFromSuite(Hip17UnhappyTokensSuite::new),
				extractSpecsFromSuite(TokenAssociationSpecs::new),
				extractSpecsFromSuite(TokenCreateSpecs::new),
				extractSpecsFromSuite(TokenDeleteSpecs::new),
				extractSpecsFromSuite(TokenFeeScheduleUpdateSpecs::new),
				extractSpecsFromSuite(TokenManagementSpecs::new),
				extractSpecsFromSuite(TokenManagementSpecsStateful::new),
				extractSpecsFromSuite(TokenMiscOps::new),
				extractSpecsFromSuite(TokenPauseSpecs::new),
				extractSpecsFromSuite(TokenTotalSupplyAfterMintBurnWipeSuite::new),
				extractSpecsFromSuite(TokenTransactSpecs::new),
				extractSpecsFromSuite(TokenUpdateSpecs::new),
				extractSpecsFromSuite(UniqueTokenManagementSpecs::new)
		);
	}

	@Tag("utils")
	@TestFactory
	Collection<DynamicContainer> utils() {
		return List.of(
				extractSpecsFromSuite(SubmitMessagePerfSuite::new)
		);
	}

	private DynamicContainer extractSpecsFromSuite(final Supplier<HapiApiSuite> suiteSupplier) {
		final var suite = suiteSupplier.get();
		final var tests = suite.getSpecsInSuite()
				.stream()
				.map(s -> dynamicTest(s.getName(), () -> {
							s.run();
							assertEquals(s.getExpectedFinalStatus(), s.getStatus(),
									"\n\t\t\tFailure in SUITE {" + suite.getClass().getSimpleName() + "}, while " +
											"executing " +
											"SPEC {" + s.getName() + "}");
						}
				));
		return dynamicContainer(suite.getClass().getSimpleName(), tests);
	}

	private DynamicContainer extractSpecsFromSuiteForEth(final Supplier<HapiApiSuite> suiteSupplier) {
		final var suite = suiteSupplier.get();
		final var tests = suite.getSpecsInSuite()
				.stream()
				.map(s -> dynamicTest(s.getName() + ETH_SUFFIX, () -> {
							s.setSuitePrefix(suite.getClass().getSimpleName() + ETH_SUFFIX);
							s.run();
							assertEquals(s.getExpectedFinalStatus(), s.getStatus(),
									"\n\t\t\tFailure in SUITE {" + suite.getClass().getSimpleName() + ETH_SUFFIX + "}, " +
											"while " +
											"executing " +
											"SPEC {" + s.getName() + ETH_SUFFIX + "}");
						}
				));
		return dynamicContainer(suite.getClass().getSimpleName(), tests);
	}
}
