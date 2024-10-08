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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * Provides network-related utility methods.
 */
public final class Network {

    /**
     * Private constructor to prevent utility class instantiation.
     */
    private Network() {}

    /**
     * Evaluates a given {@code hostname} to ensure it is resolvable to one or more valid IPv4 or IPv6 addresses.
     * Supports domain name fragments, fully qualified domain names (FQDN), and IP addresses.
     *
     * <p>
     * This method includes retry logic to handle slow DNS queries due to network conditions, slow DNS record
     * updates/propagation, and/or intermittent network connections.
     *
     * <p>
     * This overloaded method uses a default of 20 attempts with a 2-second delay between each attempt.
     *
     * @param name the domain name fragment, fully qualified domain name (FQDN), or IP address to be resolved.
     * @return true if the name resolves to one or more valid IP addresses; otherwise false.
     * @see #isNameResolvable(String, int, int)
     */
    public static boolean isNameResolvable(@NonNull final String name) {
        return isNameResolvable(name, 20, 2_000);
    }

    /**
     * Evaluates a given {@code hostname} to ensure it is resolvable to one or more valid IPv4 or IPv6 addresses.
     * Supports domain name fragments, fully qualified domain names (FQDN), and IP addresses.
     *
     * <p>
     * This method includes retry logic to handle slow DNS queries due to network conditions, slow DNS record
     * updates/propagation, and/or intermittent network connections.
     *
     * @param name        the domain name fragment, fully qualified domain name (FQDN), or IP address to be resolved.
     * @param maxAttempts the maximum number of retry attempts.
     * @param delayMs     the delay between retry attempts.
     * @return true if the name resolves to one or more valid IP addresses; otherwise false.
     */
    public static boolean isNameResolvable(@NonNull final String name, final int maxAttempts, final int delayMs) {
        Objects.requireNonNull(name, "name must not be null");
        try {
            return Retry.check(Network::isNameResolvableInternal, name, maxAttempts, delayMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException ignored) {
            return false;
        }
    }

    /**
     * Internal implementation used by the {@link #isNameResolvable(String, int, int)} method.
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
