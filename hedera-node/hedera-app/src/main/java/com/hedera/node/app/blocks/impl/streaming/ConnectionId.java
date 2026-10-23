// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.blocks.impl.streaming;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents a connection between a consensus node and a block node. Each connection is compromised of three components:
 * <ul>
 *     <li>Node ID: the self node ID of this consensus node</li>
 *     <li>Connection Type: the type of connection this represents (i.e. a streaming connection or service connection)</li>
 *     <li>Sequence Number: A unique number assigned to the connection that is unique to the connection type for the
 *     lifespan of the JVM</li>
 * </ul>
 * Since the sequence number restarts whenever the JVM restarts, correlation IDs derived from a connection ID (see
 * {@link #correlationId()}) are suffixed with a JVM instance token ({@link #JVM_ID}) to distinguish connections made by
 * different JVMs (e.g. after a restart, or by two processes running with the same node ID). If the token cannot be
 * created, it is omitted and correlation IDs fall back to the format without it.
 */
public class ConnectionId {
    private static final Logger logger = LogManager.getLogger(ConnectionId.class);

    public enum ConnectionType {
        /**
         * Denotes a connection that intends to stream block data to a block node.
         */
        BLOCK_STREAMING("STR"), // block STReaming
        /**
         * Denotes a connection that intends to query server information from a block node.
         */
        SERVER_STATUS("SVC"); // block node SerViCe

        private final String key;

        ConnectionType(final String key) {
            this.key = key;
        }
    }

    /**
     * Number of random base-36 characters at the end of the JVM instance token.
     */
    static final int JVM_ID_RANDOM_CHARS = 2;

    /**
     * Exclusive upper bound of the random part of the JVM instance token (36^2 = 1,296 possible values).
     */
    static final int JVM_ID_RANDOM_BOUND = Character.MAX_RADIX * Character.MAX_RADIX;

    /**
     * Token that identifies this JVM instance. It is composed of the JVM start time in epoch seconds (base 36) followed
     * by exactly {@link #JVM_ID_RANDOM_CHARS} random base-36 characters. The start time can be recovered by dropping the
     * last {@link #JVM_ID_RANDOM_CHARS} characters and parsing the remainder as a base-36 long. Empty if the token
     * could not be created.
     */
    static final String JVM_ID = buildJvmId(() -> ProcessHandle.current().info().startInstant());

    /**
     * Suffix appended to the end of every correlation ID, in the format of {@code -JVM<token>}. Empty if the JVM
     * instance token could not be created.
     */
    static final String JVM_SUFFIX = JVM_ID.isEmpty() ? "" : "-JVM" + JVM_ID;

    /**
     * Connection ID counters for each type of connection.
     */
    private static final Map<ConnectionType, AtomicInteger> connIdCtrByType = new EnumMap<>(ConnectionType.class);

    static {
        for (final ConnectionType type : ConnectionType.values()) {
            connIdCtrByType.put(type, new AtomicInteger(0));
        }
    }

    private final long nodeId;
    private final ConnectionType type;
    private final int sequenceNumber;
    private final String asString;

    /**
     * Create a new connection ID with fixed values. For production code, you should create a new connection ID by
     * calling {@link #newConnectionId(long, ConnectionType)}.
     *
     * @param nodeId the self node ID of this consensus node
     * @param type the type of connection
     * @param sequenceNumber the unique sequence number
     */
    ConnectionId(final long nodeId, @NonNull final ConnectionType type, final int sequenceNumber) {
        this.nodeId = nodeId;
        this.type = requireNonNull(type, "Type is required");
        this.sequenceNumber = sequenceNumber;

        asString = "N" + nodeId + "-" + type.key + sequenceNumber;
    }

    public long nodeId() {
        return nodeId;
    }

    public ConnectionType type() {
        return type;
    }

    public int sequenceNumber() {
        return sequenceNumber;
    }

    /**
     * @return the connection-level correlation ID in the format of {@code N#-[STR|SVC]#-JVM<token>}. This is the value
     * sent to block nodes and used to identify this connection in logs.
     */
    public @NonNull String correlationId() {
        return asString + JVM_SUFFIX;
    }

    /**
     * @return the connection ID without the JVM instance token, in the format of {@code N#-[STR|SVC]#}
     */
    @Override
    public @NonNull String toString() {
        return asString;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConnectionId that = (ConnectionId) o;
        return nodeId == that.nodeId && sequenceNumber == that.sequenceNumber && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, type, sequenceNumber);
    }

    /**
     * Create a new connection ID for the specified connection type and node.
     *
     * @param nodeId the local node ID
     * @param type the type of connection being created
     * @return the new connection ID
     */
    public static ConnectionId newConnectionId(final long nodeId, @NonNull final ConnectionType type) {
        final int id = connIdCtrByType.get(type).incrementAndGet();
        return new ConnectionId(nodeId, type, id);
    }

    /**
     * @return the JVM instance token, or an empty string if it could not be created
     */
    public static @NonNull String jvmId() {
        return JVM_ID;
    }

    /**
     * Builds the JVM instance token from the JVM start time and a random value. If the start time is not available,
     * the current time is used instead. This is called from a static initializer, so it must never throw: if the token
     * cannot be created, a single warning is logged and an empty string is returned.
     *
     * @param startInstantSupplier supplier of the JVM start time
     * @return the JVM instance token, or an empty string if it could not be created
     */
    static @NonNull String buildJvmId(@NonNull final Supplier<Optional<Instant>> startInstantSupplier) {
        try {
            final long startSeconds =
                    startInstantSupplier.get().orElseGet(Instant::now).getEpochSecond();
            final int rand = ThreadLocalRandom.current().nextInt(JVM_ID_RANDOM_BOUND);
            return formatJvmId(startSeconds, rand);
        } catch (final RuntimeException e) {
            logger.warn("Unable to create JVM instance ID; correlation IDs will not include it", e);
            return "";
        }
    }

    /**
     * Formats a JVM instance token as the base-36 start time followed by the random value as exactly
     * {@link #JVM_ID_RANDOM_CHARS} zero-padded base-36 characters.
     *
     * @param startSeconds the JVM start time in epoch seconds
     * @param rand the random value, in the range [0, {@link #JVM_ID_RANDOM_BOUND})
     * @return the JVM instance token
     */
    static @NonNull String formatJvmId(final long startSeconds, final int rand) {
        if (startSeconds < 0) {
            throw new IllegalArgumentException("Start seconds must be non-negative");
        }
        if (rand < 0 || rand >= JVM_ID_RANDOM_BOUND) {
            throw new IllegalArgumentException("Random value must be in the range [0, " + JVM_ID_RANDOM_BOUND + ")");
        }
        final String randPart = Integer.toString(rand, Character.MAX_RADIX);
        return Long.toString(startSeconds, Character.MAX_RADIX)
                + "0".repeat(JVM_ID_RANDOM_CHARS - randPart.length())
                + randPart;
    }
}
