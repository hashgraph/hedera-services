package com.hedera.services.bdd.suites;

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
import com.hedera.services.bdd.spec.infrastructure.HapiApiClients;
import com.hedera.services.bdd.suites.autorenew.AccountAutoRenewalSuite;
import com.hedera.services.bdd.suites.autorenew.AutoRemovalCasesSuite;
import com.hedera.services.bdd.suites.autorenew.GracePeriodRestrictionsSuite;
import com.hedera.services.bdd.suites.autorenew.MacroFeesChargedSanityCheckSuite;
import com.hedera.services.bdd.suites.autorenew.NoGprIfNoAutoRenewSuite;
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
import com.hedera.services.bdd.suites.contract.opcodes.StaticCallOperationSuite;
import com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractBurnHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractKeysHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractMintHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.CryptoTransferHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.DelegatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.DissociatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.DynamicGasCostSuite;
import com.hedera.services.bdd.suites.contract.precompile.MixedHTSPrecompileTestsSuite;
import com.hedera.services.bdd.suites.contract.records.LogsSuite;
import com.hedera.services.bdd.suites.contract.records.RecordsSuite;
import com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite;
import com.hedera.services.bdd.suites.crypto.AutoAccountUpdateSuite;
import com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite;
import com.hedera.services.bdd.suites.crypto.CryptoCornerCasesSuite;
import com.hedera.services.bdd.suites.crypto.CryptoCreateForSuiteRunner;
import com.hedera.services.bdd.suites.crypto.CryptoCreateSuite;
import com.hedera.services.bdd.suites.crypto.CryptoDeleteAllowanceSuite;
import com.hedera.services.bdd.suites.crypto.CryptoDeleteSuite;
import com.hedera.services.bdd.suites.crypto.CryptoGetInfoRegression;
import com.hedera.services.bdd.suites.crypto.CryptoTransferSuite;
import com.hedera.services.bdd.suites.crypto.CryptoUpdateSuite;
import com.hedera.services.bdd.suites.crypto.QueryPaymentSuite;
import com.hedera.services.bdd.suites.fees.CongestionPricingSuite;
import com.hedera.services.bdd.suites.fees.SpecialAccountsAreExempted;
import com.hedera.services.bdd.suites.file.ExchangeRateControlSuite;
import com.hedera.services.bdd.suites.file.FetchSystemFiles;
import com.hedera.services.bdd.suites.file.FileAppendSuite;
import com.hedera.services.bdd.suites.file.FileCreateSuite;
import com.hedera.services.bdd.suites.file.FileDeleteSuite;
import com.hedera.services.bdd.suites.file.FileUpdateSuite;
import com.hedera.services.bdd.suites.file.PermissionSemanticsSpec;
import com.hedera.services.bdd.suites.file.ProtectedFilesUpdateSuite;
import com.hedera.services.bdd.suites.file.ValidateNewAddressBook;
import com.hedera.services.bdd.suites.file.negative.UpdateFailuresSpec;
import com.hedera.services.bdd.suites.file.positive.SysDelSysUndelSpec;
import com.hedera.services.bdd.suites.freeze.CryptoTransferThenFreezeTest;
import com.hedera.services.bdd.suites.freeze.FreezeAbort;
import com.hedera.services.bdd.suites.freeze.FreezeSuite;
import com.hedera.services.bdd.suites.freeze.FreezeUpgrade;
import com.hedera.services.bdd.suites.freeze.PrepareUpgrade;
import com.hedera.services.bdd.suites.freeze.SimpleFreezeOnly;
import com.hedera.services.bdd.suites.freeze.UpdateFileForUpgrade;
import com.hedera.services.bdd.suites.freeze.UpdateServerFiles;
import com.hedera.services.bdd.suites.meta.VersionInfoSpec;
import com.hedera.services.bdd.suites.misc.CannotDeleteSystemEntitiesSuite;
import com.hedera.services.bdd.suites.misc.ConsensusQueriesStressTests;
import com.hedera.services.bdd.suites.misc.ContractQueriesStressTests;
import com.hedera.services.bdd.suites.misc.CryptoQueriesStressTests;
import com.hedera.services.bdd.suites.misc.FileQueriesStressTests;
import com.hedera.services.bdd.suites.misc.MemoValidation;
import com.hedera.services.bdd.suites.misc.MixedOpsTransactionsSuite;
import com.hedera.services.bdd.suites.misc.OneOfEveryTransaction;
import com.hedera.services.bdd.suites.misc.ZeroStakeNodeTest;
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
import com.hedera.services.bdd.suites.perf.crypto.CryptoCreatePerfSuite;
import com.hedera.services.bdd.suites.perf.crypto.CryptoTransferLoadTest;
import com.hedera.services.bdd.suites.perf.crypto.CryptoTransferLoadTestWithAutoAccounts;
import com.hedera.services.bdd.suites.perf.crypto.CryptoTransferLoadTestWithInvalidAccounts;
import com.hedera.services.bdd.suites.perf.crypto.CryptoTransferPerfSuiteWOpProvider;
import com.hedera.services.bdd.suites.perf.crypto.NWayDistNoHotspots;
import com.hedera.services.bdd.suites.perf.crypto.SimpleXfersAvoidingHotspot;
import com.hedera.services.bdd.suites.perf.file.FileUpdateLoadTest;
import com.hedera.services.bdd.suites.perf.file.MixedFileOpsLoadTest;
import com.hedera.services.bdd.suites.perf.mixedops.MixedOpsLoadTest;
import com.hedera.services.bdd.suites.perf.mixedops.MixedOpsMemoPerfSuite;
import com.hedera.services.bdd.suites.perf.mixedops.MixedTransferAndSubmitLoadTest;
import com.hedera.services.bdd.suites.perf.mixedops.MixedTransferCallAndSubmitLoadTest;
import com.hedera.services.bdd.suites.perf.schedule.ReadyToRunScheduledXfersLoad;
import com.hedera.services.bdd.suites.perf.token.TokenRelStatusChanges;
import com.hedera.services.bdd.suites.perf.token.TokenTransferBasicLoadTest;
import com.hedera.services.bdd.suites.perf.token.TokenTransfersLoadProvider;
import com.hedera.services.bdd.suites.perf.token.UniqueTokenStateSetup;
import com.hedera.services.bdd.suites.perf.topic.CreateTopicPerfSuite;
import com.hedera.services.bdd.suites.perf.topic.HCSChunkingRealisticPerfSuite;
import com.hedera.services.bdd.suites.perf.topic.SubmitMessageLoadTest;
import com.hedera.services.bdd.suites.reconnect.AutoAccountCreationValidationsAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.AutoAccountCreationsBeforeReconnect;
import com.hedera.services.bdd.suites.reconnect.AutoRenewEntitiesForReconnect;
import com.hedera.services.bdd.suites.reconnect.CheckUnavailableNode;
import com.hedera.services.bdd.suites.reconnect.CreateAccountsBeforeReconnect;
import com.hedera.services.bdd.suites.reconnect.CreateFilesBeforeReconnect;
import com.hedera.services.bdd.suites.reconnect.CreateSchedulesBeforeReconnect;
import com.hedera.services.bdd.suites.reconnect.CreateTokensBeforeReconnect;
import com.hedera.services.bdd.suites.reconnect.CreateTopicsBeforeReconnect;
import com.hedera.services.bdd.suites.reconnect.MixedValidationsAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.SchedulesExpiryDuringReconnect;
import com.hedera.services.bdd.suites.reconnect.SubmitMessagesForReconnect;
import com.hedera.services.bdd.suites.reconnect.UpdateAllProtectedFilesDuringReconnect;
import com.hedera.services.bdd.suites.reconnect.UpdateApiPermissionsDuringReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateApiPermissionStateAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateAppPropertiesStateAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateCongestionPricingAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateDuplicateTransactionAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateExchangeRateStateAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateFeeScheduleStateAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateTokensDeleteAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateTokensStateAfterReconnect;
import com.hedera.services.bdd.suites.records.ContractRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.CryptoRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.DuplicateManagementTest;
import com.hedera.services.bdd.suites.records.FileRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.RecordCreationSuite;
import com.hedera.services.bdd.suites.records.SignedTransactionBytesRecordsSuite;
import com.hedera.services.bdd.suites.regression.AddWellKnownEntities;
import com.hedera.services.bdd.suites.regression.JrsRestartTestTemplate;
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
import com.hedera.services.bdd.suites.streaming.RecordStreamValidation;
import com.hedera.services.bdd.suites.throttling.PrivilegedOpsSuite;
import com.hedera.services.bdd.suites.throttling.ResetThrottleSuite;
import com.hedera.services.bdd.suites.throttling.ResetTokenMaxPerAccount;
import com.hedera.services.bdd.suites.throttling.ThrottleDefValidationSuite;
import com.hedera.services.bdd.suites.token.Hip17UnhappyTokensSuite;
import com.hedera.services.bdd.suites.token.TokenAssociationSpecs;
import com.hedera.services.bdd.suites.token.TokenCreateSpecs;
import com.hedera.services.bdd.suites.token.TokenDeleteSpecs;
import com.hedera.services.bdd.suites.token.TokenManagementSpecs;
import com.hedera.services.bdd.suites.token.TokenManagementSpecsStateful;
import com.hedera.services.bdd.suites.token.TokenPauseSpecs;
import com.hedera.services.bdd.suites.token.TokenTransactSpecs;
import com.hedera.services.bdd.suites.token.TokenUpdateSpecs;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.HapiSpecSetup.NodeSelection.FIXED;
import static com.hedera.services.bdd.spec.HapiSpecSetup.TlsConfig.OFF;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.isIdLiteral;
import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class SuiteRunner {
	private static final Logger log = LogManager.getLogger(SuiteRunner.class);
	private static final int SUITE_NAME_WIDTH = 32;

	private static final HapiSpecSetup.TlsConfig DEFAULT_TLS_CONFIG = OFF;
	private static final HapiSpecSetup.TxnProtoStructure DEFAULT_TXN_CONFIG = HapiSpecSetup.TxnProtoStructure.ALTERNATE;
	private static final HapiSpecSetup.NodeSelection DEFAULT_NODE_SELECTOR = FIXED;

	private static final int EXPECTED_DEV_NETWORK_SIZE = 3;
	private static final int EXPECTED_CI_NETWORK_SIZE = 4;
	private static final String DEFAULT_PAYER_ID = "0.0.2";

	private static int expectedNetworkSize = EXPECTED_DEV_NETWORK_SIZE;
	private static List<HapiApiSuite> suitesToDetail = new ArrayList<>();

	private static final Map<String, Supplier<HapiApiSuite[]>> CATEGORY_MAP = new HashMap<>() {{
		/* Convenience entries, uncomment locally to run CI jobs */
//		put("CiConsensusAndCryptoJob", aof(
//				SignedTransactionBytesRecordsSuite::new,
//				DuplicateManagementTest::new,
//				TopicCreateSuite::new,
//				TopicUpdateSuite::new,
//				TopicDeleteSuite::new,
//				SubmitMessageSuite::new,
//				ChunkingSuite::new,
//				TopicGetInfoSuite::new,
//				SpecialAccountsAreExempted::new,
//				CryptoTransferSuite::new,
//				CryptoUpdateSuite::new,
//				CryptoRecordsSanityCheckSuite::new,
//				PrivilegedOpsSuite::new,
//				CannotDeleteSystemEntitiesSuite::new));
//		put("CiScheduleJob", aof(
//				ScheduleDeleteSpecs::new,
//				ScheduleExecutionSpecs::new,
//				ScheduleCreateSpecs::new,
//				ScheduleSignSpecs::new,
//				ScheduleRecordSpecs::new));
//		put("CiTokenJob", aof(
//				TokenAssociationSpecs::new,
//				TokenUpdateSpecs::new,
//				TokenCreateSpecs::new,
//				TokenDeleteSpecs::new,
//				TokenManagementSpecs::new,
//				TokenTransactSpecs::new));
//		put("CiFileJob", aof(
//				FileRecordsSanityCheckSuite::new,
//				VersionInfoSpec::new,
//				ProtectedFilesUpdateSuite::new,
//				PermissionSemanticsSpec::new,
//				SysDelSysUndelSpec::new));
//		put("CiSmartContractJob", aof(
//				ContractQueriesStressTests::new,
//				ContractCallLocalSuite::new,
//				ContractCreateSuite::new,
//				SStoreSuite::new,
//				ContractDeleteSuite::new,
//				ContractGetBytecodeSuite::new,
//				ContractGetInfoSuite::new,
//				ContractUpdateSuite::new,
//				ContractRecordsSanityCheckSuite::new,
//				ContractCallSuite::new,
//				BalanceOperationSuite::new,
//				CallCodeOperationSuite::new,
//				CallOperationSuite::new,
//				CreateOperationSuite::new,
//				DelegateCallOperationSuite::new,
//				ExtCodeCopyOperationSuite::new,
//				ExtCodeHashOperationSuite::new,
//				ExtCodeSizeOperationSuite::new,
//				GlobalPropertiesSuite::new,
//				StaticCallOperationSuite::new,
//				SStoreOperationLoadTest::new,
//				ContractCallLoadTest::new,
//				ContractCallLocalPerfSuite::new,
//				ContractCallPerfSuite::new,
//				ContractPerformanceSuite::new,
//				MixedSmartContractOpsLoadTest::new));
		/* Adjust fee schedules */
		put("AdjustFeeSchedule", aof(AdjustFeeScheduleSuite::new));
		/* Umbrella Redux */
		put("UmbrellaRedux", aof(UmbrellaRedux::new));
		/* Regression saved state management helpers */
		put("AddWellKnownEntities", aof(AddWellKnownEntities::new));
		/* JRS restart tests */
		put("RestartWithScheduledEntities", aof(JrsRestartTestTemplate::new));
		/* Load tests. */
		put("SimpleXfersAvoidingHotspot", aof(SimpleXfersAvoidingHotspot::new));
		put("NWayDistNoHotspots", aof(NWayDistNoHotspots::new));
		put("QueryOnlyLoadTest", aof(QueryOnlyLoadTest::new));
		put("TokenTransfersBasicLoadTest", aof(TokenTransferBasicLoadTest::new));
		put("AccountBalancesLoadTest", aof(AccountBalancesClientSaveLoadTest::new));
		put("TokenTransfersLoad", aof(TokenTransfersLoadProvider::new));
		put("ReadyToRunScheduledXfersLoad", aof(ReadyToRunScheduledXfersLoad::new));
		put("TokenRelChangesLoad", aof(TokenRelStatusChanges::new));
		put("FileUpdateLoadTest", aof(FileUpdateLoadTest::new));
		put("ContractCallLoadTest", aof(ContractCallLoadTest::new));
		put("SubmitMessageLoadTest", aof(SubmitMessageLoadTest::new));
		put("CryptoTransferLoadTest", aof(CryptoTransferLoadTest::new));
		put("CryptoTransferLoadTestWithAutoAccounts", aof(CryptoTransferLoadTestWithAutoAccounts::new));
		put("CryptoTransferLoadTestWithInvalidAccounts", aof(CryptoTransferLoadTestWithInvalidAccounts::new));
		put("MixedTransferAndSubmitLoadTest", aof(MixedTransferAndSubmitLoadTest::new));
		put("MixedTransferCallAndSubmitLoadTest", aof(MixedTransferCallAndSubmitLoadTest::new));
		put("HCSChunkingRealisticPerfSuite", aof(HCSChunkingRealisticPerfSuite::new));
		put("CryptoCreatePerfSuite", aof(CryptoCreatePerfSuite::new));
		put("CreateTopicPerfSuite", aof(CreateTopicPerfSuite::new));
		put("MixedOpsMemoPerfSuite", aof(MixedOpsMemoPerfSuite::new));
		put("FileContractMemoPerfSuite", aof(FileContractMemoPerfSuite::new));
		put("MixedSmartContractOpsLoadTest", aof(MixedSmartContractOpsLoadTest::new));
		put("MixedFileOpsLoadTest", aof(MixedFileOpsLoadTest::new));
		put("UniqueTokenStateSetup", aof(UniqueTokenStateSetup::new));
		/* Functional tests - RECONNECT */
		put("CreateAccountsBeforeReconnect", aof(CreateAccountsBeforeReconnect::new));
		put("CreateTopicsBeforeReconnect", aof(CreateTopicsBeforeReconnect::new));
		put("SubmitMessagesForReconnect", aof(SubmitMessagesForReconnect::new));
		put("CreateFilesBeforeReconnect", aof(CreateFilesBeforeReconnect::new));
		put("CreateTokensBeforeReconnect", aof(CreateTokensBeforeReconnect::new));
		put("CreateSchedulesBeforeReconnect", aof(CreateSchedulesBeforeReconnect::new));
		put("CheckUnavailableNode", aof(CheckUnavailableNode::new));
		put("MixedValidationsAfterReconnect", aof(MixedValidationsAfterReconnect::new));
		put("UpdateApiPermissionsDuringReconnect", aof(UpdateApiPermissionsDuringReconnect::new));
		put("ValidateDuplicateTransactionAfterReconnect", aof(ValidateDuplicateTransactionAfterReconnect::new));
		put("ValidateApiPermissionStateAfterReconnect", aof(ValidateApiPermissionStateAfterReconnect::new));
		put("ValidateAppPropertiesStateAfterReconnect", aof(ValidateAppPropertiesStateAfterReconnect::new));
		put("ValidateFeeScheduleStateAfterReconnect", aof(ValidateFeeScheduleStateAfterReconnect::new));
		put("ValidateExchangeRateStateAfterReconnect", aof(ValidateExchangeRateStateAfterReconnect::new));
		put("UpdateAllProtectedFilesDuringReconnect", aof(UpdateAllProtectedFilesDuringReconnect::new));
		put("AutoRenewEntitiesForReconnect", aof(AutoRenewEntitiesForReconnect::new));
		put("SchedulesExpiryDuringReconnect", aof(SchedulesExpiryDuringReconnect::new));
		put("ValidateTokensStateAfterReconnect", aof(ValidateTokensStateAfterReconnect::new));
		put("ValidateCongestionPricingAfterReconnect", aof(ValidateCongestionPricingAfterReconnect::new));
		/* Functional tests - AutoAccountCreations */
		put("AutoAccountCreationValidationsAfterReconnect", aof(AutoAccountCreationValidationsAfterReconnect::new));
		put("AutoAccountCreationSuite", aof(AutoAccountCreationSuite::new));
		put("AutoAccountUpdateSuite", aof(AutoAccountUpdateSuite::new));
		put("AutoAccountCreationsBeforeReconnect", aof(AutoAccountCreationsBeforeReconnect::new));
		/* Functional tests - AUTORENEW */
		put("AutoRemovalCasesSuite", aof(AutoRemovalCasesSuite::new));
		put("AccountAutoRenewalSuite", aof(AccountAutoRenewalSuite::new));
		put("GracePeriodRestrictionsSuite", aof(GracePeriodRestrictionsSuite::new));
		put("MacroFeesChargedSanityCheckSuite", aof(MacroFeesChargedSanityCheckSuite::new));
		put("NoGprIfNoAutoRenewSuite", aof(NoGprIfNoAutoRenewSuite::new));
		/* Functional tests - CONSENSUS */
		put("TopicCreateSpecs", aof(TopicCreateSuite::new));
		put("TopicDeleteSpecs", aof(TopicDeleteSuite::new));
		put("TopicUpdateSpecs", aof(TopicUpdateSuite::new));
		put("SubmitMessageSpecs", aof(SubmitMessageSuite::new));
		put("HCSTopicFragmentationSuite", aof(ChunkingSuite::new));
		put("TopicGetInfoSpecs", aof(TopicGetInfoSuite::new));
		put("ConsensusQueriesStressTests", aof(ConsensusQueriesStressTests::new));
		/* Functional tests - FILE */
		put("FileCreateSuite", aof(FileCreateSuite::new));
		put("FileAppendSuite", aof(FileAppendSuite::new));
		put("FileUpdateSuite", aof(FileUpdateSuite::new));
		put("FileDeleteSuite", aof(FileDeleteSuite::new));
		put("UpdateFailuresSpec", aof(UpdateFailuresSpec::new));
		put("ExchangeRateControlSuite", aof(ExchangeRateControlSuite::new));
		put("PermissionSemanticsSpec", aof(PermissionSemanticsSpec::new));
		put("FileQueriesStressTests", aof(FileQueriesStressTests::new));
		/* Functional tests - SCHEDULE */
		put("ScheduleCreateSpecs", aof(ScheduleCreateSpecs::new));
		put("ScheduleSignSpecs", aof(ScheduleSignSpecs::new));
		put("ScheduleLongTermExecutionSpecs", aof(ScheduleLongTermExecutionSpecs::new));
		put("ScheduleLongTermSignSpecs", aof(ScheduleLongTermSignSpecs::new));
		put("ScheduleRecordSpecs", aof(ScheduleRecordSpecs::new));
		put("ScheduleDeleteSpecs", aof(ScheduleDeleteSpecs::new));
		put("ScheduleExecutionSpecs", aof(ScheduleExecutionSpecs::new));
		put("ScheduleExecutionSpecStateful", aof(ScheduleExecutionSpecStateful::new));
		/* Functional tests - TOKEN */
		put("TokenCreateSpecs", aof(TokenCreateSpecs::new));
		put("TokenUpdateSpecs", aof(TokenUpdateSpecs::new));
		put("TokenDeleteSpecs", aof(TokenDeleteSpecs::new));
		put("TokenTransactSpecs", aof(TokenTransactSpecs::new));
		put("TokenManagementSpecs", aof(TokenManagementSpecs::new));
		put("TokenAssociationSpecs", aof(TokenAssociationSpecs::new));
		put("TokenPauseSpecs", aof(TokenPauseSpecs::new));
		put("Hip17UnhappyTokensSuite", aof(Hip17UnhappyTokensSuite::new));
		put("TokenManagementSpecsStateful", aof(TokenManagementSpecsStateful::new));
		/* Functional tests - CRYPTO */
		put("CryptoTransferSuite", aof(CryptoTransferSuite::new));
		put("CryptoDeleteSuite", aof(CryptoDeleteSuite::new));
		put("CryptoCreateSuite", aof(CryptoCreateSuite::new));
		put("CryptoUpdateSuite", aof(CryptoUpdateSuite::new));
		put("CryptoQueriesStressTests", aof(CryptoQueriesStressTests::new));
		put("CryptoCornerCasesSuite", aof(CryptoCornerCasesSuite::new));
		put("CryptoGetInfoRegression", aof(CryptoGetInfoRegression::new));
		/* Functional tests - CONTRACTS */
		put("ContractQueriesStressTests", aof(ContractQueriesStressTests::new));
		put("ContractCallLocalSuite", aof(ContractCallLocalSuite::new));
		put("ContractCreateSuite", aof(ContractCreateSuite::new));
		put("SStoreSuite", aof(SStoreSuite::new));
		put("ContractDeleteSuite", aof(ContractDeleteSuite::new));
		put("ContractGetBytecodeSuite", aof(ContractGetBytecodeSuite::new));
		put("ContractGetInfoSuite", aof(ContractGetInfoSuite::new));
		put("ContractUpdateSuite", aof(ContractUpdateSuite::new));
		put("ContractCallSuite", aof(ContractCallSuite::new));
		put("BalanceOperationSuite", aof(BalanceOperationSuite::new));
		put("CallCodeOperationSuite", aof(CallCodeOperationSuite::new));
		put("CallOperationSuite", aof(CallOperationSuite::new));
		put("CreateOperationSuite", aof(CreateOperationSuite::new));
		put("DelegateCallOperationSuite", aof(DelegateCallOperationSuite::new));
		put("ExtCodeCopyOperationSuite", aof(ExtCodeCopyOperationSuite::new));
		put("ExtCodeHashOperationSuite", aof(ExtCodeHashOperationSuite::new));
		put("ExtCodeSizeOperationSuite", aof(ExtCodeSizeOperationSuite::new));
		put("GlobalPropertiesSuite", aof(GlobalPropertiesSuite::new));
		put("StaticCallOperationSuite", aof(StaticCallOperationSuite::new));
		put("SStoreOperationLoadTest", aof(SStoreOperationLoadTest::new));
		put("ContractCallLoadTest", aof(ContractCallLoadTest::new));
		put("ContractCallLocalPerfSuite", aof(ContractCallLocalPerfSuite::new));
		put("ContractCallPerfSuite", aof(ContractCallPerfSuite::new));
		put("ContractPerformanceSuite", aof(ContractPerformanceSuite::new));
		put("MixedSmartContractOpsLoadTest", aof(MixedSmartContractOpsLoadTest::new));
		put("FibonacciPlusLoadProvider", aof(FibonacciPlusLoadProvider::new));
		put("AssociatePrecompileSuite", aof(AssociatePrecompileSuite::new));
		put("ContractBurnHTSSuite", aof(ContractBurnHTSSuite::new));
		put("ContractHTSSuite", aof(ContractHTSSuite::new));
		put("ContractKeysHTSSuite", aof(ContractKeysHTSSuite::new));
		put("ContractMintHTSSuite", aof(ContractMintHTSSuite::new));
		put("CryptoTransferHTSSuite", aof(CryptoTransferHTSSuite::new));
		put("DelegatePrecompileSuite", aof(DelegatePrecompileSuite::new));
		put("DissociatePrecompileSuite", aof(DissociatePrecompileSuite::new));
		put("DynamicGasCostSuite", aof(DynamicGasCostSuite::new));
		put("MixedHTSPrecompileTestsSuite", aof(MixedHTSPrecompileTestsSuite::new));
		/* Functional tests - AUTORENEW */
		put("AccountAutoRenewalSuite", aof(AccountAutoRenewalSuite::new));
		/* Functional tests - MIXED (record emphasis) */
		put("ThresholdRecordCreationSpecs", aof(RecordCreationSuite::new));
		put("SignedTransactionBytesRecordsSuite", aof(SignedTransactionBytesRecordsSuite::new));
		put("CryptoRecordSanityChecks", aof(CryptoRecordsSanityCheckSuite::new));
		put("FileRecordSanityChecks", aof(FileRecordsSanityCheckSuite::new));
		put("ContractRecordSanityChecks", aof(ContractRecordsSanityCheckSuite::new));
		put("LogsSuite", aof(LogsSuite::new));
		put("RecordsSuite", aof(RecordsSuite::new));
		put("ProtectedFilesUpdateSuite", aof(ProtectedFilesUpdateSuite::new));
		put("DuplicateManagementTest", aof(DuplicateManagementTest::new));
		/* Record validation. */
		put("RecordStreamValidation", aof(RecordStreamValidation::new));
		/* Fee characterization. */
		put("ControlAccountsExemptForUpdates", aof(SpecialAccountsAreExempted::new));
		/* System files. */
		put("FetchSystemFiles", aof(FetchSystemFiles::new));
		put("CannotDeleteSystemEntitiesSuite", aof(CannotDeleteSystemEntitiesSuite::new));
		/* Throttling */
		put("ThrottleDefValidationSuite", aof(ThrottleDefValidationSuite::new));
		put("ResetThrottleSuite", aof(ResetThrottleSuite::new));
		put("ResetTokenMaxPerAccount", aof(ResetTokenMaxPerAccount::new));
		put("CongestionPricingSuite", aof(CongestionPricingSuite::new));
		put("SteadyStateThrottlingCheck", aof(SteadyStateThrottlingCheck::new));
		/* Network metadata. */
		put("VersionInfoSpec", aof(VersionInfoSpec::new));
		put("FreezeSuite", aof(FreezeSuite::new));
		/* Authorization. */
		put("PrivilegedOpsSuite", aof(PrivilegedOpsSuite::new));
		put("SysDelSysUndelSpec", aof(SysDelSysUndelSpec::new));
		/* Freeze and update */
		put("UpdateServerFiles", aof(UpdateServerFiles::new));
		put("OneOfEveryTxn", aof(OneOfEveryTransaction::new));
		/* Zero Stake behaviour */
		put("ZeroStakeTest", aof(ZeroStakeNodeTest::new));
		/* Query payment validation */
		put("QueryPaymentSuite", aof(QueryPaymentSuite::new));
		put("SimpleFreezeOnly", aof(SimpleFreezeOnly::new));
		/* Transfer then freeze */
		put("CryptoTransferThenFreezeTest", aof(CryptoTransferThenFreezeTest::new));
		put("MixedOpsTransactionsSuite", aof(MixedOpsTransactionsSuite::new));
		put("MixedOpsLoadTest", aof(MixedOpsLoadTest::new));
		/* Validate new AddressBook */
		put("ValidateNewAddressBook", aof(ValidateNewAddressBook::new));
		put("CryptoTransferPerfSuiteWOpProvider", aof(CryptoTransferPerfSuiteWOpProvider::new));
		put("ValidateTokensDeleteAfterReconnect", aof(ValidateTokensDeleteAfterReconnect::new));
		/* Freeze with upgrade */
		put("UpdateFileForUpgrade", aof(UpdateFileForUpgrade::new));
		put("PrepareUpgrade", aof(PrepareUpgrade::new));
		put("FreezeUpgrade", aof(FreezeUpgrade::new));
		put("FreezeAbort", aof(FreezeAbort::new));
		/* Memo validation */
		put("MemoValidation", aof(MemoValidation::new));
		/* Approval and Allowance */
		put("CryptoApproveAllowanceSuite", aof(CryptoApproveAllowanceSuite::new));
		put("CryptoDeleteAllowanceSuite", aof(CryptoDeleteAllowanceSuite::new));
	}};

	static boolean runAsync;
	static List<CategorySuites> targetCategories;
	static boolean globalPassFlag = true;

	private static final String TLS_ARG = "-TLS";
	private static final String TXN_ARG = "-TXN";
	private static final String NODE_SELECTOR_ARG = "-NODE";
	/* Specify the network size so that we can read the appropriate throttle settings for that network. */
	private static final String NETWORK_SIZE_ARG = "-NETWORKSIZE";
	/* Specify the network to run legacy SC tests instead of using suiterunner */
	private static final String LEGACY_SMART_CONTRACT_TESTS = "SmartContractAggregatedTests";
	private static String payerId = DEFAULT_PAYER_ID;

	public static void main(String... args) throws Exception {
		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		String[] effArgs = trueArgs(args);
		log.info("Effective args :: " + List.of(effArgs));
		if (Stream.of(effArgs).anyMatch("-CI"::equals)) {
			var tlsOverride = overrideOrDefault(effArgs, TLS_ARG, DEFAULT_TLS_CONFIG.toString());
			var txnOverride = overrideOrDefault(effArgs, TXN_ARG, DEFAULT_TXN_CONFIG.toString());
			var nodeSelectorOverride = overrideOrDefault(effArgs, NODE_SELECTOR_ARG, DEFAULT_NODE_SELECTOR.toString());
			expectedNetworkSize = Integer.parseInt(overrideOrDefault(effArgs,
					NETWORK_SIZE_ARG,
					"" + EXPECTED_CI_NETWORK_SIZE).split("=")[1]);
			var otherOverrides = arbitraryOverrides(effArgs);
			// For HTS perf regression test, we need to know the number of clients to distribute
			// the creation of the test tokens and token associations to each client.
			// For current perf test setup, this number will be the size of test network.
			if (!otherOverrides.containsKey("totalClients")) {
				otherOverrides.put("totalClients", "" + expectedNetworkSize);
			}

			createPayerAccount(System.getenv("NODES"), args[1]);

			HapiApiSpec.runInCiMode(
					System.getenv("NODES"),
					payerId,
					args[1],
					tlsOverride.substring(TLS_ARG.length() + 1),
					txnOverride.substring(TXN_ARG.length() + 1),
					nodeSelectorOverride.substring(NODE_SELECTOR_ARG.length() + 1),
					otherOverrides);
		}
		Map<Boolean, List<String>> statefulCategories = Stream
				.of(effArgs)
				.filter(CATEGORY_MAP::containsKey)
				.collect(groupingBy(cat -> SuiteRunner.categoryLeaksState(CATEGORY_MAP.get(cat).get())));

		Map<String, List<CategoryResult>> byRunType = new HashMap<>();
		if (statefulCategories.get(Boolean.TRUE) != null) {
			runAsync = false;
			byRunType.put("sync", runCategories(statefulCategories.get(Boolean.TRUE)));
		}
		if (statefulCategories.get(Boolean.FALSE) != null) {
			runAsync = true;
			byRunType.put("async", runCategories(statefulCategories.get(Boolean.FALSE)));
		}
		summarizeResults(byRunType);
		HapiApiClients.tearDown();

		System.exit(globalPassFlag ? 0 : 1);
	}

	/**
	 * Create a default payer account for each test client while running JRS regression tests
	 *
	 * @param nodes
	 * @param defaultNode
	 */
	private static void createPayerAccount(String nodes, String defaultNode) {
		Random r = new Random();
		try {
			Thread.sleep(r.nextInt(5000));
			new CryptoCreateForSuiteRunner(nodes, defaultNode).runSuiteAsync();
			Thread.sleep(2000);
			if (!isIdLiteral(payerId)) {
				payerId = DEFAULT_PAYER_ID;
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static String overrideOrDefault(String[] effArgs, String argPrefix, String defaultValue) {
		return Stream.of(effArgs)
				.filter(arg -> arg.startsWith(argPrefix))
				.findAny()
				.orElse(String.format("%s=%s", argPrefix, defaultValue));
	}

	private static Map<String, String> arbitraryOverrides(String[] effArgs) {
		var MISC_OVERRIDE_PATTERN = Pattern.compile("([^-].*?)=(.*)");
		return Stream.of(effArgs)
				.map(arg -> MISC_OVERRIDE_PATTERN.matcher(arg))
				.filter(Matcher::matches)
				.collect(toMap(m -> m.group(1), m -> m.group(2)));
	}

	private static String[] trueArgs(String[] args) {
		String ciArgs = Optional.ofNullable(System.getenv("DSL_SUITE_RUNNER_ARGS")).orElse("");
		log.info("Args from CircleCI environment: |" + ciArgs + "|");

		return StringUtils.isNotEmpty(ciArgs)
				? Stream.of(args, new Object[] { "-CI" }, getEffectiveDSLSuiteRunnerArgs(ciArgs))
				.flatMap(Stream::of)
				.toArray(String[]::new)
				: args;
	}

	/**
	 * Check if the DSL_SUITE_RUNNER_ARGS contain ALL_SUITES.
	 * If so, add all test suites from CATEGORY_MAP to args that should be run.
	 *
	 * @param realArgs
	 * 		DSL_SUITE_RUNNER_ARGS provided
	 * @return effective args after examining DSL_SUITE_RUNNER_ARGS
	 */
	private static String[] getEffectiveDSLSuiteRunnerArgs(String realArgs) {
		Set<String> effectiveArgs = new HashSet<>();
		String[] ciArgs = realArgs.split("\\s+");

		if (Arrays.asList(ciArgs).contains("ALL_SUITES")) {
			effectiveArgs.addAll(CATEGORY_MAP.keySet());
			effectiveArgs.addAll(Stream.of(ciArgs).
					filter(e -> !e.equals("ALL_SUITES")).
					collect(Collectors.toList()));
			log.info("Effective args when running ALL_SUITES : " + effectiveArgs);
			return effectiveArgs.toArray(new String[0]);
		}

		return ciArgs;
	}

	private static List<CategoryResult> runCategories(List<String> args) {
		collectTargetCategories(args);
		return runTargetCategories();
	}

	private static void summarizeResults(Map<String, List<CategoryResult>> byRunType) {
		byRunType.entrySet().stream().forEach(entry -> {
			log.info("============== " + entry.getKey() + " run results ==============");
			List<CategoryResult> results = entry.getValue();
			for (CategoryResult result : results) {
				log.info(result.summary);
				for (HapiApiSuite failed : result.failedSuites) {
					String specList = failed.getFinalSpecs().stream()
							.filter(HapiApiSpec::NOT_OK)
							.map(HapiApiSpec::toString)
							.collect(joining(", "));
					log.info("  --> Problems in suite '" + failed.name() + "' :: " + specList);
				}
				globalPassFlag &= result.failedSuites.isEmpty();
			}
		});
		log.info("============== SuiteRunner finished ==============");

		/* Print detail summaries for analysis by HapiClientValidator */
		suitesToDetail.forEach(HapiApiSuite::summarizeDeferredResults);
	}

	private static boolean categoryLeaksState(HapiApiSuite[] suites) {
		return Stream.of(suites).anyMatch(suite -> !suite.canRunConcurrent());
	}

	private static List<CategoryResult> runTargetCategories() {
		if (runAsync) {
			return accumulateAsync(
					targetCategories.stream().toArray(n -> new CategorySuites[n]),
					sbc -> runSuitesAsync(sbc.category, sbc.suites));
		} else {
			return targetCategories.stream().map(sbc -> runSuitesSync(sbc.category, sbc.suites)).collect(toList());
		}
	}

	private static void collectTargetCategories(List<String> args) {
		targetCategories = args
				.stream()
				.filter(k -> null != CATEGORY_MAP.get(k))
				.map(k -> new CategorySuites(rightPadded(k, SUITE_NAME_WIDTH), CATEGORY_MAP.get(k).get()))
				.peek(cs -> List.of(cs.suites).forEach(suite -> {
					suite.skipClientTearDown();
					suite.deferResultsSummary();
					suitesToDetail.add(suite);
				})).collect(toList());
	}

	private static CategoryResult runSuitesAsync(String category, HapiApiSuite[] suites) {
		List<FinalOutcome> outcomes = accumulateAsync(suites, HapiApiSuite::runSuiteAsync);
		List<HapiApiSuite> failed = IntStream.range(0, suites.length)
				.filter(i -> outcomes.get(i) != FinalOutcome.SUITE_PASSED)
				.mapToObj(i -> suites[i])
				.collect(toList());
		return summaryOf(category, suites, failed);
	}

	private static CategoryResult runSuitesSync(String category, HapiApiSuite[] suites) {
		List<HapiApiSuite> failed = Stream.of(suites)
				.filter(suite -> suite.runSuiteSync() != FinalOutcome.SUITE_PASSED)
				.collect(toList());
		return summaryOf(category, suites, failed);
	}

	private static CategoryResult summaryOf(String category, HapiApiSuite[] suites, List<HapiApiSuite> failed) {
		int numPassed = suites.length - failed.size();
		String summary = category + " :: " + numPassed + "/" + suites.length + " suites ran OK";
		return new CategoryResult(summary, failed);
	}

	private static <T, R> List<R> accumulateAsync(T[] inputs, Function<T, R> f) {
		final List<R> outputs = new ArrayList<>();
		for (int i = 0; i < inputs.length; i++) {
			outputs.add(null);
		}
		CompletableFuture<Void> future = CompletableFuture.allOf(
				IntStream.range(0, inputs.length)
						.mapToObj(i -> runAsync(() -> outputs.set(i, f.apply(inputs[i]))))
						.toArray(n -> new CompletableFuture[n]));
		future.join();
		return outputs;
	}

	static class CategoryResult {
		final String summary;
		final List<HapiApiSuite> failedSuites;

		CategoryResult(String summary, List<HapiApiSuite> failedSuites) {
			this.summary = summary;
			this.failedSuites = failedSuites;
		}
	}

	static class CategorySuites {
		final String category;
		final HapiApiSuite[] suites;

		CategorySuites(String category, HapiApiSuite[] suites) {
			this.category = category;
			this.suites = suites;
		}
	}

	static private String rightPadded(String s, int width) {
		if (s.length() == width) {
			return s;
		} else if (s.length() > width) {
			int cutLen = (width - 3) / 2;
			return s.substring(0, cutLen) + "..." + s.substring(s.length() - cutLen);
		} else {
			return s + IntStream.range(0, width - s.length()).mapToObj(ignore -> " ").collect(joining(""));
		}
	}

//	@SafeVarargs
//	public static <T> Supplier<T[]> aof(Supplier<T>... items) {
//		return () -> (T[]) List.of(items)
//				.stream()
//				.map(Supplier::get)
//				.toArray();
//	}

	public static Supplier<HapiApiSuite[]> aof(Supplier<HapiApiSuite>... items) {
		return () -> {
			HapiApiSuite[] suites = new HapiApiSuite[items.length];
			for (int i = 0; i < items.length; i++) {
				suites[i] = items[i].get();
			}
			;
			return suites;
		};
	}

	public static void setPayerId(String payerId) {
		SuiteRunner.payerId = payerId;
	}

}
