import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.props.JutilPropertySource;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.autorenew.GracePeriodRestrictionsSuite;
import com.hedera.services.bdd.suites.consensus.ChunkingSuite;
import com.hedera.services.bdd.suites.consensus.SubmitMessageSuite;
import com.hedera.services.bdd.suites.consensus.TopicCreateSuite;
import com.hedera.services.bdd.suites.consensus.TopicDeleteSuite;
import com.hedera.services.bdd.suites.consensus.TopicGetInfoSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractCallLocalSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractDeleteSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractGetBytecodeSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractUpdateSuite;
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
import com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite;
import com.hedera.services.bdd.suites.crypto.AutoAccountUpdateSuite;
import com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite;
import com.hedera.services.bdd.suites.crypto.CryptoCreateSuite;
import com.hedera.services.bdd.suites.crypto.CryptoDeleteAllowanceSuite;
import com.hedera.services.bdd.suites.crypto.CryptoTransferSuite;
import com.hedera.services.bdd.suites.crypto.CryptoUpdateSuite;
import com.hedera.services.bdd.suites.fees.CongestionPricingSuite;
import com.hedera.services.bdd.suites.fees.SpecialAccountsAreExempted;
import com.hedera.services.bdd.suites.file.ExchangeRateControlSuite;
import com.hedera.services.bdd.suites.file.FetchSystemFiles;
import com.hedera.services.bdd.suites.file.FileAppendSuite;
import com.hedera.services.bdd.suites.file.FileCreateSuite;
import com.hedera.services.bdd.suites.file.FileUpdateSuite;
import com.hedera.services.bdd.suites.file.PermissionSemanticsSpec;
import com.hedera.services.bdd.suites.file.ProtectedFilesUpdateSuite;
import com.hedera.services.bdd.suites.file.negative.QueryFailuresSpec;
import com.hedera.services.bdd.suites.file.negative.UpdateFailuresSpec;
import com.hedera.services.bdd.suites.file.positive.SysDelSysUndelSpec;
import com.hedera.services.bdd.suites.meta.VersionInfoSpec;
import com.hedera.services.bdd.suites.misc.CannotDeleteSystemEntitiesSuite;
import com.hedera.services.bdd.suites.records.CharacterizationSuite;
import com.hedera.services.bdd.suites.records.ContractRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.CryptoRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.FileRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.RecordCreationSuite;
import com.hedera.services.bdd.suites.records.SignedTransactionBytesRecordsSuite;
import com.hedera.services.bdd.suites.regression.UmbrellaRedux;
import com.hedera.services.bdd.suites.schedule.ScheduleCreateSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleDeleteSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleRecordSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleSignSpecs;
import com.hedera.services.bdd.suites.throttling.PrivilegedOpsSuite;
import com.hedera.services.bdd.suites.throttling.ThrottleDefValidationSuite;
import com.hedera.services.bdd.suites.token.TokenAssociationSpecs;
import com.hedera.services.bdd.suites.token.TokenCreateSpecs;
import com.hedera.services.bdd.suites.token.TokenDeleteSpecs;
import com.hedera.services.bdd.suites.token.TokenManagementSpecs;
import com.hedera.services.bdd.suites.token.TokenPauseSpecs;
import com.hedera.services.bdd.suites.token.TokenTransactSpecs;
import com.hedera.services.bdd.suites.token.TokenUpdateSpecs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class IntegrationTests {

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

    @Tag("integration")
    @TestFactory
    Collection<DynamicContainer> integration() {
        return List.of(
                extractSpecsFromSuite(RecordCreationSuite::new),
                extractSpecsFromSuite(AutoAccountCreationSuite::new),
                extractSpecsFromSuite(AutoAccountUpdateSuite::new),
                extractSpecsFromSuite(GracePeriodRestrictionsSuite::new),
                extractSpecsFromSuite(CryptoApproveAllowanceSuite::new),
                extractSpecsFromSuite(CryptoDeleteAllowanceSuite::new),
                extractSpecsFromSuite(TokenAssociationSpecs::new),
                extractSpecsFromSuite(TokenCreateSpecs::new),
                extractSpecsFromSuite(TokenUpdateSpecs::new),
                extractSpecsFromSuite(TokenDeleteSpecs::new),
                extractSpecsFromSuite(TokenManagementSpecs::new),
                extractSpecsFromSuite(TokenTransactSpecs::new),
                extractSpecsFromSuite(TokenPauseSpecs::new),
                extractSpecsFromSuite(FileCreateSuite::new),
                extractSpecsFromSuite(FileAppendSuite::new),
                extractSpecsFromSuite(FileUpdateSuite::new),
                extractSpecsFromSuite(ProtectedFilesUpdateSuite::new),
                extractSpecsFromSuite(PermissionSemanticsSpec::new),
                extractSpecsFromSuite(ExchangeRateControlSuite::new),
                extractSpecsFromSuite(SysDelSysUndelSpec::new),
                extractSpecsFromSuite(UpdateFailuresSpec::new),
                extractSpecsFromSuite(QueryFailuresSpec::new),
                extractSpecsFromSuite(FileRecordsSanityCheckSuite::new),
                extractSpecsFromSuite(FetchSystemFiles::new),
                extractSpecsFromSuite(VersionInfoSpec::new),
//                extractSpecsFromSuite(NewOpInConstructorSpecs::new),
                extractSpecsFromSuite(ContractCallSuite::new),
                extractSpecsFromSuite(ContractCallLocalSuite::new),
                extractSpecsFromSuite(ContractUpdateSuite::new),
                extractSpecsFromSuite(ContractDeleteSuite::new),
//                extractSpecsFromSuite(ChildStorageSpecs::new),
//                extractSpecsFromSuite(BigArraySpec::new),
//                extractSpecsFromSuite(SmartContractInlineAssemblySpec::new),
//                extractSpecsFromSuite(OCTokenSpec::new),
                extractSpecsFromSuite(CharacterizationSuite::new),
//                extractSpecsFromSuite(SmartContractFailFirstSpec::new),
//                extractSpecsFromSuite(SmartContractSelfDestructSpec::new),
//                extractSpecsFromSuite(DeprecatedContractKeySpecs::new),
                extractSpecsFromSuite(ContractRecordsSanityCheckSuite::new),
                extractSpecsFromSuite(ContractGetBytecodeSuite::new),
                extractSpecsFromSuite(SignedTransactionBytesRecordsSuite::new),
                extractSpecsFromSuite(TopicCreateSuite::new),
//                extractSpecsFromSuite(TopicUpdateSuite::new),
                extractSpecsFromSuite(TopicDeleteSuite::new),
                extractSpecsFromSuite(SubmitMessageSuite::new),
                extractSpecsFromSuite(ChunkingSuite::new),
                extractSpecsFromSuite(TopicGetInfoSuite::new),
                extractSpecsFromSuite(SpecialAccountsAreExempted::new),
                extractSpecsFromSuite(CryptoTransferSuite::new),
                extractSpecsFromSuite(CryptoUpdateSuite::new),
                extractSpecsFromSuite(CryptoRecordsSanityCheckSuite::new),
                extractSpecsFromSuite(CannotDeleteSystemEntitiesSuite::new),
                extractSpecsFromSuite(ThrottleDefValidationSuite::new),
                extractSpecsFromSuite(PrivilegedOpsSuite::new),
                extractSpecsFromSuite(CongestionPricingSuite::new),
                extractSpecsFromSuite(CryptoCreateSuite::new),
                extractSpecsFromSuite(UmbrellaRedux::new),
                extractSpecsFromSuite(ScheduleDeleteSpecs::new),
                extractSpecsFromSuite(ScheduleExecutionSpecs::new),
                extractSpecsFromSuite(ScheduleCreateSpecs::new),
                extractSpecsFromSuite(ScheduleSignSpecs::new),
                extractSpecsFromSuite(ScheduleRecordSpecs::new),
                extractSpecsFromSuite(AssociatePrecompileSuite::new),
                extractSpecsFromSuite(ContractBurnHTSSuite::new),
                extractSpecsFromSuite(ContractHTSSuite::new),
                extractSpecsFromSuite(ContractKeysHTSSuite::new),
                extractSpecsFromSuite(ContractMintHTSSuite::new),
                extractSpecsFromSuite(CryptoTransferHTSSuite::new),
                extractSpecsFromSuite(DelegatePrecompileSuite::new),
                extractSpecsFromSuite(DissociatePrecompileSuite::new),
                extractSpecsFromSuite(DynamicGasCostSuite::new),
                extractSpecsFromSuite(MixedHTSPrecompileTestsSuite::new)
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

//    private DynamicContainer extractSpecsFromSuiteForEth(final Supplier<HapiApiSuite> suiteSupplier) {
//        final var suite = suiteSupplier.get();
//        final var tests = suite.getSpecsInSuite()
//                .stream()
//                .map(s -> dynamicTest(s.getName() + ETH_SUFFIX, () -> {
//                            s.setSuitePrefix(suite.getClass().getSimpleName() + ETH_SUFFIX);
//                            s.run();
//                            assertEquals(s.getExpectedFinalStatus(), s.getStatus(),
//                                    "\n\t\t\tFailure in SUITE {" + suite.getClass().getSimpleName() + ETH_SUFFIX + "}, " +
//                                            "while " +
//                                            "executing " +
//                                            "SPEC {" + s.getName() + ETH_SUFFIX + "}");
//                        }
//                ));
//        return dynamicContainer(suite.getClass().getSimpleName(), tests);
//    }
}