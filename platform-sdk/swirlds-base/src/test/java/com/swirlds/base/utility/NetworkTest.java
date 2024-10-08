package com.swirlds.base.utility;

import org.junit.jupiter.api.Test;

import static com.swirlds.base.utility.Network.isNameResolvable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates the behavior of the {@link Network} utility class.
 */
public class NetworkTest {

    @Test
    void ipAddressShouldResolve() {
        final String ipv4 =  "127.0.0.1";
        assertThat(isNameResolvable(ipv4)).isTrue();
    }

    @Test
    void validHostnameShouldResolve() {
        final String hostname = "localhost";
        assertThat(isNameResolvable(hostname)).isTrue();
    }

    @Test
    void invalidHostnameShouldNotResolve() {
        final String hostname = "invalid-hostname.local";
        assertThat(isNameResolvable(hostname, 2, 0)).isFalse();
    }

    @Test
    void publicHostnameShouldResolve() {
        final String hostname = "google.com";
        assertThat(isNameResolvable(hostname)).isTrue();
    }

    @Test
    void nullHostnameShouldThrow() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> isNameResolvable(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name must not be null");
    }

    @Test
    void zeroMaxAttemptsShouldThrow() {
        assertThatThrownBy(() -> isNameResolvable("localhost", 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The maximum number of attempts must be greater than zero (0)");
    }

    @Test
    void negativeDelayShouldThrow() {
        assertThatThrownBy(() -> isNameResolvable("localhost", 1, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The delay must be greater than or equal to zero (0)");
    }
}
