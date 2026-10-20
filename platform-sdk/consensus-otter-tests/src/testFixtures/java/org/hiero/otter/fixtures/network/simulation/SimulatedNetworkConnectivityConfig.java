// SPDX-License-Identifier: Apache-2.0
package org.hiero.otter.fixtures.network.simulation;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;

/**
 * Tunable parameters of {@link SimulatedNetworkConnectivity}. Use {@link #DEFAULT} for the defaults, or one of the
 * {@code with...} methods to override single values.
 *
 * @param initialSequenceNumberCapacity the initial capacity of the sequence number space of the cursored log of events.
 *                                      This should match the number of non-expired rounds according to the default
 *                                      configuration of the nodes, but it does not have to be exact because the log
 *                                      expands as needed
 * @param initialEventLogCapacity       the initial capacity of the event log. It does not have to be exact because the
 *                                      log expands as needed. Must be a power of 2
 * @param reconnectRoundTrips           how many round trips of a link pass between it coming up and anything being
 *                                      delivered over it. This stands in for the TCP and TLS handshakes, protocol
 *                                      negotiation and first sync
 * @param reconnectJitter               the fraction by which the reconnect delay is randomly varied in either
 *                                      direction, so that links which come up together do not all become usable at the
 *                                      same moment
 * @param disableBroadcastRoundTrip     a link whose round trip exceeds this leaves broadcast mode (production:
 *                                      {@code disablePingThreshold})
 * @param broadcastPauseOnLag           how long a link stays out of broadcast mode after its round trip falls below the
 *                                      threshold (production: {@code pauseOnLag})
 * @param syncHoldBackInBroadcastMode   how long a forwarded copy is held back by the sender in broadcast mode
 *                                      (production: {@code ancestorFilterThreshold})
 * @param syncPauseInBroadcastMode      the pause between two consecutive syncs of a pair of nodes, in broadcast mode
 * @param syncPauseWithoutBroadcast     the pause between two consecutive syncs of a pair of nodes, when broadcast is
 *                                      off
 */
public record SimulatedNetworkConnectivityConfig(
        int initialSequenceNumberCapacity,
        int initialEventLogCapacity,
        double reconnectRoundTrips,
        double reconnectJitter,
        @NonNull Duration disableBroadcastRoundTrip,
        @NonNull Duration broadcastPauseOnLag,
        @NonNull Duration syncHoldBackInBroadcastMode,
        @NonNull Duration syncPauseInBroadcastMode,
        @NonNull Duration syncPauseWithoutBroadcast) {

    /** The configuration used when none is given. */
    public static final SimulatedNetworkConnectivityConfig DEFAULT = new SimulatedNetworkConnectivityConfig(
            1000,
            (int) Math.pow(2, 8),
            5.0,
            0.2,
            Duration.ofMillis(900),
            Duration.ofSeconds(30),
            Duration.ofMillis(250),
            Duration.ofMillis(100),
            Duration.ofMillis(5));

    /**
     * Constructor.
     */
    public SimulatedNetworkConnectivityConfig {
        requireNonNull(disableBroadcastRoundTrip);
        requireNonNull(broadcastPauseOnLag);
        requireNonNull(syncHoldBackInBroadcastMode);
        requireNonNull(syncPauseInBroadcastMode);
        requireNonNull(syncPauseWithoutBroadcast);
    }

    /**
     * @param value the new initial capacity of the sequence number space
     * @return a copy with the value replaced
     */
    @NonNull
    public SimulatedNetworkConnectivityConfig withInitialSequenceNumberCapacity(final int value) {
        return new SimulatedNetworkConnectivityConfig(
                value,
                initialEventLogCapacity,
                reconnectRoundTrips,
                reconnectJitter,
                disableBroadcastRoundTrip,
                broadcastPauseOnLag,
                syncHoldBackInBroadcastMode,
                syncPauseInBroadcastMode,
                syncPauseWithoutBroadcast);
    }

    /**
     * @param value the new initial capacity of the event log, a power of 2
     * @return a copy with the value replaced
     */
    @NonNull
    public SimulatedNetworkConnectivityConfig withInitialEventLogCapacity(final int value) {
        return new SimulatedNetworkConnectivityConfig(
                initialSequenceNumberCapacity,
                value,
                reconnectRoundTrips,
                reconnectJitter,
                disableBroadcastRoundTrip,
                broadcastPauseOnLag,
                syncHoldBackInBroadcastMode,
                syncPauseInBroadcastMode,
                syncPauseWithoutBroadcast);
    }

    /**
     * @param value the new number of round trips of reconnect delay
     * @return a copy with the value replaced
     */
    @NonNull
    public SimulatedNetworkConnectivityConfig withReconnectRoundTrips(final double value) {
        return new SimulatedNetworkConnectivityConfig(
                initialSequenceNumberCapacity,
                initialEventLogCapacity,
                value,
                reconnectJitter,
                disableBroadcastRoundTrip,
                broadcastPauseOnLag,
                syncHoldBackInBroadcastMode,
                syncPauseInBroadcastMode,
                syncPauseWithoutBroadcast);
    }

    /**
     * @param value the new fraction the reconnect delay is varied by
     * @return a copy with the value replaced
     */
    @NonNull
    public SimulatedNetworkConnectivityConfig withReconnectJitter(final double value) {
        return new SimulatedNetworkConnectivityConfig(
                initialSequenceNumberCapacity,
                initialEventLogCapacity,
                reconnectRoundTrips,
                value,
                disableBroadcastRoundTrip,
                broadcastPauseOnLag,
                syncHoldBackInBroadcastMode,
                syncPauseInBroadcastMode,
                syncPauseWithoutBroadcast);
    }

    /**
     * @param value the new round trip above which broadcast mode is left
     * @return a copy with the value replaced
     */
    @NonNull
    public SimulatedNetworkConnectivityConfig withDisableBroadcastRoundTrip(@NonNull final Duration value) {
        return new SimulatedNetworkConnectivityConfig(
                initialSequenceNumberCapacity,
                initialEventLogCapacity,
                reconnectRoundTrips,
                reconnectJitter,
                value,
                broadcastPauseOnLag,
                syncHoldBackInBroadcastMode,
                syncPauseInBroadcastMode,
                syncPauseWithoutBroadcast);
    }

    /**
     * @param value the new time a link stays out of broadcast mode after recovering
     * @return a copy with the value replaced
     */
    @NonNull
    public SimulatedNetworkConnectivityConfig withBroadcastPauseOnLag(@NonNull final Duration value) {
        return new SimulatedNetworkConnectivityConfig(
                initialSequenceNumberCapacity,
                initialEventLogCapacity,
                reconnectRoundTrips,
                reconnectJitter,
                disableBroadcastRoundTrip,
                value,
                syncHoldBackInBroadcastMode,
                syncPauseInBroadcastMode,
                syncPauseWithoutBroadcast);
    }

    /**
     * @param value the new hold-back of forwarded copies in broadcast mode
     * @return a copy with the value replaced
     */
    @NonNull
    public SimulatedNetworkConnectivityConfig withSyncHoldBackInBroadcastMode(@NonNull final Duration value) {
        return new SimulatedNetworkConnectivityConfig(
                initialSequenceNumberCapacity,
                initialEventLogCapacity,
                reconnectRoundTrips,
                reconnectJitter,
                disableBroadcastRoundTrip,
                broadcastPauseOnLag,
                value,
                syncPauseInBroadcastMode,
                syncPauseWithoutBroadcast);
    }

    /**
     * @param value the new pause between syncs in broadcast mode
     * @return a copy with the value replaced
     */
    @NonNull
    public SimulatedNetworkConnectivityConfig withSyncPauseInBroadcastMode(@NonNull final Duration value) {
        return new SimulatedNetworkConnectivityConfig(
                initialSequenceNumberCapacity,
                initialEventLogCapacity,
                reconnectRoundTrips,
                reconnectJitter,
                disableBroadcastRoundTrip,
                broadcastPauseOnLag,
                syncHoldBackInBroadcastMode,
                value,
                syncPauseWithoutBroadcast);
    }

    /**
     * @param value the new pause between syncs when broadcast is off
     * @return a copy with the value replaced
     */
    @NonNull
    public SimulatedNetworkConnectivityConfig withSyncPauseWithoutBroadcast(@NonNull final Duration value) {
        return new SimulatedNetworkConnectivityConfig(
                initialSequenceNumberCapacity,
                initialEventLogCapacity,
                reconnectRoundTrips,
                reconnectJitter,
                disableBroadcastRoundTrip,
                broadcastPauseOnLag,
                syncHoldBackInBroadcastMode,
                syncPauseInBroadcastMode,
                value);
    }
}
