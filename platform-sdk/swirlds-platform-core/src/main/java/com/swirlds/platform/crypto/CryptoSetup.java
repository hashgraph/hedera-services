/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.crypto;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.legacy.LogMarker.CERTIFICATES;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.PathsConfig;
import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SystemExitCode;
import com.swirlds.common.system.SystemExitUtils;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.config.ThreadConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Initialization code for the cryptography utility. This is currently a static utility, but will no longer
 * be static in the near future.
 */
public final class CryptoSetup {

    private static final Logger logger = LogManager.getLogger(CryptoSetup.class);

    private CryptoSetup() {}

    /**
     * Create {@link Crypto} objects for all nodes in the address book.
     *
     * @param addressBook
     * 		the current address book
     * @param configuration
     * 		the current configuration
     * @return an array of crypto objects, one for each node
     */
    public static Map<NodeId, Crypto> initNodeSecurity(
            @NonNull final AddressBook addressBook, @NonNull final Configuration configuration) {
        Objects.requireNonNull(addressBook, "addressBook must not be null");
        Objects.requireNonNull(configuration, "configuration must not be null");

        final ThreadConfig threadConfig = configuration.getConfigData(ThreadConfig.class);
        final PathsConfig pathsConfig = configuration.getConfigData(PathsConfig.class);
        final CryptoConfig cryptoConfig = configuration.getConfigData(CryptoConfig.class);
        final BasicConfig basicConfig = configuration.getConfigData(BasicConfig.class);

        final ExecutorService cryptoThreadPool = Executors.newFixedThreadPool(
                threadConfig.numCryptoThreads(),
                new ThreadConfiguration(getStaticThreadManager())
                        .setComponent("browser")
                        .setThreadName("crypto-verify")
                        .setDaemon(false)
                        .buildFactory());

        final Map<NodeId, KeysAndCerts> keysAndCerts;
        try {
            if (basicConfig.loadKeysFromPfxFiles()) {
                try (final Stream<Path> list = Files.list(pathsConfig.getKeysDirPath())) {
                    CommonUtils.tellUserConsole("Reading crypto keys from the files here:   "
                            + list.filter(path -> path.getFileName().endsWith("pfx"))
                                    .toList());
                    logger.debug(STARTUP.getMarker(), "About start loading keys");
                    keysAndCerts = CryptoStatic.loadKeysAndCerts(
                            addressBook,
                            pathsConfig.getKeysDirPath(),
                            cryptoConfig.keystorePassword().toCharArray());
                    logger.debug(STARTUP.getMarker(), "Done loading keys");
                }
            } else {
                // if there are no keys on the disk, then create our own keys
                CommonUtils.tellUserConsole(
                        "Creating keys, because there are no files in " + pathsConfig.getKeysDirPath());
                logger.debug(STARTUP.getMarker(), "About to start creating generating keys");
                keysAndCerts = CryptoStatic.generateKeysAndCerts(addressBook, cryptoThreadPool);
                logger.debug(STARTUP.getMarker(), "Done generating keys");
            }
        } catch (final InterruptedException
                | ExecutionException
                | KeyStoreException
                | KeyLoadingException
                | UnrecoverableKeyException
                | NoSuchAlgorithmException
                | IOException e) {
            logger.error(EXCEPTION.getMarker(), "Exception while loading/generating keys", e);
            if (Utilities.isRootCauseSuppliedType(e, NoSuchAlgorithmException.class)
                    || Utilities.isRootCauseSuppliedType(e, NoSuchProviderException.class)) {
                CommonUtils.tellUserConsolePopup(
                        "ERROR",
                        "ERROR: This Java installation does not have the needed cryptography " + "providers installed");
            }
            SystemExitUtils.exitSystem(SystemExitCode.KEY_LOADING_FAILED);
            throw new CryptographyException(e); // will never reach this line due to exit above
        }

        final String msg = basicConfig.loadKeysFromPfxFiles() ? "Certificate loaded: {}" : "Certificate generated: {}";

        final Map<NodeId, Crypto> cryptoMap = new HashMap<>();

        keysAndCerts.forEach((nodeId, keysAndCertsForNode) -> {
            if (keysAndCertsForNode == null) {
                logger.error(CERTIFICATES.getMarker(), "No keys and certs for node {}", nodeId);
                return;
            }
            logger.debug(CERTIFICATES.getMarker(), "Node ID: {}", nodeId);
            logger.debug(CERTIFICATES.getMarker(), msg, keysAndCertsForNode.sigCert());
            logger.debug(CERTIFICATES.getMarker(), msg, keysAndCertsForNode.encCert());
            logger.debug(CERTIFICATES.getMarker(), msg, keysAndCertsForNode.agrCert());
            cryptoMap.put(nodeId, new Crypto(keysAndCertsForNode, cryptoThreadPool));
        });

        return cryptoMap;
    }
}
