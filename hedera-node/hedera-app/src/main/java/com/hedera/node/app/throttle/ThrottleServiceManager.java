/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.throttle;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_CREATE;
import static com.hedera.node.app.records.BlockRecordService.EPOCH;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.node.app.throttle.CongestionThrottleService.CONGESTION_LEVEL_STARTS_STATE_KEY;
import static com.hedera.node.app.throttle.CongestionThrottleService.THROTTLE_USAGE_SNAPSHOTS_STATE_KEY;
import static com.hedera.node.app.util.FileUtilities.createFileID;
import static com.hedera.node.app.util.FileUtilities.getFileContent;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshot;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.congestion.CongestionMultipliers;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.annotations.BackendThrottle;
import com.hedera.node.app.throttle.annotations.IngestThrottle;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ThrottleServiceManager {
    private static final Logger log = LogManager.getLogger(ThrottleServiceManager.class);

    private final ConfigProvider configProvider;
    private final ThrottleParser throttleParser;
    private final ThrottleAccumulator ingestThrottle;
    private final ThrottleAccumulator backendThrottle;
    private final CongestionMultipliers congestionMultipliers;

    @Inject
    public ThrottleServiceManager(
            @NonNull final ConfigProvider configProvider,
            @NonNull final ThrottleParser throttleParser,
            @NonNull @IngestThrottle final ThrottleAccumulator ingestThrottle,
            @NonNull @BackendThrottle final ThrottleAccumulator backendThrottle,
            @NonNull final CongestionMultipliers congestionMultipliers) {
        this.throttleParser = throttleParser;
        this.ingestThrottle = requireNonNull(ingestThrottle);
        this.backendThrottle = requireNonNull(backendThrottle);
        this.configProvider = requireNonNull(configProvider);
        this.congestionMultipliers = requireNonNull(congestionMultipliers);
    }

    /**
     * Completely rebuilds the throttling service from the given state.
     *
     * <p><b>IMPORTANT</b> - In the current system, the initialization
     * order below (first apply configuration, second rebuild throttles,
     * third reset multiplier expectations, final rehydrate internal state)
     * is actually necessary to avoid NPEs and other issues.
     *
     * @param state the state to use
     */
    public void initFrom(@NonNull final HederaState state) {
        requireNonNull(state);
        // Apply configuration for gas throttles
        applyGasConfig();
        // Create backend/frontend throttles from the configured system file
        rebuildFromThrottleDefinitions(state);
        // Reset multiplier expectations
        congestionMultipliers.resetExpectations();
        // Rehydrate the internal state of the throttling service (if not at genesis)
        final var serviceStates = state.getReadableStates(CongestionThrottleService.NAME);
        resetThrottlesFromUsageSnapshots(serviceStates);
        syncFromCongestionLevelStarts(serviceStates);
    }

    /**
     * Saves the current state of the throttles and congestion level starts
     * to the given state.
     *
     * @param state the state to save to
     */
    public void saveThrottleSnapshotsAndCongestionLevelStartsTo(@NonNull final HederaState state) {
        requireNonNull(state);
        final var serviceStates = state.getWritableStates(CongestionThrottleService.NAME);
        saveThrottleSnapshotsTo(serviceStates);
        saveCongestionLevelStartsTo(serviceStates);
    }

    /**
     * Refreshes the parts of the throttle service that depend on the
     * current network configuration.
     */
    public void refreshThrottleConfiguration() {
        applyGasConfig();
        congestionMultipliers.resetExpectations();
    }

    /**
     * Recreates the throttles based on the given throttle definitions.
     *
     * @param encoded the serialized throttle definitions
     * @return the success status to use if the update was via HAPI
     */
    public ResponseCodeEnum recreateThrottles(@NonNull final Bytes encoded) {
        final var validatedThrottles = rebuildThrottlesFrom(encoded);
        congestionMultipliers.resetExpectations();
        return validatedThrottles.successStatus();
    }

    public int numImplicitCreations(
            @NonNull final TransactionBody body, @NonNull final ReadableAccountStore accountStore) {
        return backendThrottle.getImplicitCreationsCount(body, accountStore);
    }

    /**
     * Updates all metrics for the throttles.
     */
    public void updateAllMetrics() {
        ingestThrottle.updateAllMetrics();
        backendThrottle.updateAllMetrics();
    }

    private void saveThrottleSnapshotsTo(@NonNull final WritableStates serviceStates) {
        final var hapiThrottles = backendThrottle.allActiveThrottles();
        final List<ThrottleUsageSnapshot> hapiThrottleSnapshots;
        if (hapiThrottles.isEmpty()) {
            hapiThrottleSnapshots = emptyList();
        } else {
            hapiThrottleSnapshots = new ArrayList<>();
            for (final var throttle : hapiThrottles) {
                hapiThrottleSnapshots.add(toPbj(throttle.usageSnapshot()));
            }
        }

        final var gasThrottle = backendThrottle.gasLimitThrottle();
        final var gasThrottleSnapshot = toPbj(gasThrottle.usageSnapshot());

        final WritableSingletonState<ThrottleUsageSnapshots> throttleSnapshots =
                serviceStates.getSingleton(THROTTLE_USAGE_SNAPSHOTS_STATE_KEY);
        throttleSnapshots.put(new ThrottleUsageSnapshots(hapiThrottleSnapshots, gasThrottleSnapshot));
    }

    private void saveCongestionLevelStartsTo(@NonNull final WritableStates serviceStates) {
        final WritableSingletonState<CongestionLevelStarts> congestionLevelStarts =
                serviceStates.getSingleton(CONGESTION_LEVEL_STARTS_STATE_KEY);
        congestionLevelStarts.put(new CongestionLevelStarts(
                translateToList(congestionMultipliers.entityUtilizationCongestionStarts()),
                translateToList(congestionMultipliers.gasThrottleMultiplierCongestionStarts())));
    }

    private void rebuildFromThrottleDefinitions(HederaState state) {
        final var config = configProvider.getConfiguration();
        final var filesConfig = config.getConfigData(FilesConfig.class);
        final var throttleDefinitionsId = createFileID(filesConfig.throttleDefinitions(), config);
        rebuildThrottlesFrom(getFileContent(state, throttleDefinitionsId));
    }

    private @NonNull ThrottleParser.ValidatedThrottles rebuildThrottlesFrom(@NonNull Bytes encoded) {
        final var validatedThrottles = throttleParser.parse(encoded);
        ingestThrottle.rebuildFor(validatedThrottles.throttleDefinitions());
        backendThrottle.rebuildFor(validatedThrottles.throttleDefinitions());
        return validatedThrottles;
    }

    private void applyGasConfig() {
        ingestThrottle.applyGasConfig();
        backendThrottle.applyGasConfig();
    }

    private void syncFromCongestionLevelStarts(@NonNull final ReadableStates serviceStates) {
        final var congestionStarts =
                CongestionStarts.from(serviceStates.getSingleton(CONGESTION_LEVEL_STARTS_STATE_KEY));
        // No matter if the congestion level starts are empty in state because
        // we're at genesis; or because that's the actual configuration, there's
        // nothing to do here.
        if (congestionStarts.cryptoTransferLevelStarts().length > 0) {
            congestionMultipliers.resetUtilizationScaledThrottleMultiplierStarts(
                    congestionStarts.cryptoTransferLevelStarts());
        }
        if (congestionStarts.gasLevelStarts().length > 0) {
            congestionMultipliers.resetGasThrottleMultiplierStarts(congestionStarts.gasLevelStarts());
        }
    }

    private void resetThrottlesFromUsageSnapshots(@NonNull final ReadableStates serviceStates) {
        final var usageSnapshots = UsageSnapshots.from(serviceStates.getSingleton(THROTTLE_USAGE_SNAPSHOTS_STATE_KEY));
        safeResetThrottles(backendThrottle.allActiveThrottles(), usageSnapshots.hapiUsageSnapshots());
        if (usageSnapshots.gasUsageSnapshot() != null) {
            backendThrottle.gasLimitThrottle().resetUsageTo(usageSnapshots.gasUsageSnapshot());
        }
    }

    /**
     * Reclaims the capacity used for throttling the given number of implicit creations
     * on the frontend.
     *
     * @param numImplicitCreations the number of implicit creations
     */
    public void reclaimFrontendThrottleCapacity(final int numImplicitCreations) {
        try {
            ingestThrottle.leakCapacityForNOfUnscaled(numImplicitCreations, CRYPTO_CREATE);
        } catch (Exception ignore) {
            // Ignore if the frontend bucket has already leaked all the capacity
            // used for throttling the transaction on the frontend
        }
    }

    // override hashCode()/equals() for array fields
    @SuppressWarnings("java:S6218")
    private record CongestionStarts(Instant[] cryptoTransferLevelStarts, Instant[] gasLevelStarts) {
        static CongestionStarts from(
                @NonNull final ReadableSingletonState<CongestionLevelStarts> congestionLevelStarts) {
            final var sourceStarts = requireNonNull(congestionLevelStarts.get());
            return new CongestionStarts(
                    asMultiplierStarts(sourceStarts.genericLevelStartsOrElse(emptyList())),
                    asMultiplierStarts(sourceStarts.gasLevelStartsOrElse(emptyList())));
        }
    }

    private record UsageSnapshots(
            List<DeterministicThrottle.UsageSnapshot> hapiUsageSnapshots,
            @Nullable DeterministicThrottle.UsageSnapshot gasUsageSnapshot) {
        static UsageSnapshots from(
                @NonNull final ReadableSingletonState<ThrottleUsageSnapshots> throttleUsageSnapshots) {
            final var sourceSnapshots = requireNonNull(throttleUsageSnapshots.get());
            return new UsageSnapshots(
                    sourceSnapshots.tpsThrottlesOrElse(emptyList()).stream()
                            .map(PbjConverter::fromPbj)
                            .toList(),
                    Optional.ofNullable(sourceSnapshots.gasThrottle())
                            .map(PbjConverter::fromPbj)
                            .orElse(null));
        }
    }

    private static @NonNull List<Timestamp> translateToList(@NonNull final Instant[] levelStartTimes) {
        final List<Timestamp> list = new ArrayList<>(levelStartTimes.length);
        for (final var startTime : levelStartTimes) {
            list.add(startTime == null ? EPOCH : new Timestamp(startTime.getEpochSecond(), startTime.getNano()));
        }
        return list;
    }

    private static void safeResetThrottles(
            final List<DeterministicThrottle> throttles, final List<DeterministicThrottle.UsageSnapshot> snapshots) {
        // No-op if we don't have a snapshot for every throttle
        if (throttles.size() != snapshots.size()) {
            return;
        }
        final var currentSnapshots =
                throttles.stream().map(DeterministicThrottle::usageSnapshot).toList();
        for (int i = 0, n = throttles.size(); i < n; i++) {
            try {
                throttles.get(i).resetUsageTo(snapshots.get(i));
            } catch (final Exception e) {
                log.warn(
                        "Saved usage snapshot @ index {} was not compatible with the corresponding"
                                + " active throttle ({}), not performing a reset !",
                        i,
                        e.getMessage());
                resetUnconditionally(throttles, currentSnapshots);
                break;
            }
        }
    }

    private static void resetUnconditionally(
            final List<DeterministicThrottle> throttles,
            final List<DeterministicThrottle.UsageSnapshot> knownCompatible) {
        for (int i = 0, n = knownCompatible.size(); i < n; i++) {
            throttles.get(i).resetUsageTo(knownCompatible.get(i));
        }
    }

    private static @NonNull Instant[] asMultiplierStarts(@NonNull final List<Timestamp> times) {
        final var n = times.size();
        final var starts = new Instant[n];
        for (int i = 0; i < n; i++) {
            final var time = times.get(i);
            // ThrottleMultiplier implementations use null to represent a congestion
            // level that has not even started at the current consensus time, but we
            // use the UTC epoch to represent the same thing in state
            if (!EPOCH.equals(time)) {
                starts[i] = Instant.ofEpochSecond(time.seconds(), time.nanos());
            }
        }
        return starts;
    }
}
