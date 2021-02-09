package com.hedera.services.bdd.suites.utils.keypairs;

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
import org.bouncycastle.openssl.PKCS8Generator;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.util.io.pem.PemGenerationException;
import org.bouncycastle.util.io.pem.PemObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.DrbgParameters;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
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

		public Builder withConverter(final JcaPEMKeyConverter converter) {
			this.converter = converter;
			return this;
		}

		public Builder withSecureRandom(final SecureRandom secureRandom) {
			this.random = secureRandom;
			return this;
		}

		public Ed25519KeyStore build() throws KeyStoreException {
			if (password == null) {
				password = new char[0];
			}
			if(converter == null) {
				this.converter = new JcaPEMKeyConverter().setProvider(ED_PROVIDER);
			}
			if(random == null) {
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

	/**
	 * Issue #139, load from a pem file without a password.
	 * @param source
	 * @return
	 * @throws KeyStoreException
	 */
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

	public static KeyPair createKeyPair() throws NoSuchAlgorithmException {
		final KeyPairGenerator generator = KeyPairGenerator.getInstance(EdDSAPrivateKey.KEY_ALGORITHM, ED_PROVIDER);
		return generator.generateKeyPair();
	}

	public static int getIndex(final String sourceFile) throws KeyStoreException{
		BufferedReader reader;
		try{
			reader = new BufferedReader(new FileReader(sourceFile));
			String line = reader.readLine();
			while (line!=null){
				if (line.contains("Index:")){
					String[] parsedLine = line.split(":");
					return Integer.parseInt(parsedLine[1].replace(" ", ""));
				}
				line = reader.readLine();
			}
		} catch (IOException e) {
			throw new KeyStoreException(e);
		}
		return -1;
	}

	public KeyPair insertNewKeyPair() throws KeyStoreException {
		try {
			final KeyPair kp = createKeyPair();
			this.add(kp);
			return kp;
		} catch (NoSuchAlgorithmException ex) {
			throw new KeyStoreException(ex);
		}
	}

	public KeyPair insertNewKeyPair(Ed25519PrivateKey privateKey) throws KeyStoreException{
		try {
			final KeyPair kp = EncryptionUtils.buildKeyPairFromMainnetPrivateKey(privateKey);
			this.add(kp);
			return kp;
		} catch (Exception e) {
			throw new KeyStoreException(e);
		}
	}

	public void write(final String destFile) throws KeyStoreException {
		write(new File(destFile));
	}

	public void write(final String destFile, long index, String version) throws KeyStoreException {
		try (FileOutputStream fos = new FileOutputStream(new File(destFile))) {
			write(fos, index, version);
		} catch (IOException ex) {
			throw new KeyStoreException(ex);
		}
	}

	public void write(final File dest) throws KeyStoreException {
		//make parent directory if it doesn't exists
		if (dest.getParentFile() != null) {
			dest.getParentFile().mkdirs();
		}
		try (FileOutputStream fos = new FileOutputStream(dest)) {
			write(fos);
		} catch (IOException ex) {
			throw new KeyStoreException(ex);
		}
	}

	public void write(final OutputStream ostream) throws KeyStoreException {
		try {
			if (isEmpty()) {
				throw new KeyStoreException("KeyStore is currently empty and cannot be persisted.");
			}

			final OutputEncryptor encryptor = (new JceOpenSSLPKCS8EncryptorBuilder(PKCS8Generator.AES_256_CBC))
					.setPRF(PKCS8Generator.PRF_HMACSHA384)
					.setIterationCount(10000)
					.setRandom(random)
					.setPasssword(password)
					.setProvider(BC_PROVIDER)
					.build();

			try (final JcaPEMWriter pemWriter = new JcaPEMWriter(new OutputStreamWriter(ostream))) {
				for (KeyPair kp : this) {
					pemWriter.writeObject(encodeKeyPair(kp, encryptor));
				}
				pemWriter.flush();
			}
		} catch (IOException | OperatorCreationException ex) {
			throw new KeyStoreException(ex);
		}
	}

	public void write(final OutputStream ostream, final long index, final String version) throws KeyStoreException {
		try {
			if (isEmpty()) {
				throw new KeyStoreException("KeyStore is currently empty and cannot be persisted.");
			}

			final OutputEncryptor encryptor = (new JceOpenSSLPKCS8EncryptorBuilder(PKCS8Generator.AES_256_CBC))
					.setPRF(PKCS8Generator.PRF_HMACSHA384)
					.setIterationCount(10000)
					.setRandom(random)
					.setPasssword(password)
					.setProvider(BC_PROVIDER)
					.build();

			try (final JcaPEMWriter pemWriter = new JcaPEMWriter(new OutputStreamWriter(ostream))) {
				pemWriter.write(String.format("Application: Hedera Transaction Tool\n"));
				pemWriter.write(String.format("%s\n", version));
				pemWriter.write(String.format("Index: %d\n",index));
				for (KeyPair kp : this) {
					pemWriter.writeObject(encodeKeyPair(kp, encryptor));
				}
				pemWriter.flush();
			}
		} catch (IOException | OperatorCreationException ex) {
			throw new KeyStoreException(ex);
		}
	}

	private PemObject encodeKeyPair(KeyPair keyPair, OutputEncryptor encryptor) throws KeyStoreException {
		try {
			return new JcaPKCS8Generator(keyPair.getPrivate(), encryptor).generate();
		} catch (PemGenerationException ex) {
			throw new KeyStoreException(ex);
		}
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
