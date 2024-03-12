/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.network;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.SOCKET_EXCEPTIONS;

import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.gossip.shadowgraph.SyncTimeoutException;
import com.swirlds.platform.network.connectivity.SocketFactory;
import com.swirlds.platform.network.connectivity.TcpFactory;
import com.swirlds.platform.network.connectivity.TlsFactory;
import com.swirlds.platform.system.PlatformConstructionException;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Closeable;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Objects;
import javax.net.ssl.SSLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

public final class NetworkUtils {
    private static final Logger logger = LogManager.getLogger(NetworkUtils.class);

    private NetworkUtils() {}

    /**
     * Close all the {@link Closeable} instances supplied, ignoring any exceptions
     *
     * @param closeables the instances to close
     */
    public static void close(final Closeable... closeables) {
        for (final Closeable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            } catch (final IOException | RuntimeException ignored) {
                // we try to close, but ignore any issues if we fail
            }
        }
    }

    /**
     * Called when an exception happens while executing something that uses a connection. This method will close the
     * connection supplied and log the exception with an appropriate marker.
     *
     * @param e          the exception that was thrown
     * @param connection the connection used when the exception was thrown
     * @throws InterruptedException if the provided exception is an {@link InterruptedException}, it will be rethrown
     *                              once the connection is closed
     */
    public static void handleNetworkException(final Exception e, final Connection connection)
            throws InterruptedException {
        final String description;
        // always disconnect when an exception gets thrown
        if (connection != null) {
            connection.disconnect();
            description = connection.getDescription();
        } else {
            description = null;
        }
        if (e instanceof InterruptedException ie) {
            // we must make sure that the network thread can be interrupted
            throw ie;
        }
        // we use a different marker depending on what the root cause is
        Marker marker = NetworkUtils.determineExceptionMarker(e);
        if (SOCKET_EXCEPTIONS.getMarker().equals(marker)) {
            String formattedException = NetworkUtils.formatException(e);
            logger.warn(marker, "Connection broken: {} {}", description, formattedException);
        } else {
            logger.error(EXCEPTION.getMarker(), "Connection broken: {}", description, e);
        }
    }

    /**
     * Determines the log marker to use for a connection exception based on the nested exception types
     *
     * @param e the exception thrown during a network operations
     * @return the marker to use for logging
     */
    public static Marker determineExceptionMarker(final Exception e) {
        return Utilities.isCausedByIOException(e)
                        || Utilities.isRootCauseSuppliedType(e, SyncTimeoutException.class)
                        // All SSLExceptions regardless of nested root cause need to be classified as SOCKET_EXCEPTIONS.
                        // https://github.com/hashgraph/hedera-services/issues/7762
                        || Utilities.hasAnyCauseSuppliedType(e, SSLException.class)
                ? SOCKET_EXCEPTIONS.getMarker()
                : EXCEPTION.getMarker();
    }

    /**
     * Returns a string containing the exception class name and the exception message.
     *
     * @param e The exception to format.
     * @return The string containing the exception class name and message.
     */
    public static String formatException(final Throwable e) {
        if (e == null) {
            return "";
        }
        return "Caused by exception: " + e.getClass().getSimpleName() + " Message: " + e.getMessage() + " "
                + formatException(e.getCause());
    }

    /**
     * Create a {@link SocketFactory} based on the configuration and the provided keys and certificates.
     * NOTE: This method is a stepping stone to decoupling the networking from the platform.
     *
     * @param selfId         the ID of the node
     * @param addressBook    the address book of the network
     * @param keysAndCerts   the keys and certificates to use for the TLS connections
     * @param configuration  the configuration of the network
     * @return the created {@link SocketFactory}
     */
    public static @NonNull SocketFactory createSocketFactory(
            @NonNull final NodeId selfId,
            @NonNull final AddressBook addressBook,
            @NonNull final KeysAndCerts keysAndCerts,
            @NonNull final Configuration configuration) {
        Objects.requireNonNull(selfId);
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(keysAndCerts);
        Objects.requireNonNull(configuration);

        final CryptoConfig cryptoConfig = configuration.getConfigData(CryptoConfig.class);
        final SocketConfig socketConfig = configuration.getConfigData(SocketConfig.class);

        if (!socketConfig.useTLS()) {
            return new TcpFactory(socketConfig);
        }
        try {
            return new TlsFactory(
                    keysAndCerts.agrCert(),
                    keysAndCerts.agrKeyPair().getPrivate(),
                    Utilities.createPeerInfoList(addressBook, selfId),
                    socketConfig,
                    cryptoConfig);
        } catch (final NoSuchAlgorithmException
                | UnrecoverableKeyException
                | KeyStoreException
                | KeyManagementException
                | CertificateException
                | IOException e) {
            throw new PlatformConstructionException("A problem occurred while creating the SocketFactory", e);
        }
    }
}
