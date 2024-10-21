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

package com.swirlds.base.utility;

import static com.swirlds.base.utility.Retry.DEFAULT_RETRY_DELAY;
import static com.swirlds.base.utility.Retry.DEFAULT_WAIT_TIME;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Objects;

/**
 * Provides network-related utility methods.
 */
public final class NetworkUtils {

    /**
     * Private constructor to prevent utility class instantiation.
     */
    private NetworkUtils() {}

    /**
     * Evaluates a given {@code hostname} to ensure it is resolvable to one or more valid IPv4 or IPv6 addresses.
     * Supports domain name fragments, fully qualified domain names (FQDN), and IP addresses.
     *
     * <p>
     * This method includes retry logic to handle slow DNS queries due to network conditions, slow DNS record
     * updates/propagation, and/or intermittent network connections.
     *
     * <p>
     * This overloaded method uses a default wait time of {@link Retry#DEFAULT_WAIT_TIME} and a default retry delay of
     * {@link Retry#DEFAULT_RETRY_DELAY}.
     *
     * @param name the domain name fragment, fully qualified domain name (FQDN), or IP address to be resolved.
     * @return true if the name resolves to one or more valid IP addresses; otherwise false.
     * @see #isNameResolvable(String, Duration, Duration)
     */
    public static boolean isNameResolvable(@NonNull final String name) {
        return isNameResolvable(name, DEFAULT_WAIT_TIME, DEFAULT_RETRY_DELAY);
    }

    /**
     * Evaluates a given {@code hostname} to ensure it is resolvable to one or more valid IPv4 or IPv6 addresses.
     * Supports domain name fragments, fully qualified domain names (FQDN), and IP addresses.
     *
     * <p>
     * This method includes retry logic to handle slow DNS queries due to network conditions, slow DNS record
     * updates/propagation, and/or intermittent network connections.
     *
     * <p>
     * This overloaded method uses a default retry delay of {@link Retry#DEFAULT_RETRY_DELAY}.
     *
     * @param name     the domain name fragment, fully qualified domain name (FQDN), or IP address to be resolved.
     * @param waitTime the maximum amount of time to wait for the DNS hostname to become resolvable.
     * @return true if the name resolves to one or more valid IP addresses; otherwise false.
     * @see #isNameResolvable(String, Duration, Duration)
     */
    public static boolean isNameResolvable(@NonNull final String name, @NonNull final Duration waitTime) {
        return isNameResolvable(name, waitTime, DEFAULT_RETRY_DELAY);
    }

    /**
     * Evaluates a given {@code hostname} to ensure it is resolvable to one or more valid IPv4 or IPv6 addresses.
     * Supports domain name fragments, fully qualified domain names (FQDN), and IP addresses.
     *
     * <p>
     * This method includes retry logic to handle slow DNS queries due to network conditions, slow DNS record
     * updates/propagation, and/or intermittent network connections.
     *
     * @param name       the domain name fragment, fully qualified domain name (FQDN), or IP address to be resolved.
     * @param waitTime   the maximum amount of time to wait for the DNS hostname to become resolvable.
     * @param retryDelay the delay between retry attempts.
     * @return true if the name resolves to one or more valid IP addresses; otherwise false.
     */
    public static boolean isNameResolvable(
            @NonNull final String name, @NonNull final Duration waitTime, @NonNull final Duration retryDelay) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(waitTime, "waitTime must not be null");
        Objects.requireNonNull(retryDelay, "retryDelay must not be null");
        try {
            return Retry.check(NetworkUtils::isNameResolvableInternal, name, waitTime, retryDelay);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Internal implementation used by the {@link #isNameResolvable(String, Duration, Duration)} method.
     *
     * @param name the domain name fragment, fully qualified domain name (FQDN), or IP address to be resolved.
     * @return true if the name resolves to one or more valid IP addresses; otherwise false.
     */
    private static boolean isNameResolvableInternal(@NonNull final String name) {
        Objects.requireNonNull(name, "name must not be null");
        try {
            final InetAddress[] addresses = InetAddress.getAllByName(name);
            // If addresses is not null and has a length greater than zero (0), then we should consider this
            // address resolvable.
            if (addresses != null && addresses.length > 0) {
                return true;
            }
        } catch (final UnknownHostException ignored) {
            // Intentionally suppressed
        }

        return false;
    }
}
