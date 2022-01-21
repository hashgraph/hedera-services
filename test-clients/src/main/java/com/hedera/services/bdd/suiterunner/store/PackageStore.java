package com.hedera.services.bdd.suiterunner.store;

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
import com.hedera.services.bdd.suites.contract.ContractCallLocalSuite;
import com.hedera.services.bdd.suites.contract.ContractCallSuite;
import com.hedera.services.bdd.suites.contract.ContractCreateSuite;
import com.hedera.services.bdd.suites.contract.ContractDeleteSuite;
import com.hedera.services.bdd.suites.contract.ContractGetBytecodeSuite;
import com.hedera.services.bdd.suites.contract.ContractGetInfoSuite;
import com.hedera.services.bdd.suites.contract.ContractUpdateSuite;
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
import com.hedera.services.bdd.suites.contract.records.LogsSuite;
import com.hedera.services.bdd.suites.contract.records.RecordsSuite;
import com.hedera.services.bdd.suites.crypto.CryptoCornerCasesSuite;
import com.hedera.services.bdd.suites.crypto.CryptoCreateForSuiteRunner;
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
import com.hedera.services.bdd.suites.file.positive.IssDemoSpec;
import com.hedera.services.bdd.suites.file.positive.SysDelSysUndelSpec;
import com.hedera.services.bdd.suites.freeze.CryptoTransferThenFreezeTest;
import com.hedera.services.bdd.suites.freeze.FreezeAbort;
import com.hedera.services.bdd.suites.freeze.FreezeDockerNetwork;
import com.hedera.services.bdd.suites.freeze.FreezeIntellijNetwork;
import com.hedera.services.bdd.suites.freeze.FreezeSuite;
import com.hedera.services.bdd.suites.freeze.FreezeUpgrade;
import com.hedera.services.bdd.suites.freeze.PrepareUpgrade;
import com.hedera.services.bdd.suites.freeze.SimpleFreezeOnly;
import com.hedera.services.bdd.suites.freeze.UpdateFileForUpgrade;
import com.hedera.services.bdd.suites.freeze.UpdateServerFiles;
import com.hedera.services.bdd.suites.freeze.UpgradeSuite;
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
import com.hedera.services.bdd.suites.misc.CannotDeleteSystemEntitiesSuite;
import com.hedera.services.bdd.suites.misc.ConsensusQueriesStressTests;
import com.hedera.services.bdd.suites.misc.ContractQueriesStressTests;
import com.hedera.services.bdd.suites.misc.CryptoQueriesStressTests;
import com.hedera.services.bdd.suites.misc.FileQueriesStressTests;
import com.hedera.services.bdd.suites.misc.FreezeRekeyedState;
import com.hedera.services.bdd.suites.misc.GuidedTourRemoteSuite;
import com.hedera.services.bdd.suites.misc.InvalidgRPCValuesTest;
import com.hedera.services.bdd.suites.misc.KeyExport;
import com.hedera.services.bdd.suites.misc.MixedOpsTransactionsSuite;
import com.hedera.services.bdd.suites.misc.OneOfEveryTransaction;
import com.hedera.services.bdd.suites.misc.PerpetualTransfers;
import com.hedera.services.bdd.suites.misc.PersistenceDevSuite;
import com.hedera.services.bdd.suites.misc.R5BugChecks;
import com.hedera.services.bdd.suites.misc.RekeySavedStateTreasury;
import com.hedera.services.bdd.suites.misc.ReviewMainnetEntities;
import com.hedera.services.bdd.suites.misc.TogglePayerRecordUse;
import com.hedera.services.bdd.suites.misc.UtilVerbChecks;
import com.hedera.services.bdd.suites.misc.WalletTestSetup;
import com.hedera.services.bdd.suites.misc.ZeroStakeNodeTest;
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
import com.hedera.services.bdd.suites.records.CharacterizationSuite;
import com.hedera.services.bdd.suites.records.ContractRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.CryptoRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.DuplicateManagementTest;
import com.hedera.services.bdd.suites.records.FeeItemization;
import com.hedera.services.bdd.suites.records.FileRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.MigrationValidation;
import com.hedera.services.bdd.suites.records.MigrationValidationPostSteps;
import com.hedera.services.bdd.suites.records.MigrationValidationPreSteps;
import com.hedera.services.bdd.suites.records.RecordCreationSuite;
import com.hedera.services.bdd.suites.records.RecordPurgeSuite;
import com.hedera.services.bdd.suites.records.SignedTransactionBytesRecordsSuite;
import com.hedera.services.bdd.suites.regression.AddWellKnownEntities;
import com.hedera.services.bdd.suites.regression.JrsRestartTestTemplate;
import com.hedera.services.bdd.suites.regression.SteadyStateThrottlingCheck;
import com.hedera.services.bdd.suites.regression.UmbrellaRedux;
import com.hedera.services.bdd.suites.regression.UmbrellaReduxWithCustomNodes;
import com.hedera.services.bdd.suites.schedule.ScheduleCreateSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleDeleteSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecStateful;
import com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleRecordSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleSignSpecs;
import com.hedera.services.bdd.suites.streaming.RecordStreamValidation;
import com.hedera.services.bdd.suites.streaming.RunTransfers;
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

import java.util.List;

import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.AUTORENEW_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.COMPOSE_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.CONSENSUS_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.CONTRACT_OP_CODES_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.CONTRACT_RECORDS_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.CONTRACT_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.CRYPTO_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.FEES_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.FILE_NEGATIVE_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.FILE_POSITIVE_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.FILE_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.FREEZE_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.ISSUES_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.META_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.MISC_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.RECONNECT_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.RECORDS_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.REGRESSION_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.SCHEDULE_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.STREAMING_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.THROTTLING_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.TOKEN_SUITES;

public class PackageStore extends SuiteStore {
	@Override
	protected void initializeSuites() {
		suites.put(AUTORENEW_SUITES, () -> List.of(
				new AccountAutoRenewalSuite(),
				new AutoRemovalCasesSuite(),
				new GracePeriodRestrictionsSuite(),
				new MacroFeesChargedSanityCheckSuite(),
				new NoGprIfNoAutoRenewSuite(),
				new TopicAutoRenewalSuite()));

		suites.put(COMPOSE_SUITES, () -> List.of(
				new LocalNetworkCheck(),
				new PerpetualLocalCalls()
		));

		suites.put(CONSENSUS_SUITES, () -> List.of(
				new AssortedHcsOps(),
				new ChunkingSuite(),
				new SubmitMessageSuite(),
				new TopicCreateSuite(),
				new TopicDeleteSuite(),
				new TopicGetInfoSuite(),
				new TopicUpdateSuite()
		));

		suites.put(CONTRACT_OP_CODES_SUITES, () -> List.of(
//				new BalanceOperationSuite(),
//				new CallCodeOperationSuite(),
//				new CallOperationSuite(),
//				new CreateOperationSuite(),
//				new DelegateCallOperationSuite(),
//				new ExtCodeCopyOperationSuite(),
//				new ExtCodeHashOperationSuite(),
//				new ExtCodeSizeOperationSuite(),
//				new GlobalPropertiesSuite(),
				new SStoreSuite()
//				new StaticCallOperationSuite()
		));

		suites.put(CONTRACT_RECORDS_SUITES, () -> List.of(
				new LogsSuite(),
				new RecordsSuite()
		));

		suites.put(CONTRACT_SUITES, () -> List.of(
				new ContractCallLocalSuite(),
				new ContractCallSuite(),
				new ContractCreateSuite(),
				new ContractDeleteSuite(),
				new ContractGetBytecodeSuite(),
				new ContractGetInfoSuite(),
				new ContractUpdateSuite()
		));

		suites.put(CRYPTO_SUITES, () -> List.of(
				new CryptoCornerCasesSuite(),
				new CryptoCreateForSuiteRunner("localhost", "3"),
				new CryptoCreateSuite(),
				new CryptoDeleteSuite(),
				new CryptoGetInfoRegression(),
				new CryptoGetRecordsRegression(),
				new CryptoTransferSuite(),
				new CryptoUpdateSuite(),
				new CrytoCreateSuiteWithUTF8(),
				new HelloWorldSpec(),
				new MiscCryptoSuite(),
				new QueryPaymentSuite(),
				new RandomOps(),
				new TransferWithCustomFees(),
				new TxnReceiptRegression(),
				new TxnRecordRegression(),
				new UnsupportedQueriesRegression()
		));

		suites.put(FEES_SUITES, () -> List.of(
				new AllBaseOpFeesSuite(),
				new CongestionPricingSuite(),
				new CostOfEverythingSuite(),
				new CreateAndUpdateOps(),
				new OverlappingKeysSuite(),
				new QueryPaymentExploitsSuite(),
				new SpecialAccountsAreExempted(),
				new TransferListServiceFeesSuite()
		));

		suites.put(FILE_NEGATIVE_SUITES, () -> List.of(
				new AppendFailuresSpec(),
				new CreateFailuresSpec(),
				new DeleteFailuresSpec(),
				new QueryFailuresSpec(),
				new UpdateFailuresSpec()
		));

		suites.put(FILE_POSITIVE_SUITES, () -> List.of(
				new CreateSuccessSpec(),
				new IssDemoSpec(),
				new SysDelSysUndelSpec()
		));

		suites.put(FILE_SUITES, () -> List.of(
				new ExchangeRateControlSuite(),
				new FetchSystemFiles(),
				new FileAppendSuite(),
				new FileCreateSuite(),
				new FileDeleteSuite(),
				new FileUpdateSuite(),
				new PermissionSemanticsSpec(),
				new ProtectedFilesUpdateSuite(),
				new ValidateNewAddressBook()
		));

		suites.put(FREEZE_SUITES, () -> List.of(
				new CryptoTransferThenFreezeTest(),
				new FreezeAbort(),
				new FreezeDockerNetwork(),
				new FreezeIntellijNetwork(),
				new FreezeSuite(),
				new FreezeUpgrade(),
				new PrepareUpgrade(),
				new SimpleFreezeOnly(),
				new UpdateFileForUpgrade(),
				new UpdateServerFiles(),
				new UpgradeSuite()
		));

		suites.put(ISSUES_SUITES, () -> List.of(
				new Issue305Spec(),
				new Issue310Suite(),
				new Issue1648Suite(),
				new Issue1741Suite(),
				new Issue1742Suite(),
				new Issue1744Suite(),
				new Issue1758Suite(),
				new Issue1765Suite(),
				new Issue2051Spec(),
				new Issue2098Spec(),
				new Issue2143Spec(),
				new Issue2150Spec(),
				new Issue2319Spec()
		));

		suites.put(META_SUITES, () -> List.of(new VersionInfoSpec()));

		suites.put(MISC_SUITES, () -> List.of(
				new CannotDeleteSystemEntitiesSuite(),
				new ConsensusQueriesStressTests(),
				new ContractQueriesStressTests(),
				new CryptoQueriesStressTests(),
				new FileQueriesStressTests(),
				new FreezeRekeyedState(),
				new GuidedTourRemoteSuite(),
				new InvalidgRPCValuesTest(),
				new KeyExport(),
				new MixedOpsTransactionsSuite(),
				new OneOfEveryTransaction(),
				new PerpetualTransfers(),
				new PersistenceDevSuite(),
				new R5BugChecks(),
				new RekeySavedStateTreasury(),
				new ReviewMainnetEntities(),
				new TogglePayerRecordUse(),
				new UtilVerbChecks(),
				new WalletTestSetup(),
				new ZeroStakeNodeTest()
		));

		suites.put(RECONNECT_SUITES, () -> List.of(
				new AutoRenewEntitiesForReconnect(),
				new CheckUnavailableNode(),
				new CreateAccountsBeforeReconnect(),
				new CreateFilesBeforeReconnect(),
				new CreateSchedulesBeforeReconnect(),
				new CreateTokensBeforeReconnect(),
				new CreateTopicsBeforeReconnect(),
				new MixedValidationsAfterReconnect(),
				new SchedulesExpiryDuringReconnect(),
				new SubmitMessagesForReconnect(),
				new UpdateAllProtectedFilesDuringReconnect(),
				new UpdateApiPermissionsDuringReconnect(),
				new ValidateApiPermissionStateAfterReconnect(),
				new ValidateAppPropertiesStateAfterReconnect(),
				new ValidateCongestionPricingAfterReconnect(),
				new ValidateDuplicateTransactionAfterReconnect(),
				new ValidateExchangeRateStateAfterReconnect(),
				new ValidateFeeScheduleStateAfterReconnect(),
				new ValidateTokensDeleteAfterReconnect(),
				new ValidateTokensStateAfterReconnect()
		));

		suites.put(RECORDS_SUITES, () -> List.of(
				new CharacterizationSuite(),
				new ContractRecordsSanityCheckSuite(),
				new CryptoRecordsSanityCheckSuite(),
				new DuplicateManagementTest(),
				new FeeItemization(),
				new FileRecordsSanityCheckSuite(),
				new MigrationValidation(),
				new MigrationValidationPostSteps(),
				new MigrationValidationPreSteps(),
				new RecordCreationSuite(),
				new RecordPurgeSuite(),
				new SignedTransactionBytesRecordsSuite()
		));

		suites.put(REGRESSION_SUITES, () -> List.of(
				new AddWellKnownEntities(),
				new JrsRestartTestTemplate(),
				new SteadyStateThrottlingCheck(),
				new UmbrellaRedux(),
				new UmbrellaReduxWithCustomNodes()
		));

		suites.put(SCHEDULE_SUITES, () -> List.of(
				new ScheduleCreateSpecs(),
				new ScheduleDeleteSpecs(),
				new ScheduleExecutionSpecs(),
				new ScheduleExecutionSpecStateful(),
				new ScheduleRecordSpecs(),
				new ScheduleSignSpecs()
		));

		suites.put(STREAMING_SUITES, () -> List.of(
				new RecordStreamValidation(),
				new RunTransfers()
		));

		suites.put(THROTTLING_SUITES, () -> List.of(
				new PrivilegedOpsSuite(),
				new ThrottleDefValidationSuite()
		));

		suites.put(TOKEN_SUITES, () -> List.of(
				new Hip17UnhappyAccountsSuite(),
				new Hip17UnhappyTokensSuite(),
				new TokenAssociationSpecs(),
				new TokenCreateSpecs(),
				new TokenDeleteSpecs(),
				new TokenFeeScheduleUpdateSpecs(),
				new TokenManagementSpecs(),
				new TokenManagementSpecsStateful(),
				new TokenMiscOps(),
				new TokenPauseSpecs(),
				new TokenTotalSupplyAfterMintBurnWipeSuite(),
				new TokenTransactSpecs(),
				new TokenUpdateSpecs(),
				new UniqueTokenManagementSpecs()
		));
	}

}
