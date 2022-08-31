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
import com.hedera.services.bdd.suites.consensus.ChunkingSuite;
import com.hedera.services.bdd.suites.consensus.SubmitMessageSuite;
import com.hedera.services.bdd.suites.consensus.TopicCreateSuite;
import com.hedera.services.bdd.suites.consensus.TopicDeleteSuite;
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
import com.hedera.services.bdd.suites.contract.precompile.ApproveAllowanceSuite;
import com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractBurnHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractKeysHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractMintHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.CryptoTransferHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.DefaultTokenStatusSuite;
import com.hedera.services.bdd.suites.contract.precompile.DelegatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.DeleteTokenPrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.DissociatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.FreezeUnfreezeTokenPrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.GrantRevokeKycSuite;
import com.hedera.services.bdd.suites.contract.precompile.MixedHTSPrecompileTestsSuite;
import com.hedera.services.bdd.suites.contract.precompile.PauseUnpauseTokenAccountPrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.PrngPrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.TokenAndTypeCheckSuite;
import com.hedera.services.bdd.suites.contract.precompile.TokenExpiryInfoSuite;
import com.hedera.services.bdd.suites.contract.precompile.TokenInfoHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.TokenUpdatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.WipeTokenAccountPrecompileSuite;
import com.hedera.services.bdd.suites.contract.records.LogsSuite;
import com.hedera.services.bdd.suites.contract.records.RecordsSuite;
import com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite;
import com.hedera.services.bdd.suites.crypto.CryptoTransferSuite;
import com.hedera.services.bdd.suites.ethereum.EthereumSuite;
import com.hedera.services.bdd.suites.ethereum.HelloWorldEthereumSuite;
import com.hedera.services.bdd.suites.file.FileCreateSuite;
import com.hedera.services.bdd.suites.file.PermissionSemanticsSpec;
import com.hedera.services.bdd.suites.file.negative.UpdateFailuresSpec;
import com.hedera.services.bdd.suites.file.positive.SysDelSysUndelSpec;
import com.hedera.services.bdd.suites.misc.CannotDeleteSystemEntitiesSuite;
import com.hedera.services.bdd.suites.records.SignedTransactionBytesRecordsSuite;
import com.hedera.services.bdd.suites.schedule.ScheduleDeleteSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleRecordSpecs;
import com.hedera.services.bdd.suites.token.TokenAssociationSpecs;
import com.hedera.services.bdd.suites.token.TokenCreateSpecs;
import com.hedera.services.bdd.suites.token.TokenDeleteSpecs;
import com.hedera.services.bdd.suites.token.TokenManagementSpecs;
import com.hedera.services.bdd.suites.token.TokenTransactSpecs;
import com.hedera.services.bdd.suites.token.TokenUpdateSpecs;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/** The set of BDD tests that we can execute in parallel. */
@Execution(ExecutionMode.CONCURRENT)
public class ParallelIntegrationTests extends IntegrationTestBase {

    @Tag("integration")
    @TestFactory
    Collection<DynamicContainer> parallel() {
        return List.of(
                extractSpecsFromSuite(AutoAccountCreationSuite::new),
                extractSpecsFromSuite(TokenAssociationSpecs::new),
                extractSpecsFromSuite(TokenCreateSpecs::new),
                extractSpecsFromSuite(TokenUpdateSpecs::new),
                extractSpecsFromSuite(TokenDeleteSpecs::new),
                extractSpecsFromSuite(TokenManagementSpecs::new),
                extractSpecsFromSuite(TokenTransactSpecs::new),
                extractSpecsFromSuite(FileCreateSuite::new),
                extractSpecsFromSuite(PermissionSemanticsSpec::new),
                extractSpecsFromSuite(SysDelSysUndelSpec::new),
                extractSpecsFromSuite(UpdateFailuresSpec::new),
                extractSpecsFromSuite(SignedTransactionBytesRecordsSuite::new),
                extractSpecsFromSuite(TopicCreateSuite::new),
                extractSpecsFromSuite(TopicDeleteSuite::new),
                extractSpecsFromSuite(SubmitMessageSuite::new),
                extractSpecsFromSuite(ChunkingSuite::new), // aka HCSTopicFragmentation
                extractSpecsFromSuite(CryptoTransferSuite::new),
                extractSpecsFromSuite(CannotDeleteSystemEntitiesSuite::new),
                extractSpecsFromSuite(ScheduleDeleteSpecs::new),
                extractSpecsFromSuite(ScheduleExecutionSpecs::new),
                extractSpecsFromSuite(ScheduleRecordSpecs::new),
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
                extractSpecsFromSuite(Create2OperationSuite::new),
                extractSpecsFromSuite(CreateOperationSuite::new),
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
                extractSpecsFromSuite(ApproveAllowanceSuite::new),
                extractSpecsFromSuite(AssociatePrecompileSuite::new),
                extractSpecsFromSuite(ContractBurnHTSSuite::new),
                extractSpecsFromSuite(ContractHTSSuite::new),
                extractSpecsFromSuite(ContractKeysHTSSuite::new),
                extractSpecsFromSuite(ContractMintHTSSuite::new),
                extractSpecsFromSuite(CreatePrecompileSuite::new),
                extractSpecsFromSuite(CryptoTransferHTSSuite::new),
                extractSpecsFromSuite(DefaultTokenStatusSuite::new),
                extractSpecsFromSuite(DelegatePrecompileSuite::new),
                extractSpecsFromSuite(DeleteTokenPrecompileSuite::new),
                extractSpecsFromSuite(DissociatePrecompileSuite::new),
                extractSpecsFromSuite(ERCPrecompileSuite::new),
                extractSpecsFromSuite(FreezeUnfreezeTokenPrecompileSuite::new),
                extractSpecsFromSuite(GrantRevokeKycSuite::new),
                extractSpecsFromSuite(MixedHTSPrecompileTestsSuite::new),
                extractSpecsFromSuite(PauseUnpauseTokenAccountPrecompileSuite::new),
                extractSpecsFromSuite(PrngPrecompileSuite::new),
                extractSpecsFromSuite(TokenAndTypeCheckSuite::new),
                extractSpecsFromSuite(TokenExpiryInfoSuite::new),
                extractSpecsFromSuite(TokenInfoHTSSuite::new),
                extractSpecsFromSuite(TokenUpdatePrecompileSuite::new),
                extractSpecsFromSuite(WipeTokenAccountPrecompileSuite::new),
                // contract.records
                extractSpecsFromSuite(LogsSuite::new),
                extractSpecsFromSuite(RecordsSuite::new),
                // contract.ethereum
                extractSpecsFromSuite(EthereumSuite::new),
                extractSpecsFromSuite(HelloWorldEthereumSuite::new));
    }
}
