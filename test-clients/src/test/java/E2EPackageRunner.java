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
import com.hedera.services.bdd.suites.autorenew.AccountAutoRenewalSuite;
import com.hedera.services.bdd.suites.autorenew.AutoRemovalCasesSuite;
import com.hedera.services.bdd.suites.autorenew.GracePeriodRestrictionsSuite;
import com.hedera.services.bdd.suites.autorenew.MacroFeesChargedSanityCheckSuite;
import com.hedera.services.bdd.suites.autorenew.NoGprIfNoAutoRenewSuite;
import com.hedera.services.bdd.suites.autorenew.TopicAutoRenewalSuite;
import com.hedera.services.bdd.suites.compose.LocalNetworkCheck;
import com.hedera.services.bdd.suites.compose.PerpetualLocalCalls;
import com.hedera.services.bdd.suites.consensus.AssortedHcsOps;
import com.hedera.services.bdd.suites.consensus.ChunkingSuite;
import com.hedera.services.bdd.suites.consensus.SubmitMessageSuite;
import com.hedera.services.bdd.suites.consensus.TopicCreateSuite;
import com.hedera.services.bdd.suites.consensus.TopicDeleteSuite;
import com.hedera.services.bdd.suites.consensus.TopicGetInfoSuite;
import com.hedera.services.bdd.suites.consensus.TopicUpdateSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractCallLocalSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractCreateSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractDeleteSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractGetBytecodeSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractGetInfoSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractMusicalChairsSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractUpdateSuite;
import com.hedera.services.bdd.suites.contract.opcodes.BalanceOperationSuite;
import com.hedera.services.bdd.suites.contract.opcodes.CallCodeOperationSuite;
import com.hedera.services.bdd.suites.contract.opcodes.CallOperationSuite;
import com.hedera.services.bdd.suites.contract.opcodes.CreateOperationSuite;
import com.hedera.services.bdd.suites.contract.opcodes.DelegateCallOperationSuite;
import com.hedera.services.bdd.suites.contract.opcodes.ExtCodeCopyOperationSuite;
import com.hedera.services.bdd.suites.contract.opcodes.ExtCodeHashOperationSuite;
import com.hedera.services.bdd.suites.contract.opcodes.ExtCodeSizeOperationSuite;
import com.hedera.services.bdd.suites.contract.opcodes.GlobalPropertiesSuite;
import com.hedera.services.bdd.suites.contract.opcodes.SStoreSuite;
import com.hedera.services.bdd.suites.contract.opcodes.SelfDestructSuite;
import com.hedera.services.bdd.suites.contract.opcodes.StaticCallOperationSuite;
import com.hedera.services.bdd.suites.contract.openzeppelin.ERC1155ContractInteractions;
import com.hedera.services.bdd.suites.contract.openzeppelin.ERC20ContractInteractions;
import com.hedera.services.bdd.suites.contract.openzeppelin.ERC721ContractInteractions;
import com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractBurnHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractKeysHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractMintHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.CryptoTransferHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.DelegatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.DissociatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.DynamicGasCostSuite;
import com.hedera.services.bdd.suites.contract.precompile.MixedHTSPrecompileTestsSuite;
import com.hedera.services.bdd.suites.contract.records.LogsSuite;
import com.hedera.services.bdd.suites.contract.records.RecordsSuite;
import com.hedera.services.bdd.suites.contract.traceability.ContractTraceabilitySuite;
import com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite;
import com.hedera.services.bdd.suites.crypto.AutoAccountUpdateSuite;
import com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite;
import com.hedera.services.bdd.suites.crypto.CryptoCornerCasesSuite;
import com.hedera.services.bdd.suites.crypto.CryptoCreateSuite;
import com.hedera.services.bdd.suites.crypto.CryptoDeleteSuite;
import com.hedera.services.bdd.suites.crypto.CryptoGetInfoRegression;
import com.hedera.services.bdd.suites.crypto.CryptoGetRecordsRegression;
import com.hedera.services.bdd.suites.crypto.CryptoTransferSuite;
import com.hedera.services.bdd.suites.crypto.CryptoUpdateSuite;
import com.hedera.services.bdd.suites.crypto.CrytoCreateSuiteWithUTF8;
import com.hedera.services.bdd.suites.crypto.HelloWorldSpec;
import com.hedera.services.bdd.suites.crypto.MiscCryptoSuite;
import com.hedera.services.bdd.suites.crypto.QueryPaymentSuite;
import com.hedera.services.bdd.suites.crypto.RandomOps;
import com.hedera.services.bdd.suites.crypto.TransferWithCustomFees;
import com.hedera.services.bdd.suites.crypto.TxnReceiptRegression;
import com.hedera.services.bdd.suites.crypto.TxnRecordRegression;
import com.hedera.services.bdd.suites.crypto.UnsupportedQueriesRegression;
import com.hedera.services.bdd.suites.fees.AllBaseOpFeesSuite;
import com.hedera.services.bdd.suites.fees.CongestionPricingSuite;
import com.hedera.services.bdd.suites.fees.CostOfEverythingSuite;
import com.hedera.services.bdd.suites.fees.CreateAndUpdateOps;
import com.hedera.services.bdd.suites.fees.OverlappingKeysSuite;
import com.hedera.services.bdd.suites.fees.QueryPaymentExploitsSuite;
import com.hedera.services.bdd.suites.fees.SpecialAccountsAreExempted;
import com.hedera.services.bdd.suites.fees.TransferListServiceFeesSuite;
import com.hedera.services.bdd.suites.file.DiverseStateCreation;
import com.hedera.services.bdd.suites.file.DiverseStateValidation;
import com.hedera.services.bdd.suites.file.ExchangeRateControlSuite;
import com.hedera.services.bdd.suites.file.FetchSystemFiles;
import com.hedera.services.bdd.suites.file.FileAppendSuite;
import com.hedera.services.bdd.suites.file.FileCreateSuite;
import com.hedera.services.bdd.suites.file.FileDeleteSuite;
import com.hedera.services.bdd.suites.file.FileUpdateSuite;
import com.hedera.services.bdd.suites.file.PermissionSemanticsSpec;
import com.hedera.services.bdd.suites.file.ProtectedFilesUpdateSuite;
import com.hedera.services.bdd.suites.file.ValidateNewAddressBook;
import com.hedera.services.bdd.suites.file.negative.AppendFailuresSpec;
import com.hedera.services.bdd.suites.file.negative.CreateFailuresSpec;
import com.hedera.services.bdd.suites.file.negative.DeleteFailuresSpec;
import com.hedera.services.bdd.suites.file.negative.QueryFailuresSpec;
import com.hedera.services.bdd.suites.file.negative.UpdateFailuresSpec;
import com.hedera.services.bdd.suites.file.positive.CreateSuccessSpec;
import com.hedera.services.bdd.suites.file.positive.SysDelSysUndelSpec;
import com.hedera.services.bdd.suites.issues.Issue1648Suite;
import com.hedera.services.bdd.suites.issues.Issue1741Suite;
import com.hedera.services.bdd.suites.issues.Issue1742Suite;
import com.hedera.services.bdd.suites.issues.Issue1744Suite;
import com.hedera.services.bdd.suites.issues.Issue1758Suite;
import com.hedera.services.bdd.suites.issues.Issue1765Suite;
import com.hedera.services.bdd.suites.issues.Issue2051Spec;
import com.hedera.services.bdd.suites.issues.Issue2098Spec;
import com.hedera.services.bdd.suites.issues.Issue2143Spec;
import com.hedera.services.bdd.suites.issues.Issue2150Spec;
import com.hedera.services.bdd.suites.issues.Issue2319Spec;
import com.hedera.services.bdd.suites.issues.Issue305Spec;
import com.hedera.services.bdd.suites.issues.Issue310Suite;
import com.hedera.services.bdd.suites.meta.VersionInfoSpec;
import com.hedera.services.bdd.suites.perf.AccountBalancesClientSaveLoadTest;
import com.hedera.services.bdd.suites.perf.AdjustFeeScheduleSuite;
import com.hedera.services.bdd.suites.perf.FileContractMemoPerfSuite;
import com.hedera.services.bdd.suites.perf.QueryOnlyLoadTest;
import com.hedera.services.bdd.suites.perf.contract.ContractCallLoadTest;
import com.hedera.services.bdd.suites.perf.contract.ContractCallLocalPerfSuite;
import com.hedera.services.bdd.suites.perf.contract.ContractCallPerfSuite;
import com.hedera.services.bdd.suites.perf.contract.ContractPerformanceSuite;
import com.hedera.services.bdd.suites.perf.contract.FibonacciPlusLoadProvider;
import com.hedera.services.bdd.suites.perf.contract.MixedSmartContractOpsLoadTest;
import com.hedera.services.bdd.suites.perf.contract.opcodes.SStoreOperationLoadTest;
import com.hedera.services.bdd.suites.perf.crypto.CryptoAllowancePerfSuite;
import com.hedera.services.bdd.suites.perf.crypto.CryptoCreatePerfSuite;
import com.hedera.services.bdd.suites.perf.crypto.CryptoTransferLoadTest;
import com.hedera.services.bdd.suites.perf.crypto.CryptoTransferLoadTestWithAutoAccounts;
import com.hedera.services.bdd.suites.perf.crypto.CryptoTransferLoadTestWithInvalidAccounts;
import com.hedera.services.bdd.suites.perf.crypto.CryptoTransferPerfSuite;
import com.hedera.services.bdd.suites.perf.crypto.CryptoTransferPerfSuiteWOpProvider;
import com.hedera.services.bdd.suites.perf.crypto.SimpleXfersAvoidingHotspot;
import com.hedera.services.bdd.suites.perf.file.FileExpansionLoadProvider;
import com.hedera.services.bdd.suites.perf.file.FileUpdateLoadTest;
import com.hedera.services.bdd.suites.perf.file.MixedFileOpsLoadTest;
import com.hedera.services.bdd.suites.perf.mixedops.MixedOpsMemoPerfSuite;
import com.hedera.services.bdd.suites.perf.mixedops.MixedTransferAndSubmitLoadTest;
import com.hedera.services.bdd.suites.perf.mixedops.MixedTransferCallAndSubmitLoadTest;
import com.hedera.services.bdd.suites.perf.schedule.OnePendingSigScheduledXfersLoad;
import com.hedera.services.bdd.suites.perf.schedule.ReadyToRunScheduledXfersLoad;
import com.hedera.services.bdd.suites.perf.token.TokenCreatePerfSuite;
import com.hedera.services.bdd.suites.perf.token.TokenRelStatusChanges;
import com.hedera.services.bdd.suites.perf.token.TokenTransferBasicLoadTest;
import com.hedera.services.bdd.suites.perf.token.TokenTransfersLoadProvider;
import com.hedera.services.bdd.suites.perf.token.UniqueTokenStateSetup;
import com.hedera.services.bdd.suites.perf.topic.CreateTopicPerfSuite;
import com.hedera.services.bdd.suites.perf.topic.HCSChunkingRealisticPerfSuite;
import com.hedera.services.bdd.suites.perf.topic.SubmitMessageLoadTest;
import com.hedera.services.bdd.suites.perf.topic.SubmitMessagePerfSuite;
import com.hedera.services.bdd.suites.perf.topic.createTopicLoadTest;
import com.hedera.services.bdd.suites.records.CharacterizationSuite;
import com.hedera.services.bdd.suites.records.ContractRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.CryptoRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.DuplicateManagementTest;
import com.hedera.services.bdd.suites.records.FileRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.RecordCreationSuite;
import com.hedera.services.bdd.suites.records.SignedTransactionBytesRecordsSuite;
import com.hedera.services.bdd.suites.regression.SplittingThrottlesWorks;
import com.hedera.services.bdd.suites.regression.SteadyStateThrottlingCheck;
import com.hedera.services.bdd.suites.regression.UmbrellaRedux;
import com.hedera.services.bdd.suites.schedule.ScheduleCreateSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleDeleteSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecStateful;
import com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleLongTermExecutionSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleLongTermSignSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleRecordSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleSignSpecs;
import com.hedera.services.bdd.suites.streaming.RunTransfers;
import com.hedera.services.bdd.suites.throttling.GasLimitThrottlingSuite;
import com.hedera.services.bdd.suites.throttling.PrivilegedOpsSuite;
import com.hedera.services.bdd.suites.throttling.ThrottleDefValidationSuite;
import com.hedera.services.bdd.suites.token.Hip17UnhappyAccountsSuite;
import com.hedera.services.bdd.suites.token.Hip17UnhappyTokensSuite;
import com.hedera.services.bdd.suites.token.TokenAssociationSpecs;
import com.hedera.services.bdd.suites.token.TokenCreateSpecs;
import com.hedera.services.bdd.suites.token.TokenDeleteSpecs;
import com.hedera.services.bdd.suites.token.TokenFeeScheduleUpdateSpecs;
import com.hedera.services.bdd.suites.token.TokenManagementSpecs;
import com.hedera.services.bdd.suites.token.TokenManagementSpecsStateful;
import com.hedera.services.bdd.suites.token.TokenMiscOps;
import com.hedera.services.bdd.suites.token.TokenPauseSpecs;
import com.hedera.services.bdd.suites.token.TokenTotalSupplyAfterMintBurnWipeSuite;
import com.hedera.services.bdd.suites.token.TokenTransactSpecs;
import com.hedera.services.bdd.suites.token.TokenUpdateSpecs;
import com.hedera.services.bdd.suites.token.UniqueTokenManagementSpecs;
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
