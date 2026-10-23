// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.blocks.impl.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hedera.node.app.blocks.impl.streaming.ConnectionId.ConnectionType;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import java.time.Instant;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConnectionIdTest {

    /**
     * Base-36 start time followed by exactly two base-36 random characters.
     */
    private static final String JVM_ID_PATTERN = "[0-9a-z]+[0-9a-z]{2}";

    /**
     * Maximum length of the correlation ID header accepted by block nodes.
     */
    private static final int MAX_CORRELATION_ID_HEADER_LENGTH = 64;

    @Test
    void testJvmIdFormat() {
        final String jvmId = ConnectionId.jvmId();

        assertThat(jvmId).matches(JVM_ID_PATTERN).isEqualTo(ConnectionId.JVM_ID);
        assertThat(ConnectionId.JVM_SUFFIX).isEqualTo("-JVM" + jvmId);
    }

    @Test
    void testJvmIdDecodesToJvmStartTime() {
        final String jvmId = ConnectionId.jvmId();
        final long startSeconds = Long.parseLong(
                jvmId.substring(0, jvmId.length() - ConnectionId.JVM_ID_RANDOM_CHARS), Character.MAX_RADIX);
        final Optional<Instant> expectedStart = ProcessHandle.current().info().startInstant();
        assumeTrue(expectedStart.isPresent(), "JVM start time is not available on this platform");

        assertThat(startSeconds).isEqualTo(expectedStart.get().getEpochSecond());
    }

    @ParameterizedTest
    @CsvSource({
        "1792610793, 0, tn9vc900",
        "1792610793, 5, tn9vc905",
        "1792610793, 35, tn9vc90z",
        "1792610793, 36, tn9vc910",
        "1792610793, 739, tn9vc9kj",
        "1792610793, 1295, tn9vc9zz",
        "0, 0, 000",
        // last second with a 6 character start time (2038-12-24T05:45:35Z), followed by the first with 7 characters
        "2176782335, 1, zzzzzz01",
        "2176782336, 1, 100000001"
    })
    void testFormatJvmId(final long startSeconds, final int rand, final String expected) {
        final String jvmId = ConnectionId.formatJvmId(startSeconds, rand);

        assertThat(jvmId).isEqualTo(expected).matches(JVM_ID_PATTERN);
        // the start time can always be recovered by dropping the random characters
        assertThat(Long.parseLong(
                        jvmId.substring(0, jvmId.length() - ConnectionId.JVM_ID_RANDOM_CHARS), Character.MAX_RADIX))
                .isEqualTo(startSeconds);
        // the random part is always exactly two characters
        assertThat(Integer.parseInt(
                        jvmId.substring(jvmId.length() - ConnectionId.JVM_ID_RANDOM_CHARS), Character.MAX_RADIX))
                .isEqualTo(rand);
    }

    @Test
    void testFormatJvmId_invalidInputs() {
        assertThatThrownBy(() -> ConnectionId.formatJvmId(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConnectionId.formatJvmId(0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConnectionId.formatJvmId(0, ConnectionId.JVM_ID_RANDOM_BOUND))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testBuildJvmId_usesSuppliedStartTime() {
        final LogCaptor logCaptor = new LogCaptor(LogManager.getLogger(ConnectionId.class));
        try {
            final String jvmId = ConnectionId.buildJvmId(() -> Optional.of(Instant.ofEpochSecond(1792610793L)));

            assertThat(jvmId).matches(JVM_ID_PATTERN).startsWith("tn9vc9").hasSize(8);
            assertThat(logCaptor.warnLogs()).isEmpty();
        } finally {
            logCaptor.stopCapture();
        }
    }

    @Test
    void testBuildJvmId_startTimeUnavailableFallsBackToNow() {
        final LogCaptor logCaptor = new LogCaptor(LogManager.getLogger(ConnectionId.class));
        try {
            final long before = Instant.now().getEpochSecond();
            final String jvmId = ConnectionId.buildJvmId(Optional::empty);
            final long after = Instant.now().getEpochSecond();

            assertThat(jvmId).matches(JVM_ID_PATTERN);
            final long startSeconds = Long.parseLong(
                    jvmId.substring(0, jvmId.length() - ConnectionId.JVM_ID_RANDOM_CHARS), Character.MAX_RADIX);
            assertThat(startSeconds).isBetween(before, after);
            assertThat(logCaptor.warnLogs()).isEmpty();
        } finally {
            logCaptor.stopCapture();
        }
    }

    @Test
    void testBuildJvmId_supplierThrowsReturnsEmpty() {
        final LogCaptor logCaptor = new LogCaptor(LogManager.getLogger(ConnectionId.class));
        try {
            final String jvmId = ConnectionId.buildJvmId(() -> {
                throw new UnsupportedOperationException("not supported");
            });

            assertThat(jvmId).isEmpty();
            assertThat(logCaptor.warnLogs()).singleElement().asString().contains("Unable to create JVM instance ID");
        } finally {
            logCaptor.stopCapture();
        }
    }

    @Test
    void testBuildJvmId_invalidStartTimeReturnsEmpty() {
        final LogCaptor logCaptor = new LogCaptor(LogManager.getLogger(ConnectionId.class));
        try {
            final String jvmId = ConnectionId.buildJvmId(() -> Optional.of(Instant.ofEpochSecond(-1)));

            assertThat(jvmId).isEmpty();
            assertThat(logCaptor.warnLogs()).singleElement().asString().contains("Unable to create JVM instance ID");
        } finally {
            logCaptor.stopCapture();
        }
    }

    @Test
    void testCorrelationId() {
        final ConnectionId streamingId = new ConnectionId(37, ConnectionType.BLOCK_STREAMING, 6);
        final ConnectionId serviceId = new ConnectionId(37, ConnectionType.SERVER_STATUS, 41);

        assertThat(streamingId).hasToString("N37-STR6");
        assertThat(streamingId.correlationId()).isEqualTo("N37-STR6-JVM" + ConnectionId.jvmId());
        assertThat(serviceId).hasToString("N37-SVC41");
        assertThat(serviceId.correlationId()).isEqualTo("N37-SVC41-JVM" + ConnectionId.jvmId());
    }

    @Test
    void testJvmIdSameForAllConnections() {
        final ConnectionId first = ConnectionId.newConnectionId(1, ConnectionType.BLOCK_STREAMING);
        final ConnectionId second = ConnectionId.newConnectionId(2, ConnectionType.SERVER_STATUS);

        assertThat(first.correlationId()).endsWith(ConnectionId.JVM_SUFFIX);
        assertThat(second.correlationId()).endsWith(ConnectionId.JVM_SUFFIX);
    }

    @Test
    void testEqualsAndHashCodeIgnoreJvmId() {
        final ConnectionId a = new ConnectionId(1, ConnectionType.BLOCK_STREAMING, 3);
        final ConnectionId b = new ConnectionId(1, ConnectionType.BLOCK_STREAMING, 3);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void testCorrelationIdFitsWithinHeaderLimit() {
        // worst case: max node ID / sequence number, combined with the JVM ID format after 2038 (7 char start time)
        final ConnectionId connectionId =
                new ConnectionId(Long.MAX_VALUE, ConnectionType.BLOCK_STREAMING, Integer.MAX_VALUE);
        final String worstCaseJvmId = ConnectionId.formatJvmId(Long.parseLong("zzzzzzz", 36), 1295);
        final String correlationId = connectionId.toString() + "-JVM" + worstCaseJvmId;

        assertThat(correlationId.length()).isLessThanOrEqualTo(MAX_CORRELATION_ID_HEADER_LENGTH);
        assertThat(connectionId.correlationId().length()).isLessThanOrEqualTo(MAX_CORRELATION_ID_HEADER_LENGTH);
    }
}
