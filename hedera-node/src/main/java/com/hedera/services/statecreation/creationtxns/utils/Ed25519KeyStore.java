package com.hedera.services.statecreation.creationtxns.utils;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.EdDSASecurityProvider;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.DrbgParameters;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.ArrayList;

public class Ed25519KeyStore extends ArrayList<KeyPair> implements KeyStore {

	public final static class Builder {

		public Builder withPassword(final char[] password) {
			this.password = password;
			return this;
		}

		public Ed25519KeyStore build() throws KeyStoreException {
			if (password == null) {
				password = new char[0];
			}
			if (converter == null) {
				this.converter = new JcaPEMKeyConverter().setProvider(ED_PROVIDER);
			}
			if (random == null) {
				try {
					this.random = SecureRandom.getInstance("DRBG",
							DrbgParameters.instantiation(256, DrbgParameters.Capability.RESEED_ONLY, null));
				} catch (NoSuchAlgorithmException ex) {
					throw new KeyStoreException(ex);
				}
			}
			return new Ed25519KeyStore(password, converter, random);
		}

		private SecureRandom random;
		private JcaPEMKeyConverter converter;
		private char[] password;
	}

	private static final Provider BC_PROVIDER = new BouncyCastleProvider();
	private static final Provider ED_PROVIDER = new EdDSASecurityProvider();

	public static Ed25519KeyStore read(final File source) throws KeyStoreException {
		final Ed25519KeyStore keyStore = new Builder().build();
		keyStore.loadFile(source);
		return keyStore;
	}

	public static Ed25519KeyStore read(final char[] password, final File source) throws KeyStoreException {
		final Ed25519KeyStore keyStore = new Builder().withPassword(password).build();
		keyStore.loadFile(source);
		return keyStore;
	}

	public static Ed25519KeyStore read(final char[] password, final String sourceFile) throws KeyStoreException {
		return read(password, new File(sourceFile));
	}

	public static Ed25519KeyStore read(final char[] password, final URL sourceUrl) throws KeyStoreException {
		try {
			return read(password, new File(sourceUrl.toURI()));
		} catch (URISyntaxException ex) {
			throw new KeyStoreException(ex);
		}
	}

	public static Ed25519KeyStore read(final char[] password, final InputStream source) throws KeyStoreException {
		final Ed25519KeyStore keyStore = new Builder().withPassword(password).build();
		keyStore.loadKeyPairs(source);
		return keyStore;
	}

	private KeyPair decodeKeyPair(Object rawObject) throws KeyStoreException {
		try {
			KeyPair kp;

			if (rawObject instanceof PEMEncryptedKeyPair) {
				final PEMEncryptedKeyPair ekp = (PEMEncryptedKeyPair) rawObject;
				final PEMDecryptorProvider decryptor = new JcePEMDecryptorProviderBuilder().setProvider(
						BC_PROVIDER).build(password);
				kp = converter.getKeyPair(ekp.decryptKeyPair(decryptor));
			} else if (rawObject instanceof PKCS8EncryptedPrivateKeyInfo) {
				final PKCS8EncryptedPrivateKeyInfo ekpi = (PKCS8EncryptedPrivateKeyInfo) rawObject;
				final InputDecryptorProvider decryptor = new JceOpenSSLPKCS8DecryptorProviderBuilder()
						.setProvider(BC_PROVIDER)
						.build(password);

				final PrivateKeyInfo pki = ekpi.decryptPrivateKeyInfo(decryptor);
				final EdDSAPrivateKey sk = (EdDSAPrivateKey) converter.getPrivateKey(pki);
				final EdDSAPublicKey pk = new EdDSAPublicKey(
						new EdDSAPublicKeySpec(sk.getA(), EdDSANamedCurveTable.ED_25519_CURVE_SPEC));
				kp = new KeyPair(pk, sk);
			} else {
				final PEMKeyPair ukp = (PEMKeyPair) rawObject;
				kp = converter.getKeyPair(ukp);
			}

			return kp;
		} catch (IOException | OperatorCreationException | PKCSException ex) {
			throw new KeyStoreException(ex);
		}
	}

	private void loadKeyPairs(final InputStream istream) throws KeyStoreException {
		clear();

		try (final PEMParser parser = new PEMParser(new InputStreamReader(istream))) {

			Object rawObject;
			while ((rawObject = parser.readObject()) != null) {
				add(decodeKeyPair(rawObject));
			}
		} catch (IOException ex) {
			throw new KeyStoreException(ex);
		}
	}

	private void loadFile(final File source) throws KeyStoreException {
		try (final FileInputStream fis = new FileInputStream(source)) {
			loadKeyPairs(fis);
		} catch (IOException ex) {
			throw new KeyStoreException(ex);
		}
	}

	private final SecureRandom random;
	private final JcaPEMKeyConverter converter;
	private final char[] password;

	private Ed25519KeyStore(final char[] password, final JcaPEMKeyConverter converter, final SecureRandom random) {
		this.password = password;
		this.converter = converter;
		this.random = random;
	}
}
