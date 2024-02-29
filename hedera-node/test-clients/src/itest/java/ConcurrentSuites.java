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

import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.consensus.ChunkingSuite;
import com.hedera.services.bdd.suites.consensus.SubmitMessageSuite;
import com.hedera.services.bdd.suites.consensus.TopicCreateSuite;
import com.hedera.services.bdd.suites.consensus.TopicDeleteSuite;
import com.hedera.services.bdd.suites.consensus.TopicGetInfoSuite;
import com.hedera.services.bdd.suites.consensus.TopicUpdateSuite;
import com.hedera.services.bdd.suites.contract.evm.Evm46ValidationSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractCallLocalSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractCreateSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractDeleteSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractGetBytecodeSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractGetInfoSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractMusicalChairsSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractUpdateSuite;
import com.hedera.services.bdd.suites.contract.opcodes.CreateOperationSuite;
import com.hedera.services.bdd.suites.contract.opcodes.GlobalPropertiesSuite;
import com.hedera.services.bdd.suites.contract.opcodes.SStoreSuite;
import com.hedera.services.bdd.suites.contract.opcodes.SelfDestructSuite;
import com.hedera.services.bdd.suites.contract.openzeppelin.ERC1155ContractInteractions;
import com.hedera.services.bdd.suites.contract.openzeppelin.ERC20ContractInteractions;
import com.hedera.services.bdd.suites.contract.openzeppelin.ERC721ContractInteractions;
import com.hedera.services.bdd.suites.contract.precompile.ApproveAllowanceSuite;
import com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileV2SecurityModelSuite;
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
import com.hedera.services.bdd.suites.contract.precompile.LazyCreateThroughPrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.PauseUnpauseTokenAccountPrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.PrngPrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.SigningReqsSuite;
import com.hedera.services.bdd.suites.contract.precompile.TokenAndTypeCheckSuite;
import com.hedera.services.bdd.suites.contract.precompile.TokenExpiryInfoSuite;
import com.hedera.services.bdd.suites.contract.precompile.TokenInfoHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.TokenUpdatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.WipeTokenAccountPrecompileSuite;
import com.hedera.services.bdd.suites.contract.records.LogsSuite;
import com.hedera.services.bdd.suites.contract.records.RecordsSuite;
import com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite;
import com.hedera.services.bdd.suites.crypto.AutoAccountUpdateSuite;
import com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite;
import com.hedera.services.bdd.suites.crypto.CryptoCreateSuite;
import com.hedera.services.bdd.suites.crypto.CryptoTransferSuite;
import com.hedera.services.bdd.suites.crypto.CryptoUpdateSuite;
import com.hedera.services.bdd.suites.crypto.HollowAccountFinalizationSuite;
import com.hedera.services.bdd.suites.ethereum.EthereumSuite;
import com.hedera.services.bdd.suites.ethereum.HelloWorldEthereumSuite;
import com.hedera.services.bdd.suites.ethereum.NonceSuite;
import com.hedera.services.bdd.suites.file.FileAppendSuite;
import com.hedera.services.bdd.suites.file.FileCreateSuite;
import com.hedera.services.bdd.suites.file.PermissionSemanticsSpec;
import com.hedera.services.bdd.suites.file.negative.QueryFailuresSpec;
import com.hedera.services.bdd.suites.file.negative.UpdateFailuresSpec;
import com.hedera.services.bdd.suites.file.positive.SysDelSysUndelSpec;
import com.hedera.services.bdd.suites.meta.VersionInfoSpec;
import com.hedera.services.bdd.suites.records.SignedTransactionBytesRecordsSuite;
import com.hedera.services.bdd.suites.token.TokenAssociationSpecs;
import com.hedera.services.bdd.suites.token.TokenCreateSpecs;
import com.hedera.services.bdd.suites.token.TokenDeleteSpecs;
import com.hedera.services.bdd.suites.token.TokenManagementSpecs;
import com.hedera.services.bdd.suites.token.TokenPauseSpecs;
import com.hedera.services.bdd.suites.token.TokenTransactSpecs;
import com.hedera.services.bdd.suites.token.TokenUpdateSpecs;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** The set of BDD tests that we can execute in parallel. */
public class ConcurrentSuites {
    @SuppressWarnings("unchecked")
    static Supplier<HapiSuite>[] all() {
        return (Supplier<HapiSuite>[]) new Supplier[] {
            CryptoCreateSuite::new,
            AutoAccountUpdateSuite::new,
            CryptoApproveAllowanceSuite::new,
            TokenPauseSpecs::new,
            FileAppendSuite::new,
            TopicGetInfoSuite::new,
            AutoAccountCreationSuite::new,
            HollowAccountFinalizationSuite::new,
            TokenAssociationSpecs::new,
            TokenCreateSpecs::new,
            TokenUpdateSpecs::new,
            TokenDeleteSpecs::new,
            TokenManagementSpecs::new,
            TokenTransactSpecs::new,
            FileCreateSuite::new,
            QueryFailuresSpec::new,
            PermissionSemanticsSpec::new,
            SysDelSysUndelSpec::new,
            UpdateFailuresSpec::new,
            SignedTransactionBytesRecordsSuite::new,
            TopicCreateSuite::new,
            TopicDeleteSuite::new,
            TopicUpdateSuite::new,
            SubmitMessageSuite::new,
            ChunkingSuite::new,
            CryptoTransferSuite::new,
            CryptoUpdateSuite::new,
            SelfDestructSuite::new,
            // contract.hapi
            ContractCallLocalSuite::new,
            ContractCallSuite::new,
            ContractCreateSuite::new,
            ContractDeleteSuite::new,
            ContractGetBytecodeSuite::new,
            ContractGetInfoSuite::new,
            ContractMusicalChairsSuite::new,
            ContractUpdateSuite::new,
            // contract.opcode
            CreateOperationSuite::new,
            GlobalPropertiesSuite::new,
            SStoreSuite::new,
            // contract.openzeppelin
            ERC20ContractInteractions::new,
            ERC721ContractInteractions::new,
            ERC1155ContractInteractions::new,
            // contract.precompile
            SigningReqsSuite::new,
            ApproveAllowanceSuite::new,
            AssociatePrecompileSuite::new,
            ContractBurnHTSSuite::new,
            ContractHTSSuite::new,
            ContractKeysHTSSuite::new,
            ContractMintHTSSuite::new,
            CryptoTransferHTSSuite::new,
            DefaultTokenStatusSuite::new,
            DelegatePrecompileSuite::new,
            DeleteTokenPrecompileSuite::new,
            DissociatePrecompileSuite::new,
            CreatePrecompileSuite::new,
            ERCPrecompileSuite::new,
            FreezeUnfreezeTokenPrecompileSuite::new,
            GrantRevokeKycSuite::new,
            LazyCreateThroughPrecompileSuite::new,
            PauseUnpauseTokenAccountPrecompileSuite::new,
            PrngPrecompileSuite::new,
            TokenAndTypeCheckSuite::new,
            TokenExpiryInfoSuite::new,
            TokenInfoHTSSuite::new,
            TokenUpdatePrecompileSuite::new,
            WipeTokenAccountPrecompileSuite::new,
            AssociatePrecompileV2SecurityModelSuite::new,
            // contract.records
            LogsSuite::new,
            RecordsSuite::new,
            // contract.ethereum
            EthereumSuite::new,
            HelloWorldEthereumSuite::new,
            // network info
            VersionInfoSpec::new,
            Evm46ValidationSuite::new,
            NonceSuite::new
        };
    }

    /**
     * Wrap a suite supplier with a call to set the auto-scheduling override for the given functions.
     *
     * @param suiteSupplier the suite supplier to wrap
     * @param functions the functions to auto-schedule
     * @return the wrapped suite supplier
     */
    private static Supplier<HapiSuite> withAutoScheduling(
            final Supplier<HapiSuite> suiteSupplier, final Set<HederaFunctionality> functions) {
        return () -> {
            final var suite = suiteSupplier.get();
            final var commaSeparated = functions.stream().map(Enum::toString).collect(Collectors.joining(","));
            suite.setOverrides(Map.of("spec.autoScheduledTxns", commaSeparated));
            return suite;
        };
    }

    /*
       All the EVM suites that should be executed with Ethereum calls to verify
       Ethereum compatibility.
    */
    @SuppressWarnings("unchecked")
    static Supplier<HapiSuite>[] ethereumSuites() {
        return (Supplier<HapiSuite>[]) new Supplier[] {
            // contract.precompile
            SigningReqsSuite::new,
            ApproveAllowanceSuite::new,
            AssociatePrecompileSuite::new,
            ContractBurnHTSSuite::new,
            ContractHTSSuite::new,
            ContractKeysHTSSuite::new,
            ContractMintHTSSuite::new,
            CryptoTransferHTSSuite::new,
            DefaultTokenStatusSuite::new,
            DelegatePrecompileSuite::new,
            DeleteTokenPrecompileSuite::new,
            CreatePrecompileSuite::new,
            ERCPrecompileSuite::new,
            FreezeUnfreezeTokenPrecompileSuite::new,
            GrantRevokeKycSuite::new,
            LazyCreateThroughPrecompileSuite::new,
            PauseUnpauseTokenAccountPrecompileSuite::new,
            PrngPrecompileSuite::new,
            TokenAndTypeCheckSuite::new,
            TokenExpiryInfoSuite::new,
            TokenInfoHTSSuite::new,
            TokenUpdatePrecompileSuite::new,
            WipeTokenAccountPrecompileSuite::new,
            // contract opcodes
            CreateOperationSuite::new,
            GlobalPropertiesSuite::new,
            SelfDestructSuite::new,
            SStoreSuite::new,
            // contract.hapi
            ContractCallLocalSuite::new,
            ContractCallSuite::new,
            ContractCreateSuite::new,
            ContractDeleteSuite::new,
            ContractGetBytecodeSuite::new,
            ContractGetInfoSuite::new,
            ContractMusicalChairsSuite::new,
            ContractUpdateSuite::new,
            // contracts.openZeppelin
            ERC20ContractInteractions::new,
            ERC721ContractInteractions::new,
            ERC1155ContractInteractions::new,
            //  contract.records
            RecordsSuite::new,
            LogsSuite::new,
            Evm46ValidationSuite::new
        };
    }
}
