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
import com.hedera.services.bdd.suites.file.FileUpdateSuite;
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
                //                extractSpecsFromSuite(RecordCreationSuite::new),
                //                extractSpecsFromSuite(AutoAccountUpdateSuite::new),
                //                extractSpecsFromSuite(GracePeriodRestrictionsSuite::new),
                //                extractSpecsFromSuite(CryptoApproveAllowanceSuite::new),
                //                extractSpecsFromSuite(TokenPauseSpecs::new),
                //                extractSpecsFromSuite(FileAppendSuite::new),
                extractSpecsFromSuite(FileUpdateSuite::new)
                //                extractSpecsFromSuite(ProtectedFilesUpdateSuite::new),
                //                extractSpecsFromSuite(ExchangeRateControlSuite::new),
                //                extractSpecsFromSuite(FileRecordsSanityCheckSuite::new),
                //                extractSpecsFromSuite(FetchSystemFiles::new),
                //                extractSpecsFromSuite(VersionInfoSpec::new),
                //                extractSpecsFromSuite(ContractRecordsSanityCheckSuite::new),
                //                //                extractSpecsFromSuite(TopicUpdateSuite::new),
                //                extractSpecsFromSuite(TopicGetInfoSuite::new),
                //                extractSpecsFromSuite(SpecialAccountsAreExempted::new),
                //                extractSpecsFromSuite(CryptoUpdateSuite::new),
                //                extractSpecsFromSuite(CryptoRecordsSanityCheckSuite::new),
                //                extractSpecsFromSuite(ThrottleDefValidationSuite::new),
                //                extractSpecsFromSuite(PrivilegedOpsSuite::new),
                //                extractSpecsFromSuite(CongestionPricingSuite::new),
                //                extractSpecsFromSuite(CryptoCreateSuite::new),
                //                extractSpecsFromSuite(UmbrellaRedux::new),
                //                extractSpecsFromSuite(ScheduleCreateSpecs::new),
                //                extractSpecsFromSuite(ScheduleSignSpecs::new)
                );
    }
}
