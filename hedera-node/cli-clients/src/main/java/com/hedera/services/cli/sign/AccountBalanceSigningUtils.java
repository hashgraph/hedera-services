/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.cli.sign;

import static com.hedera.services.cli.sign.SignUtils.TYPE_FILE_HASH;
import static com.hedera.services.cli.sign.SignUtils.TYPE_SIGNATURE;
import static com.hedera.services.cli.sign.SignUtils.integerToBytes;
import static com.hedera.services.cli.sign.SignUtils.sign;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.computeEntireHash;
import static com.swirlds.logging.LogMarker.FILE_SIGN;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.util.BootstrapUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class for signing account balance files
 */
public class AccountBalanceSigningUtils {

    private static final Logger logger = LogManager.getLogger(AccountBalanceSigningUtils.class);
    /**
     * Hidden constructor
     */
    private AccountBalanceSigningUtils() {}



    /**
     * Sets up the constructable registry, and configures
     * <p>
     * Should be called before using stream utilities
     * <p>
     * Do <strong>NOT</strong> call this during standard node operation, as it will interfere with static settings
     */
    public static void initializeSystem() {
        BootstrapUtils.setupConstructableRegistry();

        // we don't want deserialization to fail based on any of these settings, not needed for services
        //        SettingsCommon.maxTransactionCountPerEvent = Integer.MAX_VALUE;
        //        SettingsCommon.maxTransactionBytesPerEvent = Integer.MAX_VALUE;
        //        SettingsCommon.transactionMaxBytes = Integer.MAX_VALUE;
        //        SettingsCommon.maxAddressSizeAllowed = Integer.MAX_VALUE;
    }

    /**
     * Generates a signature file for the account balance file
     *
     * @param signatureFileDestination the full path where the signature file will be generated
     * @param streamFileToSign         the stream file to be signed
     * @param keyPair                  the keyPair used for signing
     * @return true if the signature file was generated successfully, false otherwise
     */
    public static boolean signAccountBalanceFile(
            @NonNull final Path signatureFileDestination,
            @NonNull final Path streamFileToSign,
            @NonNull final KeyPair keyPair) {

        Objects.requireNonNull(signatureFileDestination, "signatureFileDestination must not be null");
        Objects.requireNonNull(streamFileToSign, "streamFileToSign must not be null");
        Objects.requireNonNull(keyPair, "keyPair must not be null");

        try {
            final Hash entireHash = computeEntireHash(streamFileToSign.toFile());
            final byte[] fileHashByte = entireHash.getValue();
            final byte[] signature = sign(fileHashByte, keyPair);

            generateSigBalanceFile(signatureFileDestination.toFile(), signature, fileHashByte);

            System.out.println("Generated signature file: " + signatureFileDestination);

            return true;
        } catch (final SignatureException | IOException e) {
            System.err.println("Failed to sign file " + streamFileToSign.getFileName() + ". Exception: " + e);
            return false;
        } catch (final InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException("Irrecoverable error encountered", e);
        }
    }

    private static void generateSigBalanceFile(final File filePath, final byte[] signature, final byte[] fileHash) {
        try (final BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(filePath))) {
            output.write(TYPE_FILE_HASH);
            output.write(fileHash);
            output.write(TYPE_SIGNATURE);
            output.write(integerToBytes(signature.length));
            output.write(signature);
            output.flush();
        } catch (final IOException e) {
            logger.error(
                    FILE_SIGN.getMarker(),
                    "generateSigBalanceFile :: Fail to generate signature file for {}. Exception: {}",
                    filePath,
                    e);
        }
    }
}
