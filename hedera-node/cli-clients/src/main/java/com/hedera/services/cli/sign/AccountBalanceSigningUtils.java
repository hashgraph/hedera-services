/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.computeEntireHash;
import static com.swirlds.platform.util.FileSigningUtils.signData;

import com.swirlds.common.crypto.Hash;
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

/**
 * Utility class for signing account balance files
 */
@SuppressWarnings("java:S106") // Suppressing the usage of System.out.println instead of a logger
public class AccountBalanceSigningUtils {

    /**
     * Hidden constructor
     */
    private AccountBalanceSigningUtils() {}

    /**
     * Generates a signature file for the account balance file
     *
     * @param signatureFileDestination the full path where the signature file will be generated
     * @param streamFileToSign         the stream file to be signed
     * @param keyPair                  the keyPair used for signing
     * @return true if the signature file was generated successfully, false otherwise
     */
    @SuppressWarnings("java:S112") // Suppressing that we are throwing RuntimeException(generic exception)
    public static boolean signAccountBalanceFile(
            @NonNull final Path signatureFileDestination,
            @NonNull final Path streamFileToSign,
            @NonNull final KeyPair keyPair) {

        Objects.requireNonNull(signatureFileDestination, "signatureFileDestination must not be null");
        Objects.requireNonNull(streamFileToSign, "streamFileToSign must not be null");
        Objects.requireNonNull(keyPair, "keyPair must not be null");

        try {
            final Hash entireHash = computeEntireHash(streamFileToSign.toFile());
            final byte[] fileHashByte = entireHash.copyToByteArray();
            final byte[] signature = signData(fileHashByte, keyPair);

            generateSigBalanceFile(signatureFileDestination.toFile(), signature, fileHashByte);

            System.out.println("Generated signature file: " + signatureFileDestination);

            return true;
        } catch (final SignatureException | InvalidKeyException | IOException e) {
            System.err.printf("signAccountBalanceFile :: Failed to sign file [%s]", streamFileToSign);
            return false;
        } catch (final NoSuchAlgorithmException | NoSuchProviderException e) {
            System.err.printf(
                    "signAccountBalanceFile :: Irrecoverable error encountered when signing [%s]", streamFileToSign);
            throw new RuntimeException("signAccountBalanceFile :: Irrecoverable error encountered", e);
        }
    }

    private static void generateSigBalanceFile(final File filePath, final byte[] signature, final byte[] fileHash)
            throws IOException {
        try (final BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(filePath))) {
            output.write(TYPE_FILE_HASH);
            output.write(fileHash);
            output.write(TYPE_SIGNATURE);
            output.write(integerToBytes(signature.length));
            output.write(signature);
            output.flush();
        } catch (final IOException e) {
            System.err.printf(
                    "generateSigBalanceFile :: Failed to generate signature file [%s] with exception : [%s]%n",
                    filePath.getAbsolutePath(), e);
            throw e;
        }
    }
}
