// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.clpr.impl.test.calculator;

import static com.hedera.hapi.node.base.HederaFunctionality.CLPR_CLOSE_CHANNEL;
import static com.hedera.hapi.node.base.HederaFunctionality.CLPR_COMPLETE_CHANNEL;
import static com.hedera.hapi.node.base.HederaFunctionality.CLPR_COMPLETE_CONNECTOR;
import static com.hedera.hapi.node.base.HederaFunctionality.CLPR_DEREGISTER_CONNECTOR;
import static com.hedera.hapi.node.base.HederaFunctionality.CLPR_REGISTER_CHANNEL;
import static com.hedera.hapi.node.base.HederaFunctionality.CLPR_REGISTER_CONNECTOR;
import static com.hedera.hapi.node.base.HederaFunctionality.CLPR_SUBMIT_BUNDLE;
import static com.hedera.hapi.node.base.HederaFunctionality.CLPR_UPDATE_LEDGER_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.hapi.fees.FeeScheduleUtils.lookupServiceFee;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.hiero.hapi.support.fees.FeeSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the production CLPR fee values inside the genesis {@code simpleFeesSchedules.json}.
 *
 * <p>Goal: catch any silent drift in the per-op base fee or accidental re-introduction of
 * {@code nodeNetworkFeeExempt} on {@link HederaFunctionality#CLPR_SUBMIT_BUNDLE}. Every CLPR
 * transaction functionality MUST appear in the schedule with its pinned base fee. Channel
 * registration and completion cost $1, and connector operations 10¢, so that looping them (each
 * completion runs a verifier contract chosen by the payer) is expensive even when they fail.
 *
 * <p>Structural validity of the JSON is already covered by
 * {@code hedera-file-service-impl}'s {@code FeeScheduleTest.testLoadingFeeScheduleFromJson};
 * this test only asserts CLPR-specific values.
 */
class ClprGenesisFeeScheduleTest {

    /** 1/10th of a cent in tinycents (10^8 tinycents per cent ⇒ 10^7 per 0.1 cent). */
    private static final long TENTH_OF_A_CENT_TINYCENTS = 10_000_000L;

    /** 10 cents in tinycents. */
    private static final long TEN_CENTS_TINYCENTS = 1_000_000_000L;

    /** One dollar in tinycents. */
    private static final long ONE_DOLLAR_TINYCENTS = 10_000_000_000L;

    /**
     * Relative path from the {@code hedera-clpr-service-impl} module's working directory
     * (where Gradle runs tests) to the genesis fee schedule resource owned by
     * {@code hedera-file-service-impl}.
     */
    private static final Path GENESIS_SCHEDULE_PATH = Path.of(
            "..", "hedera-file-service-impl", "src", "main", "resources", "genesis", "simpleFeesSchedules.json");

    /** The testnet upgrade fee schedule, which must carry the same CLPR fees as genesis. */
    private static final Path TESTNET_UPGRADE_SCHEDULE_PATH =
            Path.of("..", "configuration", "testnet", "upgrade", "simpleFeesSchedules.json");

    /** Every CLPR transaction functionality and the base fee it MUST have. */
    private static final Map<HederaFunctionality, Long> CLPR_TRANSACTION_BASE_FEES = Map.of(
            CLPR_UPDATE_LEDGER_CONFIGURATION, TENTH_OF_A_CENT_TINYCENTS,
            CLPR_REGISTER_CHANNEL, ONE_DOLLAR_TINYCENTS,
            CLPR_COMPLETE_CHANNEL, ONE_DOLLAR_TINYCENTS,
            CLPR_CLOSE_CHANNEL, TENTH_OF_A_CENT_TINYCENTS,
            CLPR_REGISTER_CONNECTOR, TEN_CENTS_TINYCENTS,
            CLPR_COMPLETE_CONNECTOR, TEN_CENTS_TINYCENTS,
            CLPR_DEREGISTER_CONNECTOR, TEN_CENTS_TINYCENTS,
            CLPR_SUBMIT_BUNDLE, TENTH_OF_A_CENT_TINYCENTS);

    @Test
    @DisplayName("Every CLPR transaction op has its pinned base fee in the genesis schedule")
    void everyClprTransactionHasPinnedFee() throws Exception {
        assertPinnedFees(loadSchedule(GENESIS_SCHEDULE_PATH));
    }

    @Test
    @DisplayName("Every CLPR transaction op has its pinned base fee in the testnet upgrade schedule")
    void testnetUpgradeScheduleHasPinnedFees() throws Exception {
        assertPinnedFees(loadSchedule(TESTNET_UPGRADE_SCHEDULE_PATH));
    }

    private static void assertPinnedFees(final FeeSchedule schedule) {
        CLPR_TRANSACTION_BASE_FEES.forEach((functionality, baseFee) -> {
            final var serviceDef = lookupServiceFee(schedule, functionality);
            assertThat(serviceDef).as("schedule entry for %s", functionality).isNotNull();
            assertThat(serviceDef.baseFee()).as("baseFee for %s", functionality).isEqualTo(baseFee);
            assertThat(serviceDef.nodeNetworkFeeExempt())
                    .as("nodeNetworkFeeExempt for %s — endpoints MUST pay per spec §8.10", functionality)
                    .isFalse();
        });
    }

    private static FeeSchedule loadSchedule(final Path path) throws Exception {
        assertThat(path)
                .as("simpleFeesSchedules.json must exist at the expected sibling-module path")
                .exists();
        try (var in = Files.newInputStream(path)) {
            return FeeSchedule.JSON.parse(new ReadableStreamingData(in));
        }
    }
}
