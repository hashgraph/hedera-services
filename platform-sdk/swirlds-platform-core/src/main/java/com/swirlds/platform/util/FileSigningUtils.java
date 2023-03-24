/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.util;

import com.swirlds.common.crypto.SignatureType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Objects;

/**
 * Class containing utilities for creating signature files
 */
public final class FileSigningUtils {
    /**
     * Hidden constructor for utility class
     */
    private FileSigningUtils() {}

    /**
     * The suffix to append to file name when generating corresponding signature file
     */
    public static final String SIGNATURE_FILE_NAME_SUFFIX = "_sig";

    /**
     * Type of the keyStore
     */
    private static final String KEYSTORE_TYPE = "pkcs12";

    /**
     * Builds a signature file path from a destination directory and file name.
     * <p>
     * Creates the needed directories if they don't already exist
     *
     * @param destinationDirectory the directory to which the signature file is saved
     * @param sourceFile           file to be signed
     * @return signature file path
     */
    @NonNull
    public static String buildSignatureFilePath(
            @NonNull final File destinationDirectory, @NonNull final File sourceFile) {

        Objects.requireNonNull(destinationDirectory, "destinationDirectory must not be null");
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");

        try {
            Files.createDirectories(destinationDirectory.toPath());
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create directory", e);
        }

        return new File(destinationDirectory, sourceFile.getName() + SIGNATURE_FILE_NAME_SUFFIX).getPath();
    }

    /**
     * Sign a byte array with a private key
     *
     * @param data    the data to be signed
     * @param keyPair the keyPair used for signing
     * @return the signature
     * @throws NoSuchAlgorithmException if an implementation of the required algorithm cannot be located or loaded
     * @throws NoSuchProviderException  thrown if the specified provider is not registered in the security provider
     *                                  list
     * @throws InvalidKeyException      thrown if the key is invalid
     * @throws SignatureException       thrown if this signature object is not initialized properly
     */
    @NonNull
    public static byte[] signData(@NonNull final byte[] data, @NonNull final KeyPair keyPair)
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {

        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(keyPair, "keyPair must not be null");

        final Signature signature =
                Signature.getInstance(SignatureType.RSA.signingAlgorithm(), SignatureType.RSA.provider());

        signature.initSign(keyPair.getPrivate());
        signature.update(data);

        return signature.sign();
    }

    /**
     * Loads a pfx key from file and returns the key pair
     *
     * @param keyFileName a pfx key file
     * @param password    the password for the key file
     * @param alias       alias of the key
     * @return a KeyPair
     */
    @NonNull
    public static KeyPair loadPfxKey(
            @NonNull final String keyFileName, @NonNull final String password, @NonNull final String alias) {

        Objects.requireNonNull(keyFileName, "keyFileName");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(alias, "alias");

        try (final FileInputStream inputStream = new FileInputStream(keyFileName)) {
            final KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            keyStore.load(inputStream, password.toCharArray());

            return new KeyPair(keyStore.getCertificate(alias).getPublicKey(), (PrivateKey)
                    keyStore.getKey(alias, password.toCharArray()));
        } catch (final NoSuchAlgorithmException
                       | KeyStoreException
                       | UnrecoverableKeyException
                       | IOException
                       | CertificateException e) {

            throw new RuntimeException("Unable to load Pfx key from file: " + keyFileName, e);
        }
    }
}
