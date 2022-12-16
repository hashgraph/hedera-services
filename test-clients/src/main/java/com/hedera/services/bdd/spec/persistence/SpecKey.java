/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.persistence;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.keys.Ed25519Utils;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.keys.deterministic.Bip0032;
import com.hedera.services.bdd.spec.keys.deterministic.Bip0039;
import com.hedera.services.bdd.spec.keys.deterministic.Ed25519Factory;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.utility.CommonUtils;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.BiConsumer;
import javax.crypto.ShortBufferException;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SpecKey {
    static final Logger log = LogManager.getLogger(SpecKey.class);

    private static final String DEFAULT_PASSPHRASE = "swirlds";
    private static final String MISSING_LOC = null;
    private static final boolean GENERATE_IF_MISSING = true;

    boolean generateIfMissing = GENERATE_IF_MISSING;

    String pemLoc = MISSING_LOC;
    String passphrase = DEFAULT_PASSPHRASE;
    String wordsLoc = MISSING_LOC;

    public SpecKey() {}

    public static SpecKey prefixedAt(String pemLoc) {
        var key = new SpecKey();
        key.setPemLoc(pemLoc + ".pem");
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

    public String getWordsLoc() {
        return wordsLoc;
    }

    public void setWordsLoc(String wordsLoc) {
        this.wordsLoc = wordsLoc;
    }

    public void registerWith(HapiSpec spec, RegistryForms forms) {
        if (pemLoc != MISSING_LOC) {
            registerPemWith(spec, forms);
        } else if (wordsLoc != MISSING_LOC) {
            passphrase = null;
            registerMnemonicWith(spec, forms);
        } else {
            throw new IllegalStateException("Both PEM and mnemonic locations are missing!");
        }
    }

    private void registerMnemonicWith(HapiSpec spec, RegistryForms forms) {
        var qWordsLoc = qualifiedKeyLoc(wordsLoc, spec);
        var words = new File(qWordsLoc);
        String mnemonic;
        if (!words.exists()) {
            if (!generateIfMissing) {
                throw new IllegalStateException(
                        String.format("File missing at mnemonic loc '%s'!", qWordsLoc));
            }
            mnemonic = randomMnemonic();
            try {
                Files.write(Paths.get(qWordsLoc), List.of(mnemonic));
                log.info("Created new simple key at mnemonic loc '{}'.", qWordsLoc);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            mnemonic = KeyFactory.mnemonicFromFile(qWordsLoc);
        }
        var cryptoKey = mnemonicToEd25519Key(mnemonic);
        var grpcKey = Ed25519Factory.populatedFrom(cryptoKey.getAbyte());
        forms.completeIntake(spec.registry(), grpcKey);
        spec.keys()
                .incorporate(
                        forms.name(),
                        CommonUtils.hex(cryptoKey.getAbyte()),
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

    public static EdDSAPrivateKey mnemonicToEd25519Key(String mnemonic) {
        try {
            return Ed25519Factory.ed25519From(Bip0032.privateKeyFrom(Bip0032.seedFrom(mnemonic)));
        } catch (NoSuchAlgorithmException | InvalidKeyException | ShortBufferException e) {
            throw new IllegalStateException(e);
        }
    }

    private void registerPemWith(HapiSpec spec, RegistryForms forms) {
        var qPemLoc = qualifiedKeyLoc(pemLoc, spec);
        var aes256EncryptedPkcs8Pem = new File(qPemLoc);
        if (!aes256EncryptedPkcs8Pem.exists()) {
            if (!generateIfMissing) {
                throw new IllegalStateException(
                        String.format("File missing at PEM loc '%s'!", qPemLoc));
            }
            Key simpleKey = spec.keys().generate(spec, KeyFactory.KeyType.SIMPLE);
            forms.completeIntake(spec.registry(), simpleKey);
            spec.keys().exportSimpleKey(qPemLoc, forms.name(), passphrase);
            log.info("Created new simple key at PEM loc '{}'.", qPemLoc);
            return;
        }

        var keyPair = readFirstKpFromPem(aes256EncryptedPkcs8Pem, passphrase);
        var publicKey = (EdDSAPublicKey) keyPair.getPublic();
        var hederaKey = asSimpleHederaKey(publicKey.getAbyte());
        forms.completeIntake(spec.registry(), hederaKey);
        /* When we incorporate the key into the spec's key factory, it will:
        (1) Update the mapping from hexed public keys to PrivateKeys; and,
        (2) Set the given SigControl as default for signing requests with the Key. */
        spec.keys()
                .incorporate(
                        forms.name(),
                        CommonUtils.hex(publicKey.getAbyte()),
                        keyPair.getPrivate(),
                        SigControl.ON);
    }

    private String qualifiedKeyLoc(String loc, HapiSpec spec) {
        return String.format(
                "%s/%s/%s", spec.setup().persistentEntitiesDir(), EntityManager.KEYS_SUBDIR, loc);
    }

    public static KeyPair readFirstKpFromPem(File pem, String passphrase) {
        return Ed25519Utils.readKeyPairFrom(pem, passphrase);
    }

    private Key asSimpleHederaKey(byte[] A) {
        return Key.newBuilder().setEd25519(ByteString.copyFrom(A)).build();
    }

    public static class RegistryForms {
        private String name;
        private BiConsumer<HapiSpecRegistry, Key> intake =
                (registry, key) -> registry.saveKey(name, key);

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
            return new RegistryForms(
                    kycKeyFor(token), (registry, key) -> registry.saveKycKey(token, key));
        }

        public static RegistryForms asWipeKeyFor(String token) {
            return new RegistryForms(
                    wipeKeyFor(token), (registry, key) -> registry.saveWipeKey(token, key));
        }

        public static RegistryForms asSupplyKeyFor(String token) {
            return new RegistryForms(
                    supplyKeyFor(token), (registry, key) -> registry.saveSupplyKey(token, key));
        }

        public static RegistryForms asFreezeKeyFor(String token) {
            return new RegistryForms(
                    freezeKeyFor(token), (registry, key) -> registry.saveFreezeKey(token, key));
        }

        public static RegistryForms asAdminKeyFor(String entity) {
            return new RegistryForms(
                    adminKeyFor(entity), (registry, key) -> registry.saveAdminKey(entity, key));
        }

        public static RegistryForms asPauseKeyFor(String token) {
            return new RegistryForms(
                    pauseKeyFor(token), (registry, key) -> registry.savePauseKey(token, key));
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

    public static String pauseKeyFor(String name) {
        return name + "Pause";
    }
}
