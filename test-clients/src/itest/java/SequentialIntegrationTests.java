/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.suites.consensus.TopicGetInfoSuite;
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
import com.hedera.services.bdd.suites.contract.opcodes.Create2OperationSuite;
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
import com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.MixedHTSPrecompileTestsSuite;
import com.hedera.services.bdd.suites.contract.records.LogsSuite;
import com.hedera.services.bdd.suites.contract.records.RecordsSuite;
import com.hedera.services.bdd.suites.contract.traceability.ContractTraceabilitySuite;
import com.hedera.services.bdd.suites.crypto.AutoAccountUpdateSuite;
import com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite;
import com.hedera.services.bdd.suites.crypto.CryptoCreateSuite;
import com.hedera.services.bdd.suites.crypto.CryptoUpdateSuite;
import com.hedera.services.bdd.suites.ethereum.EthereumSuite;
import com.hedera.services.bdd.suites.ethereum.HelloWorldEthereumSuite;
import com.hedera.services.bdd.suites.fees.CongestionPricingSuite;
import com.hedera.services.bdd.suites.fees.SpecialAccountsAreExempted;
import com.hedera.services.bdd.suites.file.ExchangeRateControlSuite;
import com.hedera.services.bdd.suites.file.FetchSystemFiles;
import com.hedera.services.bdd.suites.file.FileAppendSuite;
import com.hedera.services.bdd.suites.file.FileUpdateSuite;
import com.hedera.services.bdd.suites.file.ProtectedFilesUpdateSuite;
import com.hedera.services.bdd.suites.meta.VersionInfoSpec;
import com.hedera.services.bdd.suites.records.ContractRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.CryptoRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.FileRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.RecordCreationSuite;
import com.hedera.services.bdd.suites.regression.UmbrellaRedux;
import com.hedera.services.bdd.suites.schedule.ScheduleCreateSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleSignSpecs;
import com.hedera.services.bdd.suites.throttling.PrivilegedOpsSuite;
import com.hedera.services.bdd.suites.throttling.ThrottleDefValidationSuite;
import com.hedera.services.bdd.suites.token.TokenPauseSpecs;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/** The set of BDD tests that are sequential. */
@Execution(ExecutionMode.SAME_THREAD)
public class SequentialIntegrationTests extends IntegrationTestBase {
    @Tag("integration")
    @TestFactory
    Collection<DynamicContainer> sequential() {
        return List.of(
                extractSpecsFromSuite(RecordCreationSuite::new),
                extractSpecsFromSuite(AutoAccountUpdateSuite::new),
                extractSpecsFromSuite(GracePeriodRestrictionsSuite::new),
                extractSpecsFromSuite(CryptoApproveAllowanceSuite::new),
                extractSpecsFromSuite(TokenPauseSpecs::new),
                extractSpecsFromSuite(FileAppendSuite::new),
                extractSpecsFromSuite(FileUpdateSuite::new),
                extractSpecsFromSuite(ProtectedFilesUpdateSuite::new),
                extractSpecsFromSuite(ExchangeRateControlSuite::new),
                extractSpecsFromSuite(FileRecordsSanityCheckSuite::new),
                extractSpecsFromSuite(FetchSystemFiles::new),
                extractSpecsFromSuite(VersionInfoSpec::new),
                extractSpecsFromSuite(ContractRecordsSanityCheckSuite::new),
                //                extractSpecsFromSuite(TopicUpdateSuite::new),
                extractSpecsFromSuite(TopicGetInfoSuite::new),
                extractSpecsFromSuite(SpecialAccountsAreExempted::new),
                extractSpecsFromSuite(CryptoUpdateSuite::new),
                extractSpecsFromSuite(CryptoRecordsSanityCheckSuite::new),
                extractSpecsFromSuite(ThrottleDefValidationSuite::new),
                extractSpecsFromSuite(PrivilegedOpsSuite::new),
                extractSpecsFromSuite(CongestionPricingSuite::new),
                extractSpecsFromSuite(CryptoCreateSuite::new),
                extractSpecsFromSuite(UmbrellaRedux::new),
                extractSpecsFromSuite(ScheduleCreateSpecs::new),
                extractSpecsFromSuite(ScheduleSignSpecs::new),
                // contract.hapi
                extractSpecsFromSuite(ContractCallLocalSuite::new),
                extractSpecsFromSuite(ContractCallSuite::new),
                extractSpecsFromSuite(ContractCreateSuite::new),
                extractSpecsFromSuite(ContractDeleteSuite::new),
                extractSpecsFromSuite(ContractGetBytecodeSuite::new),
                extractSpecsFromSuite(ContractGetInfoSuite::new),
                extractSpecsFromSuite(ContractMusicalChairsSuite::new),
                extractSpecsFromSuite(ContractUpdateSuite::new),
                // contract.opcode
                extractSpecsFromSuite(BalanceOperationSuite::new),
                extractSpecsFromSuite(CallCodeOperationSuite::new),
                extractSpecsFromSuite(CallOperationSuite::new),
                extractSpecsFromSuite(CreateOperationSuite::new),
                extractSpecsFromSuite(Create2OperationSuite::new),
                extractSpecsFromSuite(DelegateCallOperationSuite::new),
                extractSpecsFromSuite(ExtCodeCopyOperationSuite::new),
                extractSpecsFromSuite(ExtCodeHashOperationSuite::new),
                extractSpecsFromSuite(ExtCodeSizeOperationSuite::new),
                extractSpecsFromSuite(GlobalPropertiesSuite::new),
                extractSpecsFromSuite(SelfDestructSuite::new),
                extractSpecsFromSuite(SStoreSuite::new),
                extractSpecsFromSuite(StaticCallOperationSuite::new),
                // contract.openzeppelin
                extractSpecsFromSuite(ERC20ContractInteractions::new),
                extractSpecsFromSuite(ERC721ContractInteractions::new),
                extractSpecsFromSuite(ERC1155ContractInteractions::new),
                // contract.precompile
                extractSpecsFromSuite(AssociatePrecompileSuite::new),
                extractSpecsFromSuite(ContractBurnHTSSuite::new),
                extractSpecsFromSuite(ContractHTSSuite::new),
                extractSpecsFromSuite(ContractKeysHTSSuite::new),
                extractSpecsFromSuite(ContractMintHTSSuite::new),
                extractSpecsFromSuite(CreatePrecompileSuite::new),
                extractSpecsFromSuite(CryptoTransferHTSSuite::new),
                extractSpecsFromSuite(DelegatePrecompileSuite::new),
                extractSpecsFromSuite(DissociatePrecompileSuite::new),
                extractSpecsFromSuite(ERCPrecompileSuite::new),
                extractSpecsFromSuite(MixedHTSPrecompileTestsSuite::new),
                // contract.records
                extractSpecsFromSuite(LogsSuite::new),
                extractSpecsFromSuite(RecordsSuite::new),
                // contract.traceability
                extractSpecsFromSuite(ContractTraceabilitySuite::new),
                // contract.ethereum
                extractSpecsFromSuite(EthereumSuite::new),
                extractSpecsFromSuite(HelloWorldEthereumSuite::new));
    }
}
