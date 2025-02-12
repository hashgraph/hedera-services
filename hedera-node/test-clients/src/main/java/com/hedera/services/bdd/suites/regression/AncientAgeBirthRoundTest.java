package com.hedera.services.bdd.suites.regression;

import static com.hedera.services.bdd.junit.TestTags.ADHOC;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.suites.regression.system.MixedOperations.burstOfTps;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.suites.regression.system.LifecycleTest;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(ADHOC)
public class AncientAgeBirthRoundTest implements LifecycleTest {

    @HapiTest
    @DisplayName("Upgrade to enable birth round ancient age")
    final Stream<DynamicTest> upgradeToUseBirthRounds() {
        return hapiTest(
                burstOfTps(MIXED_OPS_BURST_TPS, MIXED_OPS_BURST_DURATION),
                upgradeToNextConfigVersion(Map.of("event.useBirthRoundAncientThreshold", "true")),
                burstOfTps(MIXED_OPS_BURST_TPS, MIXED_OPS_BURST_DURATION)
        );
    }
}
