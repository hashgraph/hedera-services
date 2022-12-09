import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.autorenew.GracePeriodRestrictionsSuite;
import com.hedera.services.bdd.suites.consensus.TopicGetInfoSuite;
import com.hedera.services.bdd.suites.contract.opcodes.Create2OperationSuite;
import com.hedera.services.bdd.suites.contract.opcodes.SelfDestructSuite;
import com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite;
import com.hedera.services.bdd.suites.contract.traceability.TraceabilitySuite;
import com.hedera.services.bdd.suites.crypto.AutoAccountUpdateSuite;
import com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite;
import com.hedera.services.bdd.suites.crypto.CryptoCreateSuite;
import com.hedera.services.bdd.suites.crypto.CryptoUpdateSuite;
import com.hedera.services.bdd.suites.fees.CongestionPricingSuite;
import com.hedera.services.bdd.suites.fees.SpecialAccountsAreExempted;
import com.hedera.services.bdd.suites.file.ExchangeRateControlSuite;
import com.hedera.services.bdd.suites.file.FetchSystemFiles;
import com.hedera.services.bdd.suites.file.FileAppendSuite;
import com.hedera.services.bdd.suites.file.FileUpdateSuite;
import com.hedera.services.bdd.suites.file.ProtectedFilesUpdateSuite;
import com.hedera.services.bdd.suites.leaky.FeatureFlagSuite;
import com.hedera.services.bdd.suites.meta.VersionInfoSpec;
import com.hedera.services.bdd.suites.records.ContractRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.CryptoRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.FileRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.RecordCreationSuite;
import com.hedera.services.bdd.suites.regression.UmbrellaRedux;
import com.hedera.services.bdd.suites.schedule.ScheduleCreateSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleDeleteSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleRecordSpecs;
import com.hedera.services.bdd.suites.schedule.ScheduleSignSpecs;
import com.hedera.services.bdd.suites.throttling.PrivilegedOpsSuite;
import com.hedera.services.bdd.suites.throttling.ThrottleDefValidationSuite;
import com.hedera.services.bdd.suites.token.TokenPauseSpecs;

import java.util.function.Supplier;

public class SequentialSuites {
    @SuppressWarnings("unchecked")
    static Supplier<HapiSuite>[] all() {
        return (Supplier<HapiSuite>[]) new Supplier[]{
                RecordCreationSuite::new,
                AutoAccountUpdateSuite::new,
                GracePeriodRestrictionsSuite::new,
                CryptoApproveAllowanceSuite::new,
                TokenPauseSpecs::new,
                FileAppendSuite::new,
                FileUpdateSuite::new,
                ProtectedFilesUpdateSuite::new,
                ExchangeRateControlSuite::new,
                FileRecordsSanityCheckSuite::new,
                FetchSystemFiles::new,
                VersionInfoSpec::new,
                ContractRecordsSanityCheckSuite::new,
                TopicGetInfoSuite::new,
                SpecialAccountsAreExempted::new,
                CryptoUpdateSuite::new,
                CryptoRecordsSanityCheckSuite::new,
                ThrottleDefValidationSuite::new,
                PrivilegedOpsSuite::new,
                CongestionPricingSuite::new,
                CryptoCreateSuite::new,
                UmbrellaRedux::new,
                ScheduleCreateSpecs::new,
                ScheduleSignSpecs::new,
                TraceabilitySuite::new,
                ScheduleRecordSpecs::new,
                ScheduleExecutionSpecs::new,
                ScheduleDeleteSpecs::new,
                Create2OperationSuite::new,
                SelfDestructSuite::new,
                CreatePrecompileSuite::new,
                FeatureFlagSuite::new
        };
    }
}
