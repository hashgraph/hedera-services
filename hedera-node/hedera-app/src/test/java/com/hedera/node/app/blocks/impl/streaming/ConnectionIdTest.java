// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.blocks.impl.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hedera.node.app.blocks.impl.streaming.ConnectionId.ConnectionType;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import java.security.SecureRandom;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConnectionIdTest {

    /**
     * Exactly eight base-36 characters.
     */
    private static final String JVM_ID_PATTERN = "[0-9a-z]{8}";

    /**
     * Maximum length of the correlation ID header accepted by block nodes.
     */
    private static final int MAX_CORRELATION_ID_HEADER_LENGTH = 64;

    @Test
    void testJvmIdFormat() {
        final String jvmId = ConnectionId.jvmId();
        assumeTrue(!jvmId.isEmpty(), "JVM instance ID could not be created on this platform");

        assertThat(jvmId).matches(JVM_ID_PATTERN);
        assertThat(ConnectionId.JVM_SUFFIX).isEqualTo("-JVM" + jvmId);
    }

    @Test
    void testJvmIdBoundIsAllBase36Values() {
        assertThat(ConnectionId.JVM_ID_BOUND).isEqualTo(Long.parseLong("zzzzzzzz", Character.MAX_RADIX) + 1);
    }

    @ParameterizedTest
    @CsvSource({
        "0, 00000000",
        "5, 00000005",
        "35, 0000000z",
        "36, 00000010",
        "1792610793, 00tn9vc9",
        "2821109907455, zzzzzzzz"
    })
    void testFormatJvmId(final long value, final String expected) {
        final String jvmId = ConnectionId.formatJvmId(value);

        assertThat(jvmId).isEqualTo(expected).matches(JVM_ID_PATTERN);
        assertThat(Long.parseLong(jvmId, Character.MAX_RADIX)).isEqualTo(value);
    }

    @Test
    void testFormatJvmId_invalidInputs() {
        assertThatThrownBy(() -> ConnectionId.formatJvmId(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConnectionId.formatJvmId(ConnectionId.JVM_ID_BOUND))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testBuildJvmId_usesSuppliedRandom() {
        final LogCaptor logCaptor = new LogCaptor(LogManager.getLogger(ConnectionId.class));
        try {
            assertThat(ConnectionId.buildJvmId(() -> 0L)).isEqualTo("00000000");
            assertThat(logCaptor.warnLogs()).isEmpty();
        } finally {
            logCaptor.stopCapture();
        }
    }

    @Test
    void testBuildJvmId_differsBetweenCalls() {
        final SecureRandom random = new SecureRandom();

        assertThat(ConnectionId.buildJvmId(random))
                .matches(JVM_ID_PATTERN)
                .isNotEqualTo(ConnectionId.buildJvmId(random));
    }

    @Test
    void testBuildJvmId_randomThrowsReturnsEmpty() {
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
    void testCorrelationId() {
        final ConnectionId streamingId = new ConnectionId(37, ConnectionType.BLOCK_STREAMING, 6);
        final ConnectionId serviceId = new ConnectionId(37, ConnectionType.SERVER_STATUS, 41);

        assertThat(streamingId).hasToString("N37-STR6");
        assertThat(streamingId.correlationId()).isEqualTo("N37-STR6" + ConnectionId.JVM_SUFFIX);
        assertThat(serviceId).hasToString("N37-SVC41");
        assertThat(serviceId.correlationId()).isEqualTo("N37-SVC41" + ConnectionId.JVM_SUFFIX);
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
        // worst case: max node ID / sequence number, combined with the largest JVM ID
        final ConnectionId connectionId =
                new ConnectionId(Long.MAX_VALUE, ConnectionType.BLOCK_STREAMING, Integer.MAX_VALUE);
        final String worstCaseJvmId = ConnectionId.formatJvmId(ConnectionId.JVM_ID_BOUND - 1);
        final String correlationId = connectionId.toString() + "-JVM" + worstCaseJvmId;

        assertThat(correlationId.length()).isLessThanOrEqualTo(MAX_CORRELATION_ID_HEADER_LENGTH);
        assertThat(connectionId.correlationId().length()).isLessThanOrEqualTo(MAX_CORRELATION_ID_HEADER_LENGTH);
    }
}
