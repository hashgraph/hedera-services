package com.hedera.services.bdd.suites;

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
import com.hedera.services.bdd.suites.reconnect.SubmitMessagesForReconnect;
import com.hedera.services.bdd.suites.reconnect.UpdateApiPermissionsDuringReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateApiPermissionStateAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateAppPropertiesStateAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateDuplicateTransactionAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateExchangeRateStateAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateFeeScheduleStateAfterReconnect;
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

// TODO: Refactor to better name
// TODO: Remove stress tests if confirmed
// TODO: Include performance tests if confirmed

// TODO: Verify in daily to run sync() or async()
// TODO: Implement a solution, extracting only failing test as console output


public class E2EGlobalRunner {
	public static void main(String[] args) {
//		runAutoRenewSuites();
//		runComposeSuites();
		runConsensusSuites();
//		runContractOpCodesSuites();
//		runContractRecordsSuites();
		runContractSuites();
		runCryptoSuites();
		runFeesSuites();
		runFileNegativeSuites();
		runFilePositiveSuites();
		runFileSuites();
//		runFreezeSuites();
//		runIssuesSuites();
//		runMetaSuites();
//		runMiscSuites();
//		runReconnectSuites();
//		runRecordsSuites();
//		runRegressionSuites();
		runScheduleSuites();
//		runStreamingSuites();
//		runThrottlingSuites();
		runTokenSuites();
	}

	// Covered
	private static void runAutoRenewSuites() {
		new AccountAutoRenewalSuite().runSuiteSync();
		new AutoRemovalCasesSuite().runSuiteSync();
		new GracePeriodRestrictionsSuite().runSuiteSync();
		new MacroFeesChargedSanityCheckSuite().runSuiteSync();
		new NoGprIfNoAutoRenewSuite().runSuiteSync();
		new TopicAutoRenewalSuite().runSuiteSync();
	}
	// Covered
	private static void runComposeSuites() {
		new LocalNetworkCheck().runSuiteSync();
		new PerpetualLocalCalls().runSuiteSync();
	}

	// Covered
	private static void runConsensusSuites() {
//		new AssortedHcsOps().runSuiteSync(); // performance test
		new ChunkingSuite().runSuiteSync();
		new SubmitMessageSuite().runSuiteSync();
		new TopicCreateSuite().runSuiteSync();
		new TopicDeleteSuite().runSuiteSync();
		new TopicGetInfoSuite().runSuiteSync();
		new TopicUpdateSuite().runSuiteSync();
	}

	// Covered
	private static void runContractOpCodesSuites() {
		new BalanceOperationSuite().runSuiteSync();
		new CallCodeOperationSuite().runSuiteSync();
		new CallOperationSuite().runSuiteSync();
		new CreateOperationSuite().runSuiteSync();
		new DelegateCallOperationSuite().runSuiteSync();
		new ExtCodeCopyOperationSuite().runSuiteSync();
		new ExtCodeHashOperationSuite().runSuiteSync();
		new ExtCodeSizeOperationSuite().runSuiteSync();
		new GlobalPropertiesSuite().runSuiteSync();
		new SStoreSuite().runSuiteSync();
		new StaticCallOperationSuite().runSuiteSync();
	}

	// Covered
	private static void runContractRecordsSuites() {
		new LogsSuite().runSuiteSync();
		new RecordsSuite().runSuiteSync();
	}

	// Covered
	private static void runContractSuites() {
		new ContractCallLocalSuite().runSuiteSync();
		new ContractCallSuite().runSuiteSync();
		new ContractCreateSuite().runSuiteSync();
		new ContractDeleteSuite().runSuiteSync();
		new ContractGetBytecodeSuite().runSuiteSync();
		new ContractGetInfoSuite().runSuiteSync();
		new ContractUpdateSuite().runSuiteSync();
	}

	// Covered
	private static void runCryptoSuites() {
		new CryptoCornerCasesSuite().runSuiteSync();
		new CryptoCreateForSuiteRunner("localhost", "3").runSuiteSync();
		new CryptoCreateSuite().runSuiteSync();
		new CryptoDeleteSuite().runSuiteSync();
		new CryptoGetInfoRegression().runSuiteSync();
		new CryptoGetRecordsRegression().runSuiteSync();
		new CryptoTransferSuite().runSuiteSync();
		new CryptoUpdateSuite().runSuiteSync();
		new CrytoCreateSuiteWithUTF8().runSuiteSync();
		new HelloWorldSpec().runSuiteSync();
//		new MiscCryptoSuite().runSuiteSync();
		new QueryPaymentSuite().runSuiteSync();
//		new RandomOps().runSuiteSync();
		new TransferWithCustomFees().runSuiteSync();
//		new TxnReceiptRegression().runSuiteSync();
//		new TxnRecordRegression().runSuiteSync();
//		new UnsupportedQueriesRegression().runSuiteSync();
	}

	// Covered
	private static void runFeesSuites() {
		new AllBaseOpFeesSuite().runSuiteSync();
		new CongestionPricingSuite().runSuiteSync();
		new CostOfEverythingSuite().runSuiteSync();
		new CreateAndUpdateOps().runSuiteSync();
		new OverlappingKeysSuite().runSuiteSync();
		new QueryPaymentExploitsSuite().runSuiteSync();
		new SpecialAccountsAreExempted().runSuiteSync();
		new TransferListServiceFeesSuite().runSuiteSync();
	}

	// Covered
	private static void runFileNegativeSuites() {
		new AppendFailuresSpec().runSuiteSync();
		new CreateFailuresSpec().runSuiteSync();
		new DeleteFailuresSpec().runSuiteSync();
		new QueryFailuresSpec().runSuiteSync();
		new UpdateFailuresSpec().runSuiteSync();
	}

	// Covered
	private static void runFilePositiveSuites() {
		new CreateSuccessSpec().runSuiteSync();
		new IssDemoSpec().runSuiteSync();
		new SysDelSysUndelSpec().runSuiteSync();
	}

	// Covered
	private static void runFileSuites() {
		new ExchangeRateControlSuite().runSuiteSync();
		new FetchSystemFiles().runSuiteSync();
		new FileAppendSuite().runSuiteSync();
		new FileCreateSuite().runSuiteSync();
		new FileDeleteSuite().runSuiteSync();
		new FileUpdateSuite().runSuiteSync();
		new PermissionSemanticsSpec().runSuiteSync();
		new ProtectedFilesUpdateSuite().runSuiteSync();
		new ValidateNewAddressBook().runSuiteSync();
	}

	// Covered
	private static void runFreezeSuites() {
		new CryptoTransferThenFreezeTest().runSuiteSync();
		new FreezeAbort().runSuiteSync();
		new FreezeDockerNetwork().runSuiteSync();
		new FreezeIntellijNetwork().runSuiteSync();
		new FreezeSuite().runSuiteSync();
		new FreezeUpgrade().runSuiteSync();
		new PrepareUpgrade().runSuiteSync();
		new SimpleFreezeOnly().runSuiteSync();
		new UpdateFileForUpgrade().runSuiteSync();
		new UpdateServerFiles().runSuiteSync();
		new UpgradeSuite().runSuiteSync();
	}

	// Covered
	private static void runIssuesSuites() {
		new Issue305Spec().runSuiteSync();
		new Issue310Suite().runSuiteSync();
		new Issue1648Suite().runSuiteSync();
		new Issue1741Suite().runSuiteSync();
		new Issue1742Suite().runSuiteSync();
		new Issue1744Suite().runSuiteSync();
		new Issue1758Suite().runSuiteSync();
		new Issue1765Suite().runSuiteSync();
		new Issue2051Spec().runSuiteSync();
		new Issue2098Spec().runSuiteSync();
		new Issue2143Spec().runSuiteSync();
		new Issue2150Spec().runSuiteSync();
		new Issue2319Spec().runSuiteSync();
	}

	// Covered
	private static void runMetaSuites() {
		new VersionInfoSpec().runSuiteSync();
	}

	private static void runMiscSuites() {
		new CannotDeleteSystemEntitiesSuite().runSuiteSync();
//		new ConsensusQueriesStressTests().runSuiteSync();
//		new ContractQueriesStressTests().runSuiteSync();
//		new CryptoQueriesStressTests().runSuiteSync();
//		new FileQueriesStressTests().runSuiteSync();
		new FreezeRekeyedState().runSuiteSync();
		new GuidedTourRemoteSuite().runSuiteSync();
		new InvalidgRPCValuesTest().runSuiteSync();
		new KeyExport().runSuiteSync();
		new MixedOpsTransactionsSuite().runSuiteSync();
		new OneOfEveryTransaction().runSuiteSync();
		//TODO: Skip this test - runs forever
//		new PerpetualTransfers().runSuiteSync();
		new PersistenceDevSuite().runSuiteSync();
		new R5BugChecks().runSuiteSync();
		new RekeySavedStateTreasury().runSuiteSync();
		new ReviewMainnetEntities().runSuiteSync();
		new TogglePayerRecordUse().runSuiteSync();
		new UtilVerbChecks().runSuiteSync();
		new WalletTestSetup().runSuiteSync();
		new ZeroStakeNodeTest().runSuiteSync();
	}

	private static void runReconnectSuites() {
		new AutoRenewEntitiesForReconnect().runSuiteSync();
		new CheckUnavailableNode().runSuiteSync();
		new CreateAccountsBeforeReconnect().runSuiteSync();
		new CreateFilesBeforeReconnect().runSuiteSync();
		new CreateSchedulesBeforeReconnect().runSuiteSync();
		new CreateTokensBeforeReconnect().runSuiteSync();
		new CreateTopicsBeforeReconnect().runSuiteSync();
		new MixedValidationsAfterReconnect().runSuiteSync();
		new ValidateAppPropertiesStateAfterReconnect().runSuiteSync();
		new SubmitMessagesForReconnect().runSuiteSync();
		new ValidateAppPropertiesStateAfterReconnect().runSuiteSync();
		new UpdateApiPermissionsDuringReconnect().runSuiteSync();
		new ValidateApiPermissionStateAfterReconnect().runSuiteSync();
		new ValidateAppPropertiesStateAfterReconnect().runSuiteSync();
		new ValidateAppPropertiesStateAfterReconnect().runSuiteSync();
		new ValidateDuplicateTransactionAfterReconnect().runSuiteSync();
		new ValidateExchangeRateStateAfterReconnect().runSuiteSync();
		new ValidateFeeScheduleStateAfterReconnect().runSuiteSync();
		new ValidateAppPropertiesStateAfterReconnect().runSuiteSync();
		new ValidateAppPropertiesStateAfterReconnect().runSuiteSync();
	}

	private static void runRecordsSuites() {
		new CharacterizationSuite().runSuiteSync();
		new ContractRecordsSanityCheckSuite().runSuiteSync();
		new CryptoRecordsSanityCheckSuite().runSuiteSync();
		new DuplicateManagementTest().runSuiteSync();
		new FeeItemization().runSuiteSync();
		new FileRecordsSanityCheckSuite().runSuiteSync();
		new MigrationValidation().runSuiteSync();
		new MigrationValidationPostSteps().runSuiteSync();
		new MigrationValidationPreSteps().runSuiteSync();
		new RecordCreationSuite().runSuiteSync();
		new RecordPurgeSuite().runSuiteSync();
		new SignedTransactionBytesRecordsSuite().runSuiteSync();
	}

	private static void runRegressionSuites() {
		new AddWellKnownEntities().runSuiteSync();
		new JrsRestartTestTemplate().runSuiteSync();
		new SteadyStateThrottlingCheck().runSuiteSync();
		new UmbrellaRedux().runSuiteSync();
		new UmbrellaReduxWithCustomNodes().runSuiteSync();

	}

	private static void runScheduleSuites() {
		new ScheduleCreateSpecs().runSuiteSync();
		new ScheduleDeleteSpecs().runSuiteAsync();
		new ScheduleExecutionSpecs().runSuiteAsync();
		new ScheduleExecutionSpecStateful().runSuiteSync();
		new ScheduleRecordSpecs().runSuiteAsync();
		new ScheduleSignSpecs().runSuiteSync();
	}

	private static void runStreamingSuites() {
		new RecordStreamValidation().runSuiteSync();
		new RunTransfers().runSuiteSync();
	}

	private static void runThrottlingSuites() {
		new PrivilegedOpsSuite().runSuiteSync();
		new ThrottleDefValidationSuite().runSuiteSync();
	}

	private static void runTokenSuites() {
		new Hip17UnhappyAccountsSuite().runSuiteSync();
		new Hip17UnhappyTokensSuite().runSuiteSync();
		final var spec = new TokenAssociationSpecs();
		spec.deferResultsSummary();
		spec.runSuiteAsync();
		spec.summarizeDeferredResults();
		new TokenCreateSpecs().runSuiteSync();
		new TokenDeleteSpecs().runSuiteAsync();
		new TokenFeeScheduleUpdateSpecs().runSuiteSync();
		new TokenManagementSpecs().runSuiteSync();
		new TokenManagementSpecsStateful().runSuiteSync();
//		new TokenMiscOps().runSuiteSync();
		new TokenPauseSpecs().runSuiteSync();
		new TokenTotalSupplyAfterMintBurnWipeSuite().runSuiteSync();
		new TokenTransactSpecs().runSuiteAsync();
		new TokenUpdateSpecs().runSuiteAsync();
		new UniqueTokenManagementSpecs().runSuiteSync();
	}
}
