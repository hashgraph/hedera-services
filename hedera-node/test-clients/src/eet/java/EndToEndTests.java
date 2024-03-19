/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
 *
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
 */

import com.hedera.services.bdd.suites.autorenew.GracePeriodRestrictionsSuite;
// import com.hedera.services.bdd.suites.crypto.TransferWithCustomFractionalFees;
import com.hedera.services.bdd.suites.fees.CongestionPricingSuite;
import com.hedera.services.bdd.suites.file.ExchangeRateControlSuite;
import com.hedera.services.bdd.suites.file.FileUpdateSuite;
import com.hedera.services.bdd.suites.file.ProtectedFilesUpdateSuite;
import com.hedera.services.bdd.suites.leaky.FeatureFlagSuite;
import com.hedera.services.bdd.suites.records.ContractRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.CryptoRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.FileRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.RecordCreationSuite;
import com.hedera.services.bdd.suites.regression.AddressAliasIdFuzzing;
import com.hedera.services.bdd.suites.regression.HollowAccountCompletionFuzzing;
import com.hedera.services.bdd.suites.regression.TargetNetworkPrep;
import com.hedera.services.bdd.suites.regression.UmbrellaRedux;
import com.hedera.services.bdd.suites.schedule.ScheduleCreateSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleDeleteSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleRecordSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleSignSpecs;
import com.hedera.services.bdd.suites.throttling.GasLimitThrottlingSuite;
import com.hedera.services.bdd.suites.throttling.ThrottleDefValidationSuite;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndTests extends E2ETestBase {
    @Order(0)
    @Tag("setup")
    @TestFactory
    Collection<DynamicContainer> networkSetup() {
        return List.of(extractSpecsFromSuite(TargetNetworkPrep::new), extractSpecsFromSuite(FeatureFlagSuite::new));
    }

    // These tests need to run first since they are hyper-sensitive to the tests in the
    // contractPrecompile group.
    // Running these after the the contractPrecompile group will cause the
    // GasLimitOverMaxGasLimitFailsPrecheck &
    // KvLimitsEnforced tests to fail.
    @Tag("file")
    @TestFactory
    Collection<DynamicContainer> file() {
        return List.of(
                //				extractSpecsFromSuite(DiverseStateCreation::new),
                //				extractSpecsFromSuite(DiverseStateValidation::new),
                extractSpecsFromSuite(ExchangeRateControlSuite::new),
                //              extractSpecsFromSuite(FetchSystemFiles::new),
                //              extractSpecsFromSuite(FileAppendSuite::new),
                //              extractSpecsFromSuite(FileCreateSuite::new),
                //				extractSpecsFromSuite(FileDeleteSuite::new),
                extractSpecsFromSuite(FileUpdateSuite::new),
                //              extractSpecsFromSuite(PermissionSemanticsSpec::new),
                extractSpecsFromSuite(ProtectedFilesUpdateSuite::new)
                //				extractSpecsFromSuite(ValidateNewAddressBook::new)
                );
    }

    @Tag("autorenew")
    @TestFactory
    Collection<DynamicContainer> autorenew() {
        return List.of(
                //				extractSpecsFromSuite(AccountAutoRenewalSuite::new),
                //				extractSpecsFromSuite(AutoRemovalCasesSuite::new),
                extractSpecsFromSuite(GracePeriodRestrictionsSuite::new)
                // extractSpecsFromSuite(MacroFeesChargedSanityCheckSuite::new), TODO FAILS
                //				extractSpecsFromSuite(NoGprIfNoAutoRenewSuite::new),
                //				extractSpecsFromSuite(TopicAutoRenewalSuite::new)
                );
    }

    @Tag("compose")
    @TestFactory
    Collection<DynamicContainer> compose() {
        return List.of(
                //				extractSpecsFromSuite(LocalNetworkCheck::new),
                //				extractSpecsFromSuite(PerpetualLocalCalls::new)
                );
    }

    @Tag("consensus")
    @TestFactory
    Collection<DynamicContainer> consensus() {
        return List.of(
                //				extractSpecsFromSuite(AssortedHcsOps::new),
                //              extractSpecsFromSuite(ChunkingSuite::new),
                //              extractSpecsFromSuite(SubmitMessageSuite::new),
                //              extractSpecsFromSuite(TopicCreateSuite::new),
                //              extractSpecsFromSuite(TopicDeleteSuite::new),
                //              extractSpecsFromSuite(TopicGetInfoSuite::new),
                //              extractSpecsFromSuite(TopicUpdateSuite::new)
                );
    }

    @Tag("contract")
    @Tag("contract.precompile")
    @Tag("contract.precompile.part1")
    @TestFactory
    Collection<DynamicContainer> contractPrecompile() {
        return List.of(
                //              extractSpecsFromSuite(AssociatePrecompileSuite::new),
                //              extractSpecsFromSuite(ContractBurnHTSSuite::new),
                //              extractSpecsFromSuite(ContractHTSSuite::new),
                //              extractSpecsFromSuite(ContractKeysHTSSuite::new),
                //              extractSpecsFromSuite(ContractMintHTSSuite::new)
                //				extractSpecsFromSuite(CreatePrecompileSuite::new)
                );
    }

    @Tag("contract")
    @Tag("contract.precompile")
    @Tag("contract.precompile.part2")
    @TestFactory
    Collection<DynamicContainer> contractPrecompile2() {
        return List.of(
                new DynamicContainer[] {
                    //				extractSpecsFromSuite(CryptoTransferHTSSuite::new),
                    //				extractSpecsFromSuite(DelegatePrecompileSuite::new),
                    //				extractSpecsFromSuite(DynamicGasCostSuite::new),
                    //				extractSpecsFromSuite(MixedHTSPrecompileTestsSuite::new)
                });
    }

    @Tag("contract")
    @Tag("contract.openzeppelin")
    @TestFactory
    Collection<DynamicContainer> contractOpenZeppelin() {
        return List.of(
                //				extractSpecsFromSuite(ERC20ContractInteractions::new),
                //				extractSpecsFromSuite(ERC721ContractInteractions::new),
                //				extractSpecsFromSuite(ERC1155ContractInteractions::new)
                );
    }

    @Tag("contract")
    @Tag("contract.records")
    @TestFactory
    Collection<DynamicContainer> contractRecords() {
        return List.of(
                //				extractSpecsFromSuite(LogsSuite::new),
                //				extractSpecsFromSuite(RecordsSuite::new)
                );
    }

    @Tag("contract")
    @Tag("contract.opcodes")
    @TestFactory
    Collection<DynamicContainer> contractOpcodes() {
        return List.of(
                //				extractSpecsFromSuite(BalanceOperationSuite::new),
                //				extractSpecsFromSuite(CallCodeOperationSuite::new),
                //				extractSpecsFromSuite(CallOperationSuite::new),
                //				extractSpecsFromSuite(CreateOperationSuite::new),
                //				extractSpecsFromSuite(DelegateCallOperationSuite::new),
                //				extractSpecsFromSuite(ExtCodeCopyOperationSuite::new),
                //				extractSpecsFromSuite(ExtCodeHashOperationSuite::new),
                //				extractSpecsFromSuite(ExtCodeSizeOperationSuite::new),
                //				extractSpecsFromSuite(GlobalPropertiesSuite::new),
                //				extractSpecsFromSuite(SelfDestructSuite::new),
                //				extractSpecsFromSuite(SStoreSuite::new),
                //				extractSpecsFromSuite(StaticCallOperationSuite::new)
                );
    }

    @Tag("contract")
    @Tag("contract.hapi")
    @TestFactory
    Collection<DynamicContainer> contractHapi() {
        return List.of(
                new DynamicContainer[] {
                    //				extractSpecsFromSuite(ContractCallLocalSuite::new), TODO FAILS
                    //				extractSpecsFromSuite(ContractCallSuite::new), TODO FAILS
                    //				extractSpecsFromSuite(ContractCreateSuite::new),
                    //				extractSpecsFromSuite(ContractDeleteSuite::new), TODO FAILS
                    //              extractSpecsFromSuite(ContractGetBytecodeSuite::new),
                    //				extractSpecsFromSuite(ContractGetInfoSuite::new),
                    //				extractSpecsFromSuite(ContractMusicalChairsSuite::new),
                    //              extractSpecsFromSuite(ContractUpdateSuite::new)
                });
    }

    @Tag("crypto")
    @TestFactory
    Collection<DynamicContainer> crypto() {
        return List.of(
                //				extractSpecsFromSuite(AutoAccountCreationSuite::new), // TODO Fails BUT SHOULD
                // PASS
                //				extractSpecsFromSuite(AutoAccountUpdateSuite::new), // TODO Fails BUT SHOULD
                // PASS
                //              extractSpecsFromSuite(CryptoApproveAllowanceSuite::new),
                //              extractSpecsFromSuite(CryptoDeleteAllowanceSuite::new),
                //				extractSpecsFromSuite(CryptoCornerCasesSuite::new),
                //              extractSpecsFromSuite(CryptoCreateSuite::new),
                //				extractSpecsFromSuite(CryptoDeleteSuite::new),
                //				extractSpecsFromSuite(CryptoGetInfoRegression::new),
                //				extractSpecsFromSuite(CryptoGetRecordsRegression::new), // TODO Fails
                //              extractSpecsFromSuite(CryptoTransferSuite::new),
                //              extractSpecsFromSuite(CryptoUpdateSuite::new)
                //				extractSpecsFromSuite(CrytoCreateSuiteWithUTF8::new),
                //				extractSpecsFromSuite(HelloWorldSpec::new),
                //				extractSpecsFromSuite(MiscCryptoSuite::new),
                //				extractSpecsFromSuite(QueryPaymentSuite::new),
                //				extractSpecsFromSuite(RandomOps::new), // TODO Fails
                //				extractSpecsFromSuite(TransferWithCustomFixedFees::new),
                //              extractSpecsFromSuite(TransferWithCustomFractionalFees::new),
                //				extractSpecsFromSuite(TxnReceiptRegression::new),
                //				extractSpecsFromSuite(TxnRecordRegression::new), // TODO Fails
                //				extractSpecsFromSuite(UnsupportedQueriesRegression::new) // TODO Fails
                );
    }

    @Tag("fees")
    @TestFactory
    Collection<DynamicContainer> fees() {
        return List.of(
                //				extractSpecsFromSuite(AllBaseOpFeesSuite::new),
                extractSpecsFromSuite(CongestionPricingSuite::new)
                //				extractSpecsFromSuite(CostOfEverythingSuite::new),
                //				extractSpecsFromSuite(CreateAndUpdateOps::new),
                //				extractSpecsFromSuite(OverlappingKeysSuite::new),
                //				extractSpecsFromSuite(QueryPaymentExploitsSuite::new),
                //              extractSpecsFromSuite(SpecialAccountsAreExempted::new)
                //				extractSpecsFromSuite(TransferListServiceFeesSuite::new)
                );
    }

    @Tag("file")
    @Tag("file.positive")
    @TestFactory
    Collection<DynamicContainer> filePositive() {
        return List.of(
                //				extractSpecsFromSuite(CreateSuccessSpec::new),
                //              extractSpecsFromSuite(SysDelSysUndelSpec::new)
                );
    }

    @Tag("file")
    @Tag("file.negative")
    @TestFactory
    Collection<DynamicContainer> fileNegative() {
        return List.of(
                //				extractSpecsFromSuite(AppendFailuresSpec::new),
                //				extractSpecsFromSuite(CreateFailuresSpec::new),
                //				extractSpecsFromSuite(DeleteFailuresSpec::new),
                //              extractSpecsFromSuite(QueryFailuresSpec::new),
                //              extractSpecsFromSuite(UpdateFailuresSpec::new)
                );
    }

    // TODO MISSING: NewOpInConstructorSpecs, ChildStorageSpecs, BigArraySpec,
    // SmartContractInlineAssemblySpec, OCTokenSpec, SmartContractFailFirstSpec,
    // SmartContractSelfDestructSpec, DeprecatedContractKeySpecs

    @Tag("issues")
    @TestFactory
    Collection<DynamicContainer> issues() {
        return List.of(
                //				extractSpecsFromSuite(Issue305Spec::new),
                //				extractSpecsFromSuite(Issue310Suite::new),
                //				extractSpecsFromSuite(Issue1648Suite::new),
                //				extractSpecsFromSuite(Issue1741Suite::new),
                //				extractSpecsFromSuite(Issue1742Suite::new),
                //				extractSpecsFromSuite(Issue1744Suite::new),
                //				extractSpecsFromSuite(Issue1758Suite::new),
                //				extractSpecsFromSuite(Issue1765Suite::new),
                //				extractSpecsFromSuite(Issue2051Spec::new),
                //				extractSpecsFromSuite(Issue2098Spec::new),
                //				extractSpecsFromSuite(Issue2143Spec::new),
                //				extractSpecsFromSuite(Issue2150Spec::new),
                //				extractSpecsFromSuite(Issue2319Spec::new)
                );
    }

    @Tag("meta")
    @TestFactory
    Collection<DynamicContainer> meta() {
        return List.of(
                //              extractSpecsFromSuite(VersionInfoSpec::new)
                );
    }

    @Tag("meta")
    @TestFactory
    Collection<DynamicContainer> misc() {
        return List.of(
                //              extractSpecsFromSuite(CannotDeleteSystemEntitiesSuite::new)
                );
    }

    @Tag("perf")
    @TestFactory
    Collection<DynamicContainer> perf() {
        return List.of(
                //				extractSpecsFromSuite(AccountBalancesClientSaveLoadTest::new),
                //				extractSpecsFromSuite(AdjustFeeScheduleSuite::new),
                //				extractSpecsFromSuite(FileContractMemoPerfSuite::new),
                //				extractSpecsFromSuite(QueryOnlyLoadTest::new)
                );
    }

    @Tag("perf")
    @Tag("perf.contract")
    @TestFactory
    Collection<DynamicContainer> perfContract() {
        return List.of(
                //				extractSpecsFromSuite(ContractCallLoadTest::new),
                //				extractSpecsFromSuite(ContractCallLocalPerfSuite::new),
                //				extractSpecsFromSuite(ContractCallPerfSuite::new),
                //				extractSpecsFromSuite(ContractPerformanceSuite::new),
                //				extractSpecsFromSuite(FibonacciPlusLoadProvider::new),
                //				extractSpecsFromSuite(MixedSmartContractOpsLoadTest::new)
                );
    }

    @Tag("perf")
    @Tag("perf.contract.opcodes")
    @TestFactory
    Collection<DynamicContainer> perfContractOpcodes() {
        return List.of(
                //				extractSpecsFromSuite(SStoreOperationLoadTest::new)
                );
    }

    @Tag("perf")
    @Tag("perf.crypto")
    @TestFactory
    Collection<DynamicContainer> perfCrypto() {
        return List.of(
                //				extractSpecsFromSuite(CryptoAllowancePerfSuite::new),
                //				extractSpecsFromSuite(CryptoCreatePerfSuite::new),
                //				extractSpecsFromSuite(CryptoTransferLoadTest::new),
                //				extractSpecsFromSuite(CryptoTransferLoadTestWithAutoAccounts::new),
                //				extractSpecsFromSuite(CryptoTransferLoadTestWithInvalidAccounts::new),
                //				extractSpecsFromSuite(CryptoTransferPerfSuite::new),
                //				extractSpecsFromSuite(CryptoTransferPerfSuiteWOpProvider::new),
                //				extractSpecsFromSuite(SimpleXfersAvoidingHotspot::new)
                );
    }

    @Tag("perf")
    @Tag("perf.file")
    @TestFactory
    Collection<DynamicContainer> perfFile() {
        return List.of(
                //				extractSpecsFromSuite(FileExpansionLoadProvider::new),
                //				extractSpecsFromSuite(FileUpdateLoadTest::new),
                //				extractSpecsFromSuite(MixedFileOpsLoadTest::new)
                );
    }

    @Tag("perf")
    @Tag("perf.mixedops")
    @TestFactory
    Collection<DynamicContainer> perfMixedOps() {
        return List.of(
                //				extractSpecsFromSuite(MixedFileOpsLoadTest::new),
                //				extractSpecsFromSuite(MixedOpsMemoPerfSuite::new),
                //				extractSpecsFromSuite(MixedTransferAndSubmitLoadTest::new),
                //				extractSpecsFromSuite(MixedTransferCallAndSubmitLoadTest::new)
                );
    }

    @Tag("perf")
    @Tag("perf.schedule")
    @TestFactory
    Collection<DynamicContainer> perfSchedule() {
        return List.of(
                //				extractSpecsFromSuite(OnePendingSigScheduledXfersLoad::new),
                //				extractSpecsFromSuite(ReadyToRunScheduledXfersLoad::new)
                );
    }

    @Tag("perf")
    @Tag("perf.token")
    @TestFactory
    Collection<DynamicContainer> perfToken() {
        return List.of(
                //				extractSpecsFromSuite(TokenCreatePerfSuite::new),
                //				extractSpecsFromSuite(TokenRelStatusChanges::new),
                //				extractSpecsFromSuite(TokenTransferBasicLoadTest::new),
                //				extractSpecsFromSuite(TokenTransfersLoadProvider::new),
                //				extractSpecsFromSuite(UniqueTokenStateSetup::new)
                );
    }

    @Tag("perf")
    @Tag("perf.topic")
    @TestFactory
    Collection<DynamicContainer> perfTopic() {
        return List.of(
                //				extractSpecsFromSuite(createTopicLoadTest::new),
                //				extractSpecsFromSuite(CreateTopicPerfSuite::new),
                //				extractSpecsFromSuite(HCSChunkingRealisticPerfSuite::new),
                //				extractSpecsFromSuite(SubmitMessageLoadTest::new),
                //				extractSpecsFromSuite(SubmitMessagePerfSuite::new)
                );
    }

    @Tag("records")
    @TestFactory
    Collection<DynamicContainer> records() {
        return List.of(
                extractSpecsFromSuite(RecordCreationSuite::new),
                extractSpecsFromSuite(FileRecordsSanityCheckSuite::new),
                extractSpecsFromSuite(ContractRecordsSanityCheckSuite::new),
                extractSpecsFromSuite(CryptoRecordsSanityCheckSuite::new)
                //				extractSpecsFromSuite(DuplicateManagementTest::new),
                // extractSpecsFromSuite(SignedTransactionBytesRecordsSuite::new)
                );
    }

    @Tag("regression")
    @TestFactory
    Collection<DynamicContainer> regression() {
        return List.of(
                //				extractSpecsFromSuite(SplittingThrottlesWorks::new),
                //				extractSpecsFromSuite(SteadyStateThrottlingCheck::new),
                extractSpecsFromSuite(UmbrellaRedux::new),
                extractSpecsFromSuite(AddressAliasIdFuzzing::new),
                extractSpecsFromSuite(HollowAccountCompletionFuzzing::new));
    }

    @Tag("throttling")
    @TestFactory
    Collection<DynamicContainer> throttling() {
        return List.of(
                extractSpecsFromSuite(GasLimitThrottlingSuite::new),
                //				extractSpecsFromSuite(PrivilegedOpsSuite::new), TODO FAILS
                extractSpecsFromSuite(ThrottleDefValidationSuite::new));
    }

    @Tag("schedule")
    @TestFactory
    Collection<DynamicContainer> schedule() {
        return List.of(
                extractSpecsFromSuite(ScheduleCreateSpecs::new),
                extractSpecsFromSuite(ScheduleSignSpecs::new),
                extractSpecsFromSuite(ScheduleRecordSpecs::new),
                extractSpecsFromSuite(ScheduleDeleteSpecs::new),
                extractSpecsFromSuite(ScheduleExecutionSpecs::new)
                //				extractSpecsFromSuite(ScheduleExecutionSpecStateful::new),
                );
    }

    @Tag("streaming")
    @TestFactory
    Collection<DynamicContainer> streaming() {
        return List.of(
                //				extractSpecsFromSuite(RunTransfers::new)
                );
    }

    @Tag("token")
    @TestFactory
    Collection<DynamicContainer> token() {
        return List.of(
                //				extractSpecsFromSuite(Hip17UnhappyAccountsSuite::new),
                //				extractSpecsFromSuite(Hip17UnhappyTokensSuite::new),
                //              extractSpecsFromSuite(TokenAssociationSpecs::new),
                //              extractSpecsFromSuite(TokenCreateSpecs::new),
                //              extractSpecsFromSuite(TokenDeleteSpecs::new),
                //				extractSpecsFromSuite(TokenFeeScheduleUpdateSpecs::new),
                //              extractSpecsFromSuite(TokenManagementSpecs::new),
                //				extractSpecsFromSuite(TokenManagementSpecsStateful::new),
                //				extractSpecsFromSuite(TokenMiscOps::new),
                //              extractSpecsFromSuite(TokenPauseSpecs::new),
                //				extractSpecsFromSuite(TokenTotalSupplyAfterMintBurnWipeSuite::new),
                //              extractSpecsFromSuite(TokenTransactSpecs::new),
                //              extractSpecsFromSuite(TokenUpdateSpecs::new)
                //				extractSpecsFromSuite(UniqueTokenManagementSpecs::new)
                );
    }

    @Tag("utils")
    @TestFactory
    Collection<DynamicContainer> utils() {
        return List.of(
                //				extractSpecsFromSuite(SubmitMessagePerfSuite::new)
                );
    }
}
