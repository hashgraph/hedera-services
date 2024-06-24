/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.utils.keys;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.security.DrbgParameters;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.EdDSASecurityProvider;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.PKCS8Generator;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;

/**
 * Minimal utility to read/write a single Ed25519 key from/to an encrypted PEM file.
 */
public final class Ed25519Utils {
    private static final int ENCRYPTOR_ITERATION_COUNT = 10_000;
    private static final Provider BC_PROVIDER = new BouncyCastleProvider();
    private static final Provider ED_PROVIDER = new EdDSASecurityProvider();

    private static final String RESOURCE_PATH_SEGMENT = "src/main/resource";
    public static final String TEST_CLIENTS_PREFIX = "hedera-node" + File.separator + "test-clients" + File.separator;
    public static final EdDSANamedCurveSpec ED25519_PARAMS =
            EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
    private static final DrbgParameters.Instantiation DRBG_INSTANTIATION =
            DrbgParameters.instantiation(256, DrbgParameters.Capability.RESEED_ONLY, null);

    public static KeyPair readKeyPairFrom(final File pem, final String passphrase) {
        return keyPairFrom(readKeyFrom(pem, passphrase));
    }

    public static EdDSAPrivateKey readKeyFrom(final String pemLoc, final String passphrase) {
        return readKeyFrom(new File(pemLoc), passphrase);
    }

    public static EdDSAPrivateKey readKeyFrom(final File pem, final String passphrase) {
        final var relocatedPem = relocatedIfNotPresentInWorkingDir(pem);
        try (final var in = new FileInputStream(relocatedPem)) {
            final var decryptProvider = new JceOpenSSLPKCS8DecryptorProviderBuilder()
                    .setProvider(BC_PROVIDER)
                    .build(passphrase.toCharArray());
            final var converter = new JcaPEMKeyConverter().setProvider(ED_PROVIDER);
            try (final var parser = new PEMParser(new InputStreamReader(in))) {
                final var encryptedPrivateKeyInfo = (PKCS8EncryptedPrivateKeyInfo) parser.readObject();
                final var info = encryptedPrivateKeyInfo.decryptPrivateKeyInfo(decryptProvider);
                return (EdDSAPrivateKey) converter.getPrivateKey(info);
            }
        } catch (final IOException | OperatorCreationException | PKCSException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Path relocatedIfNotPresentInWorkingDir(final Path path) {
        return relocatedIfNotPresentInWorkingDir(path.toFile()).toPath();
    }

    public static File relocatedIfNotPresentInWorkingDir(final File file) {
        return relocatedIfNotPresentWithCurrentPathPrefix(file, RESOURCE_PATH_SEGMENT, TEST_CLIENTS_PREFIX);
    }

    public static void writeKeyTo(final byte[] seed, final String pemLoc, final String passphrase) {
        writeKeyTo(keyFrom(seed), pemLoc, passphrase);
    }

    public static void writeKeyTo(final EdDSAPrivateKey key, final String pemLoc, final String passphrase) {
        final var pem = new File(pemLoc);
        try (final var out = new FileOutputStream(pem)) {
            final var random = SecureRandom.getInstance("DRBG", DRBG_INSTANTIATION);
            final var encryptor = new JceOpenSSLPKCS8EncryptorBuilder(PKCS8Generator.AES_256_CBC)
                    .setPRF(PKCS8Generator.PRF_HMACSHA384)
                    .setIterationCount(ENCRYPTOR_ITERATION_COUNT)
                    .setRandom(random)
                    .setPassword(passphrase.toCharArray())
                    .setProvider(BC_PROVIDER)
                    .build();
            try (final var pemWriter = new JcaPEMWriter(new OutputStreamWriter(out))) {
                pemWriter.writeObject(new JcaPKCS8Generator(key, encryptor).generate());
                pemWriter.flush();
            }
        } catch (final IOException | NoSuchAlgorithmException | OperatorCreationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static EdDSAPrivateKey keyFrom(final byte[] seed) {
        return new EdDSAPrivateKey(new EdDSAPrivateKeySpec(seed, ED25519_PARAMS));
    }

    public static KeyPair keyPairFrom(final EdDSAPrivateKey privateKey) {
        final var publicKey = new EdDSAPublicKey(new EdDSAPublicKeySpec(privateKey.getAbyte(), ED25519_PARAMS));
        return new KeyPair(publicKey, privateKey);
    }

    public static File relocatedIfNotPresentWithCurrentPathPrefix(
            final File file, final String firstSegmentToRelocate, final String newPathPrefix) {
        if (!file.exists()) {
            final var absPath = withDedupedHederaNodePathSegments(file.getAbsolutePath());
            final var idx = absPath.indexOf(firstSegmentToRelocate);
            if (idx == -1) {
                return new File(absPath);
            }
            final var relocatedPath = newPathPrefix + absPath.substring(idx);
            return new File(relocatedPath);
        } else {
            return file;
        }
    }

    private static String withDedupedHederaNodePathSegments(@NonNull final String loc) {
        final var firstSegmentI = loc.indexOf("hedera-node");
        if (firstSegmentI == -1) {
            return loc;
        }
        final var lastSegmentI = loc.lastIndexOf("hedera-node");
        if (lastSegmentI != firstSegmentI) {
            return loc.substring(0, firstSegmentI) + loc.substring(lastSegmentI);
        } else {
            return loc;
        }
    }

    private Ed25519Utils() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
