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
                //                extractSpecsFromSuite(AutoAccountCreationSuite::new),
                //                extractSpecsFromSuite(TokenAssociationSpecs::new),
                //                extractSpecsFromSuite(TokenCreateSpecs::new),
                //                extractSpecsFromSuite(TokenUpdateSpecs::new),
                //                extractSpecsFromSuite(TokenDeleteSpecs::new),
                //                extractSpecsFromSuite(TokenManagementSpecs::new),
                //                extractSpecsFromSuite(TokenTransactSpecs::new),
                //                extractSpecsFromSuite(FileCreateSuite::new),
                //                extractSpecsFromSuite(PermissionSemanticsSpec::new),
                //                extractSpecsFromSuite(SysDelSysUndelSpec::new),
                //                extractSpecsFromSuite(UpdateFailuresSpec::new),
                //                extractSpecsFromSuite(ContractCallLocalSuite::new),
                //                extractSpecsFromSuite(ContractUpdateSuite::new),
                //                extractSpecsFromSuite(ContractDeleteSuite::new),
                //                extractSpecsFromSuite(ContractGetBytecodeSuite::new),
                //                extractSpecsFromSuite(SignedTransactionBytesRecordsSuite::new),
                //                extractSpecsFromSuite(TopicCreateSuite::new),
                //                extractSpecsFromSuite(TopicDeleteSuite::new),
                //                extractSpecsFromSuite(SubmitMessageSuite::new),
                //                extractSpecsFromSuite(ChunkingSuite::new), // aka
                // HCSTopicFragmentation
                //                extractSpecsFromSuite(CryptoTransferSuite::new),
                //                extractSpecsFromSuite(CannotDeleteSystemEntitiesSuite::new),
                //                extractSpecsFromSuite(ScheduleDeleteSpecs::new),
                //                extractSpecsFromSuite(ScheduleExecutionSpecs::new),
                //                extractSpecsFromSuite(ScheduleRecordSpecs::new),
                //                extractSpecsFromSuite(ContractBurnHTSSuite::new),
                //                extractSpecsFromSuite(ContractHTSSuite::new),
                //                extractSpecsFromSuite(ContractKeysHTSSuite::new),
                //                extractSpecsFromSuite(ContractMintHTSSuite::new),
                //                extractSpecsFromSuite(CryptoTransferHTSSuite::new),
                //                extractSpecsFromSuite(DissociatePrecompileSuite::new),
                //                extractSpecsFromSuite(MixedHTSPrecompileTestsSuite::new),
                //                // contract.hapi
                //                extractSpecsFromSuite(ContractCallSuite::new),
                //                extractSpecsFromSuite(ContractCreateSuite::new),
                //                extractSpecsFromSuite(ContractGetInfoSuite::new),
                //                extractSpecsFromSuite(ContractMusicalChairsSuite::new),
                //                // contract.opcode
                //                extractSpecsFromSuite(BalanceOperationSuite::new),
                //                extractSpecsFromSuite(CallCodeOperationSuite::new),
                //                extractSpecsFromSuite(CallOperationSuite::new),
                //                extractSpecsFromSuite(CreateOperationSuite::new),
                //                extractSpecsFromSuite(Create2OperationSuite::new),
                //                extractSpecsFromSuite(DelegateCallOperationSuite::new),
                //                extractSpecsFromSuite(ExtCodeCopyOperationSuite::new),
                //                extractSpecsFromSuite(ExtCodeHashOperationSuite::new),
                //                extractSpecsFromSuite(ExtCodeSizeOperationSuite::new),
                //                extractSpecsFromSuite(GlobalPropertiesSuite::new),
                //                extractSpecsFromSuite(SelfDestructSuite::new),
                //                extractSpecsFromSuite(SStoreSuite::new),
                //                extractSpecsFromSuite(StaticCallOperationSuite::new),
                //                // contract.openzeppelin
                //                extractSpecsFromSuite(ERC20ContractInteractions::new),
                //                extractSpecsFromSuite(ERC721ContractInteractions::new),
                //                extractSpecsFromSuite(ERC1155ContractInteractions::new),
                //                // contract.precompile
                //                extractSpecsFromSuite(AssociatePrecompileSuite::new),
                //                extractSpecsFromSuite(CreatePrecompileSuite::new),
                //                extractSpecsFromSuite(DelegatePrecompileSuite::new),
                //                extractSpecsFromSuite(ERCPrecompileSuite::new),
                //                extractSpecsFromSuite(ApproveAllowanceSuite::new),
                //                extractSpecsFromSuite(DefaultTokenStatusSuite::new),
                //                extractSpecsFromSuite(DeleteTokenPrecompileSuite::new),
                //                extractSpecsFromSuite(FreezeUnfreezeTokenPrecompileSuite::new),
                //                extractSpecsFromSuite(GrantRevokeKycSuite::new),
                //
                // extractSpecsFromSuite(PauseUnpauseTokenAccountPrecompileSuite::new),
                //                extractSpecsFromSuite(PrngPrecompileSuite::new),
                //                extractSpecsFromSuite(TokenInfoHTSSuite::new),
                //                extractSpecsFromSuite(WipeTokenAccountPrecompileSuite::new),
                //                // contract.records
                //                extractSpecsFromSuite(LogsSuite::new),
                //                extractSpecsFromSuite(RecordsSuite::new),
                //                // contract.ethereum
                //                extractSpecsFromSuite(EthereumSuite::new),
                //                extractSpecsFromSuite(HelloWorldEthereumSuite::new)
                );
    }
}
