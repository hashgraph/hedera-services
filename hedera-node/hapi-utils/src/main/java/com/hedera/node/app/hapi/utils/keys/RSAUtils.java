/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

public class RSAUtils {
    public static final String SHA_384_WITH_RSA = "SHA384withRSA";
    public static final String RSA = "RSA";
    public static final String X_509 = "X.509";

    public static X509Certificate generateCertificate(@NonNull final RSAPrivateKey privateKey, final int nodeId)
            throws Exception {
        // Generate the public key from the private key
        final RSAPublicKeySpec publicKeySpec =
                new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPrivateExponent());
        final KeyFactory keyFactory = KeyFactory.getInstance(RSA);
        final PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

        // Set certificate details
        final X500Name issuer = new X500Name("CN=s-node" + nodeId);
        final X500Name subject = new X500Name("CN=s-node" + nodeId);
        // The following values are constant so that we can verify the certificate in tests
        final var seed = System.currentTimeMillis();
        final BigInteger serial = BigInteger.valueOf(seed);
        final Date notBefore = new Date(seed - 1000L);
        final LocalDateTime plus100yrs = LocalDateTime.now().plusYears(100);
        final Date notAfter =
                Date.from(plus100yrs.atZone(ZoneId.systemDefault()).toInstant());

        // Create the certificate
        final X509v3CertificateBuilder certBuilder =
                new JcaX509v3CertificateBuilder(issuer, serial, notBefore, notAfter, subject, publicKey);

        final ContentSigner signer = new JcaContentSignerBuilder(SHA_384_WITH_RSA).build(privateKey);
        return new JcaX509CertificateConverter()
                .setProvider(Ed25519Utils.BC_PROVIDER)
                .getCertificate(certBuilder.build(signer));
    }

    public static X509Certificate parseCertificate(String pemFilePath) throws Exception {
        try (PemReader pemReader = new PemReader(new FileReader(pemFilePath))) {
            final PemObject pemObject = pemReader.readPemObject();
            return parseCertificate(pemObject.getContent());
        }
    }

    public static X509Certificate parseCertificate(@NonNull final byte[] certBytes) throws Exception {
        final CertificateFactory certFactory = CertificateFactory.getInstance(X_509, Ed25519Utils.BC_PROVIDER);
        return (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes));
    }

    public static RSAPrivateKey loadPrivateKey(@NonNull final String pemFilePath, @NonNull final String pass)
            throws Exception {
        try (PEMParser pemParser = new PEMParser(new FileReader(pemFilePath))) {
            final Object object = pemParser.readObject();
            PEMDecryptorProvider decryptorProvider = new JcePEMDecryptorProviderBuilder().build(pass.toCharArray());
            final JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(Ed25519Utils.BC_PROVIDER);

            PrivateKey privateKey;

            if (object instanceof PEMEncryptedKeyPair encryptedKeyPair) {
                PEMKeyPair keyPair = encryptedKeyPair.decryptKeyPair(decryptorProvider);
                privateKey = converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            } else if (object instanceof PEMKeyPair keyPair) {
                privateKey = converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            } else if (object instanceof PrivateKeyInfo) {
                privateKey = converter.getPrivateKey((PrivateKeyInfo) object);
            } else {
                throw new IllegalArgumentException("Unsupported key format");
            }

            if (privateKey instanceof RSAPrivateKey) {
                return (RSAPrivateKey) privateKey;
            } else {
                throw new IllegalArgumentException("Not an RSAPrivateKey");
            }
        }
    }

    public static long parseIdFromPemLoc(@NonNull final Path pemLoc) {
        final var pemFilename = pemLoc.getFileName().toString();
        return Long.parseLong(
                pemFilename.replace("account", "").replace("s-public-node", "").replace(".pem", ""));
    }
}
