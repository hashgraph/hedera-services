package com.hedera.services.bdd.spec.persistence;

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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.keys.deterministic.Bip0032;
import com.hedera.services.bdd.spec.keys.deterministic.Bip0039;
import com.hedera.services.bdd.spec.keys.deterministic.Ed25519Factory;
import com.hedera.services.bdd.suites.utils.keypairs.Ed25519KeyStore;
import com.hederahashgraph.api.proto.java.Key;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.ShortBufferException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.SplittableRandom;
import java.util.function.BiConsumer;

import static org.apache.commons.codec.binary.Hex.encodeHexString;

public class SpecKey {
	static final Logger log = LogManager.getLogger(SpecKey.class);

	private static final String DEFAULT_PASSPHRASE = "swirlds";
	private static final String MISSING_LOC = null;
	private static final boolean GENERATE_IF_MISSING = true;

	boolean generateIfMissing = GENERATE_IF_MISSING;

	String pemLoc = MISSING_LOC;
	String passphrase = DEFAULT_PASSPHRASE;
	String mnemonicLoc = MISSING_LOC;

	public SpecKey() {
	}

	public static SpecKey prefixedAt(String pemLoc) {
		var key = new SpecKey();
		key.setPemLoc(pemLoc + ".pem");
		return key;
	}

	public static SpecKey prefixedMnemonicAt(String mnemonicLoc) {
		var key = new SpecKey();
		key.setMnemonicLoc(mnemonicLoc + ".words");
		return key;
	}

	public String getPassphrase() {
		return passphrase;
	}

	public void setPassphrase(String passphrase) {
		this.passphrase = passphrase;
	}

	public boolean isGenerateIfMissing() {
		return generateIfMissing;
	}

	public void setGenerateIfMissing(boolean generateIfMissing) {
		this.generateIfMissing = generateIfMissing;
	}

	public String getPemLoc() {
		return pemLoc;
	}

	public void setPemLoc(String pemLoc) {
		this.pemLoc = pemLoc;
	}

	public void setMnemonicLoc(String mnemonicLoc) {
		this.mnemonicLoc = mnemonicLoc;
	}

	public void registerWith(HapiApiSpec spec, RegistryForms forms) {
		if (pemLoc != MISSING_LOC) {
			registerPemWith(spec, forms);
		} else if (mnemonicLoc != MISSING_LOC) {
			registerMnemonic(spec, forms);
		} else {
			throw new IllegalStateException("Both PEM and mnemonic locations are missing!");
		}
	}

	private void registerMnemonic(HapiApiSpec spec, RegistryForms forms) {
		var qWordsLoc = qualifiedKeyLoc(mnemonicLoc, spec);
		var words = new File(qWordsLoc);
		String mnemonic = null;
		if (!words.exists()) {
			if (!generateIfMissing) {
				throw new IllegalStateException(String.format("File missing at mnemonic loc '%s'!", qWordsLoc));
			}
			mnemonic = randomMnemonic();
		} else {
			try {
				mnemonic = Files.readString(Paths.get(qWordsLoc));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		var cryptoKey = asEd25519Key(mnemonic);
		var grpcKey = Ed25519Factory.populatedFrom(cryptoKey.getAbyte());
		forms.completeIntake(spec.registry(), grpcKey);
		spec.keys().incorporate(
				forms.name(),
				encodeHexString(cryptoKey.getAbyte()),
				cryptoKey,
				SigControl.ON);
	}

	public static String randomMnemonic() {
		byte[] entropy = new byte[32];
		new SplittableRandom().nextBytes(entropy);
		try {
			return Bip0039.mnemonicFrom(entropy);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public static EdDSAPrivateKey asEd25519Key(String mnemonic) {
		try {
			return Ed25519Factory.ed25519From(Bip0032.privateKeyFrom(Bip0032.seedFrom(mnemonic)));
		} catch (NoSuchAlgorithmException | InvalidKeyException | ShortBufferException e) {
			throw new IllegalStateException(e);
		}
	}

	private void registerPemWith(HapiApiSpec spec, RegistryForms forms) {
		var qPemLoc = qualifiedKeyLoc(pemLoc, spec);
		var aes256EncryptedPkcs8Pem = new File(qPemLoc);
		if (!aes256EncryptedPkcs8Pem.exists()) {
			if (!generateIfMissing) {
				throw new IllegalStateException(String.format("File missing at PEM loc '%s'!", qPemLoc));
			}
			Key simpleKey = spec.keys().generate(KeyFactory.KeyType.SIMPLE);
			forms.completeIntake(spec.registry(), simpleKey);
			try {
				spec.keys().exportSimpleKey(qPemLoc, forms.name(), passphrase);
				log.info("Created new simple key at PEM loc '{}'.", qPemLoc);
			} catch (KeyStoreException e) {
				throw new IllegalStateException(String.format("Cannot generate key to PEM loc '%s'!", qPemLoc), e);
			}
			return;
		}

		var keyPair = readFirstKp(aes256EncryptedPkcs8Pem, passphrase);
		var publicKey = (EdDSAPublicKey) keyPair.getPublic();
		var hederaKey = asSimpleHederaKey(publicKey.getAbyte());
		forms.completeIntake(spec.registry(), hederaKey);
		/* When we incorporate the key into the spec's key factory, it will:
			(1) Update the mapping from hexed public keys to PrivateKeys; and,
			(2) Set the given SigControl as default for signing requests with the Key. */
		spec.keys().incorporate(
				forms.name(),
				encodeHexString(publicKey.getAbyte()),
				keyPair.getPrivate(),
				SigControl.ON);
	}

	private String qualifiedKeyLoc(String loc, HapiApiSpec spec) {
		return String.format("%s/keys/%s", spec.setup().persistentEntitiesDirPath(), loc);
	}

	public static KeyPair readFirstKp(File pem, String passphrase) {
		try {
			var keyStore = Ed25519KeyStore.read(passphrase.toCharArray(), pem);
			return keyStore.get(0);
		} catch (KeyStoreException kse) {
			throw new IllegalStateException(
					String.format("Unusable key at alleged PEM loc '%s'!", pem.getPath()), kse);
		}
	}

	private Key asSimpleHederaKey(byte[] A) {
		return Key.newBuilder()
				.setEd25519(ByteString.copyFrom(A))
				.build();
	}

	public static class RegistryForms {
		private String name;
		private BiConsumer<HapiSpecRegistry, Key> intake = (registry, key) -> registry.saveKey(name, key);

		private RegistryForms(String name) {
			this.name = name;
		}

		private RegistryForms(String name, BiConsumer<HapiSpecRegistry, Key> intake) {
			this.name = name;
			this.intake = intake;
		}

		public static RegistryForms under(String name) {
			return new RegistryForms(name);
		}

		public static RegistryForms asKycKeyFor(String token) {
			return new RegistryForms(kycKeyFor(token), (registry, key) -> registry.saveKycKey(token, key));
		}

		public static RegistryForms asWipeKeyFor(String token) {
			return new RegistryForms(wipeKeyFor(token), (registry, key) -> registry.saveWipeKey(token, key));
		}

		public static RegistryForms asSupplyKeyFor(String token) {
			return new RegistryForms(supplyKeyFor(token), (registry, key) -> registry.saveSupplyKey(token, key));
		}

		public static RegistryForms asFreezeKeyFor(String token) {
			return new RegistryForms(freezeKeyFor(token), (registry, key) -> registry.saveFreezeKey(token, key));
		}

		public static RegistryForms asAdminKeyFor(String entity) {
			return new RegistryForms(adminKeyFor(entity), (registry, key) -> registry.saveAdminKey(entity, key));
		}

		public String name() {
			return name;
		}

		public void completeIntake(HapiSpecRegistry registry, Key key) {
			intake.accept(registry, key);
		}
	}

	public static String kycKeyFor(String name) {
		return name + "Kyc";
	}

	public static String wipeKeyFor(String name) {
		return name + "Wipe";
	}

	public static String adminKeyFor(String name) {
		return name + "Admin";
	}

	public static String supplyKeyFor(String name) {
		return name + "Supply";
	}

	public static String freezeKeyFor(String name) {
		return name + "Freeze";
	}

	public static String submitKeyFor(String name) {
		return name + "Submit";
	}
}
