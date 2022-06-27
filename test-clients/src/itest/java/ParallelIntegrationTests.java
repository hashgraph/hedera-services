import com.hedera.services.bdd.suites.consensus.ChunkingSuite;
import com.hedera.services.bdd.suites.consensus.SubmitMessageSuite;
import com.hedera.services.bdd.suites.consensus.TopicCreateSuite;
import com.hedera.services.bdd.suites.consensus.TopicDeleteSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractCallLocalSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractDeleteSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractGetBytecodeSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractUpdateSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractBurnHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractKeysHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractMintHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.CryptoTransferHTSSuite;
import com.hedera.services.bdd.suites.contract.precompile.DissociatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.precompile.MixedHTSPrecompileTestsSuite;
import com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite;
import com.hedera.services.bdd.suites.crypto.CryptoTransferSuite;
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
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Collection;
import java.util.List;

/**
 * The set of BDD tests that we can execute in parallel.
 */
@Execution(ExecutionMode.SAME_THREAD)
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
                extractSpecsFromSuite(ContractCallLocalSuite::new),
                extractSpecsFromSuite(ContractUpdateSuite::new),
                extractSpecsFromSuite(ContractDeleteSuite::new),
                extractSpecsFromSuite(ContractGetBytecodeSuite::new),
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
                extractSpecsFromSuite(ContractBurnHTSSuite::new),
                extractSpecsFromSuite(ContractHTSSuite::new),
                extractSpecsFromSuite(ContractKeysHTSSuite::new),
                extractSpecsFromSuite(ContractMintHTSSuite::new),
                extractSpecsFromSuite(CryptoTransferHTSSuite::new),
                extractSpecsFromSuite(DissociatePrecompileSuite::new),
                extractSpecsFromSuite(MixedHTSPrecompileTestsSuite::new)
        );
    }
}
