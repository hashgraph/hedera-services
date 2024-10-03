package com.hedera.services.bdd.suites.hip991;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;

import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.approveTopicAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHtsFee;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;

@HapiTestLifecycle
@DisplayName("Topic Approve Allowance")
public class TopicApproveAllowanceTest extends TopicCustomFeeBase{

    @Nested
    @DisplayName("Positive scenarios")
    class ApproveAllowancePositiveScenarios {

        private static final String FUNGIBLE_TOKEN_1 = "fungibleToken1";
        private static final String FUNGIBLE_TOKEN_2 = "fungibleToken2";
        private static final String FEE_COLLECTOR = "feeCollector";

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle lifecycle) {
            lifecycle.doAdhoc(setupBaseKeys());
        }

        @HapiTest
        @DisplayName("Approve crypto allowance for topic")
        final Stream<DynamicTest> approveAllowance() {
            return hapiTest(
                    tokenCreate(FUNGIBLE_TOKEN_1),
                    tokenCreate(FUNGIBLE_TOKEN_2),
                    cryptoCreate(OWNER),
                    cryptoCreate(FEE_COLLECTOR),
                    createTopic(TOPIC)
                            .adminKeyName(ADMIN_KEY)
                            .submitKeyName(SUBMIT_KEY)
                            .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                            .withConsensusCustomFee(fixedConsensusHtsFee(5,FUNGIBLE_TOKEN_1, FEE_COLLECTOR)),
                    approveTopicAllowance()
                            .addTokenAllowance(OWNER, FUNGIBLE_TOKEN_1, TOPIC, 100, 10)
                            .payingWith(OWNER));
        }
    }
}
