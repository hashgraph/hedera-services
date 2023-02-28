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
import static com.swirlds.logging.LogMarker.CERTIFICATES;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.Settings;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.system.SystemExitReason;
import com.swirlds.platform.system.SystemUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.util.Arrays;
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
    public static Crypto[] initNodeSecurity(final AddressBook addressBook, final Configuration configuration) {
        final ExecutorService cryptoThreadPool = Executors.newFixedThreadPool(
                Settings.getInstance().getNumCryptoThreads(),
                new ThreadConfiguration(getStaticThreadManager())
                        .setComponent("browser")
                        .setThreadName("crypto-verify")
                        .setDaemon(false)
                        .buildFactory());

        final Path keysDirPath = Settings.getInstance().getKeysDirPath();
        final KeysAndCerts[] keysAndCerts;
        try {
            if (Settings.getInstance().isLoadKeysFromPfxFiles()) {
                try (final Stream<Path> list = Files.list(keysDirPath)) {
                    CommonUtils.tellUserConsole("Reading crypto keys from the files here:   "
                            + list.filter(path -> path.getFileName().endsWith("pfx"))
                                    .toList());
                    logger.debug(STARTUP.getMarker(), "About start loading keys");
                    keysAndCerts = CryptoStatic.loadKeysAndCerts(
                            addressBook,
                            keysDirPath,
                            configuration
                                    .getConfigData(CryptoConfig.class)
                                    .keystorePassword()
                                    .toCharArray());
                    logger.debug(STARTUP.getMarker(), "Done loading keys");
                }
            } else {
                // if there are no keys on the disk, then create our own keys
                CommonUtils.tellUserConsole("Creating keys, because there are no files in " + keysDirPath);
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
            SystemUtils.exitSystem(SystemExitReason.KEY_LOADING_FAILED);
            throw new CryptographyException(e); // will never reach this line due to exit above
        }

        final String msg = Settings.getInstance().isLoadKeysFromPfxFiles()
                ? "Certificate loaded: {}"
                : "Certificate generated: {}";
        Arrays.stream(keysAndCerts).filter(Objects::nonNull).forEach(k -> {
            logger.debug(CERTIFICATES.getMarker(), msg, k.sigCert());
            logger.debug(CERTIFICATES.getMarker(), msg, k.encCert());
            logger.debug(CERTIFICATES.getMarker(), msg, k.agrCert());
        });

        return Arrays.stream(keysAndCerts)
                .map(kc -> new Crypto(kc, cryptoThreadPool))
                .toArray(Crypto[]::new);
    }
}
