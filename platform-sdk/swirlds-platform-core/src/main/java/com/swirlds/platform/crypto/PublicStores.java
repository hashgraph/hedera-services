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

import static com.swirlds.platform.crypto.KeyCertPurpose.AGREEMENT;
import static com.swirlds.platform.crypto.KeyCertPurpose.ENCRYPTION;
import static com.swirlds.platform.crypto.KeyCertPurpose.SIGNING;

import com.swirlds.common.crypto.CryptographyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import org.apache.commons.lang3.ObjectUtils;

/**
 * Public certificates for all the members of the network
 *
 * @param agrTrustStore
 * 		the trust store for all the sig certs (self-signed signing cert)
 * @param encTrustStore
 * 		the trust store for all the agr certs (agreement cert, signed by signing key)
 * @param sigTrustStore
 * 		the trust store for all the enc certs (encryption cert, signed by signing key)
 */
public record PublicStores(KeyStore sigTrustStore, KeyStore agrTrustStore, KeyStore encTrustStore) {
    public PublicStores() throws KeyStoreException {
        this(
                CryptoStatic.createEmptyTrustStore(),
                CryptoStatic.createEmptyTrustStore(),
                CryptoStatic.createEmptyTrustStore());
    }

    /**
     * Creates an instance loads all the certificates from the provided key store
     *
     * @param allPublic
     * 		key store with all certificates
     * @param names
     * 		the names of all members
     * @return an instance
     * @throws KeyStoreException
     * 		if there is no provider that supports {@link CryptoConstants#KEYSTORE_TYPE}
     * 		or the key store provided has not been initialized
     * @throws KeyLoadingException
     * 		if any of the certificates cannot be found
     */
    public static PublicStores fromAllPublic(final KeyStore allPublic, final Iterable<String> names)
            throws KeyStoreException, KeyLoadingException {
        final KeyStore sigTrustStore = CryptoStatic.createEmptyTrustStore();
        final KeyStore encTrustStore = CryptoStatic.createEmptyTrustStore();
        final KeyStore agrTrustStore = CryptoStatic.createEmptyTrustStore();

        for (String name : names) {
            Certificate sigCert = allPublic.getCertificate(SIGNING.storeName(name));
            Certificate agrCert = allPublic.getCertificate(AGREEMENT.storeName(name));
            Certificate encCert = allPublic.getCertificate(ENCRYPTION.storeName(name));

            if (ObjectUtils.anyNull(sigCert, agrCert, encCert)) {
                throw new KeyLoadingException("Cannot find certificates for: " + name);
            }

            sigTrustStore.setCertificateEntry(SIGNING.storeName(name), sigCert);
            agrTrustStore.setCertificateEntry(AGREEMENT.storeName(name), agrCert);
            encTrustStore.setCertificateEntry(ENCRYPTION.storeName(name), encCert);
        }
        return new PublicStores(sigTrustStore, agrTrustStore, encTrustStore);
    }

    /**
     * @param type
     * 		the type of certificate
     * @param certificate
     * 		the certificate
     * @param name
     * 		the name of the member
     * @throws KeyStoreException
     * 		if the given alias already exists and does not identify an entry containing a trusted certificate,
     * 		or this operation fails for some other reason
     */
    public void setCertificate(final KeyCertPurpose type, final X509Certificate certificate, final String name)
            throws KeyStoreException {
        switch (type) {
            case SIGNING -> sigTrustStore.setCertificateEntry(type.storeName(name), certificate);
            case AGREEMENT -> agrTrustStore.setCertificateEntry(type.storeName(name), certificate);
            case ENCRYPTION -> encTrustStore.setCertificateEntry(type.storeName(name), certificate);
        }
    }

    /**
     * @param type
     * 		the type of certificate requested
     * @param name
     * 		the name of the member whose certificate is requested
     * @return a certificate stored in one of the stores
     * @throws KeyLoadingException
     * 		if the certificate is missing or is not an instance of X509Certificate
     */
    public X509Certificate getCertificate(final KeyCertPurpose type, final String name) throws KeyLoadingException {
        final Certificate certificate;
        try {
            certificate = switch (type) {
                case SIGNING -> sigTrustStore.getCertificate(type.storeName(name));
                case AGREEMENT -> agrTrustStore.getCertificate(type.storeName(name));
                case ENCRYPTION -> encTrustStore.getCertificate(type.storeName(name));};
        } catch (KeyStoreException e) {
            // cannot be thrown because we ensure the key store is initialized in the constructor
            throw new CryptographyException(e);
        }
        if (certificate == null) {
            throw new KeyLoadingException("Certificate not found", type, name);
        }
        if (certificate instanceof X509Certificate x509) {
            return x509;
        }
        throw new KeyLoadingException("Certificate is not an instance of X509Certificate", type, name);
    }

    /**
     * @param type
     * 		the type of key requested
     * @param name
     * 		the name of the member whose key is requested
     * @return this members public key
     * @throws KeyLoadingException
     * 		if {@link #getCertificate(KeyCertPurpose, String)} throws
     */
    public PublicKey getPublicKey(final KeyCertPurpose type, final String name) throws KeyLoadingException {
        return getCertificate(type, name).getPublicKey();
    }
}
