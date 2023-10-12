/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.keys;

import static com.hedera.services.bdd.spec.keys.DefaultKeyGen.DEFAULT_KEY_GEN;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.keys.SigMapGenerator.Nature.UNIQUE_PREFIXES;
import static com.hedera.services.bdd.spec.persistence.SpecKey.mnemonicToEd25519Key;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asContractId;
import static java.util.Map.Entry;
import static java.util.stream.Collectors.toList;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.node.app.hapi.utils.SignatureGenerator;
import com.hedera.node.app.hapi.utils.keys.Ed25519Utils;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Transaction;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.junit.jupiter.api.Assertions;

public class KeyFactory implements Serializable {
    public static String PEM_PASSPHRASE = "swirlds";
    private static final long serialVersionUID = 1L;
    private static final Logger log = LogManager.getLogger(KeyFactory.class);

    public enum KeyType {
        SIMPLE,
        LIST,
        THRESHOLD
    }

    private final HapiSpecSetup setup;
    private final Map<String, PrivateKey> pkMap = new ConcurrentHashMap<>();
    private final Map<Key, SigControl> controlMap = new ConcurrentHashMap<>();
    private final SigMapGenerator defaultSigMapGen = TrieSigMapGenerator.withNature(UNIQUE_PREFIXES);

    private final transient HapiSpecRegistry registry;

    public KeyFactory(final HapiSpecSetup setup, final HapiSpecRegistry registry) throws Exception {
        this.setup = setup;
        this.registry = registry;

        final var genesisKey = payerKey(setup);
        incorporate(setup.genesisAccountName(), genesisKey, KeyShape.listSigs(ON));
    }

    public void exportSimpleKey(final String loc, final String name) {
        exportSimpleKey(loc, name, key -> key.getEd25519().toByteArray());
    }

    public void exportSimpleKey(final String loc, final String name, final String passphrase) {
        exportSimpleKey(loc, name, key -> key.getEd25519().toByteArray(), passphrase);
    }

    public void exportEcdsaKey(final String name) {
        exportSimpleEcdsaKey(name, key -> key.getECDSASecp256K1().toByteArray());
    }

    public void exportSimpleWacl(final String loc, final String name) {
        exportSimpleKey(
                loc, name, key -> key.getKeyList().getKeys(0).getEd25519().toByteArray());
    }

    public void exportSimpleKey(final String loc, final String name, final Function<Key, byte[]> targetKeyExtractor) {
        exportSimpleKey(loc, name, targetKeyExtractor, PEM_PASSPHRASE);
    }

    public void exportSimpleKey(
            final String loc,
            final String name,
            final Function<Key, byte[]> targetKeyExtractor,
            final String passphrase) {
        final var pubKeyBytes = targetKeyExtractor.apply(registry.getKey(name));
        final var hexedPubKey = com.swirlds.common.utility.CommonUtils.hex(pubKeyBytes);
        final var key = (EdDSAPrivateKey) pkMap.get(hexedPubKey);
        Ed25519Utils.writeKeyTo(key, loc, passphrase);
    }

    public void exportSimpleEcdsaKey(final String name, final Function<Key, byte[]> targetKeyExtractor) {
        final var pubKeyBytes = targetKeyExtractor.apply(registry.getKey(name));
        final var hexedPubKey = com.swirlds.common.utility.CommonUtils.hex(pubKeyBytes);
        final var key = (ECPrivateKey) pkMap.get(hexedPubKey);
        final var loc = explicitEcdsaLocFor(name);
        try {
            Files.writeString(Paths.get(loc), hexedPubKey + "|" + key.getS().toString(16));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String explicitEcdsaLocFor(final String name) {
        return name + "-secp256k1.hexed";
    }

    public void incorporate(final String byName, final EdDSAPrivateKey key) {
        incorporate(byName, key, ON);
    }

    public void incorporate(final String byName, final EdDSAPrivateKey key, final SigControl control) {
        final var pubKeyHex = com.swirlds.common.utility.CommonUtils.hex(key.getAbyte());
        pkMap.put(pubKeyHex, key);
        controlMap.put(registry.getKey(byName), control);
    }

    public void incorporateEd25519SimpleWacl(final String byName, final EdDSAPrivateKey key) {
        final var pubKeyHex = com.swirlds.common.utility.CommonUtils.hex(key.getAbyte());
        incorporate(byName, pubKeyHex, key, KeyShape.listOf(1));
    }

    public void incorporate(
            final String byName, final String pubKeyHex, final PrivateKey privateKey, final SigControl control) {
        pkMap.put(pubKeyHex, privateKey);
        controlMap.put(registry.getKey(byName), control);
    }

    public SigControl controlFor(final Key key) {
        return controlMap.get(key);
    }

    public void setControl(final Key key, final SigControl control) {
        controlMap.put(key, control);
    }

    public int controlledKeyCount(final Key key, final Map<Key, SigControl> overrides) {
        return asAuthor(key, overrides).getValue().numSimpleKeys();
    }

    public Transaction sign(
            final HapiSpec spec,
            final Transaction.Builder txn,
            final List<Key> keys,
            final Map<Key, SigControl> overrides)
            throws Throwable {
        return sign(spec, txn, defaultSigMapGen, authorsFor(keys, overrides));
    }

    public Transaction sign(
            final HapiSpec spec,
            final Transaction.Builder txn,
            final List<Key> keys,
            final Map<Key, SigControl> overrides,
            final SigMapGenerator sigMapGen)
            throws Throwable {
        return sign(spec, txn, sigMapGen, authorsFor(keys, overrides));
    }

    public List<Entry<Key, SigControl>> authorsFor(final List<Key> keys, final Map<Key, SigControl> overrides) {
        return keys.stream().map(k -> asAuthor(k, overrides)).collect(toList());
    }

    private Entry<Key, SigControl> asAuthor(final Key key, final Map<Key, SigControl> overrides) {
        final SigControl control = overrides.getOrDefault(key, controlMap.get(key));

        if (control == null) {
            throw new IllegalArgumentException("No sig control for key " + key);
        }
        if (!control.appliesTo(key)) {
            throw new IllegalStateException("Control " + control + " for key " + key + " doesn't apply");
        }

        return Pair.of(key, control);
    }

    private Transaction sign(
            final HapiSpec spec,
            final Transaction.Builder txn,
            final SigMapGenerator sigMapGen,
            final List<Entry<Key, SigControl>> authors)
            throws Throwable {
        final var signing = new PrimitiveSigning(CommonUtils.extractTransactionBodyBytes(txn), authors);

        final var primitiveSigs = signing.completed();
        final var sigMap = sigMapGen.forPrimitiveSigs(spec, primitiveSigs);

        txn.setSigMap(sigMap);

        return txn.build();
    }

    public Transaction signWithFullPrefixEd25519Keys(final Transaction.Builder txn, final List<Key> keys)
            throws IOException, GeneralSecurityException {
        final List<Entry<Key, SigControl>> authors = keys.stream()
                .<Entry<Key, SigControl>>map(k -> Pair.of(k, SigControl.ED25519_ON))
                .toList();
        final var signing = new PrimitiveSigning(CommonUtils.extractTransactionBodyBytes(txn), authors);
        final var primitiveSigs = signing.completed();
        final var sigMap = SignatureMap.newBuilder();
        for (final var sig : primitiveSigs) {
            sigMap.addSigPair(SignaturePair.newBuilder()
                    .setPubKeyPrefix(ByteString.copyFrom(sig.getKey()))
                    .setEd25519(ByteString.copyFrom(sig.getValue())));
        }
        txn.setSigMap(sigMap);
        return txn.build();
    }

    public class PrimitiveSigning {
        private byte[] keccak256Digest;

        private final byte[] data;
        private final Set<String> used = new HashSet<>();
        private final List<Entry<Key, SigControl>> authors;
        private final List<Entry<byte[], byte[]>> keySigs = new ArrayList<>();

        public PrimitiveSigning(final byte[] data, final List<Entry<Key, SigControl>> authors) {
            this.data = data;
            this.authors = authors;
        }

        public List<Entry<byte[], byte[]>> completed() throws GeneralSecurityException {
            for (final var author : authors) {
                signRecursively(author.getKey(), author.getValue());
            }
            return keySigs;
        }

        private void signRecursively(final Key key, final SigControl controller) throws GeneralSecurityException {
            switch (controller.getNature()) {
                case SIG_OFF:
                case CONTRACT_ID:
                case DELEGATABLE_CONTRACT_ID:
                    break;
                case SIG_ON:
                    signIfNecessary(key);
                    break;
                default:
                    final KeyList composite = getCompositeList(key);
                    final SigControl[] childControls = controller.getChildControls();
                    for (int i = 0; i < childControls.length; i++) {
                        signRecursively(composite.getKeys(i), childControls[i]);
                    }
            }
        }

        private void signIfNecessary(final Key key) throws GeneralSecurityException {
            final var pk = extractPubKey(key);
            final var hexedPk = com.swirlds.common.utility.CommonUtils.hex(pk);
            if (!used.contains(hexedPk)) {
                final var privateKey = pkMap.get(hexedPk);
                final byte[] sig;
                if (privateKey instanceof ECPrivateKey) {
                    if (keccak256Digest == null) {
                        keccak256Digest = new Keccak.Digest256().digest(data);
                    }
                    sig = SignatureGenerator.signBytes(keccak256Digest, privateKey);
                } else {
                    sig = SignatureGenerator.signBytes(data, privateKey);
                }
                keySigs.add(new AbstractMap.SimpleEntry<>(pk, sig));
                used.add(hexedPk);
            }
        }

        private byte[] extractPubKey(final Key key) {
            if (!key.getECDSASecp256K1().isEmpty()) {
                return key.getECDSASecp256K1().toByteArray();
            } else if (!key.getEd25519().isEmpty()) {
                return key.getEd25519().toByteArray();
            } else {
                throw new IllegalArgumentException("No supported public key in " + key);
            }
        }
    }

    public ECPrivateKey getPrivateKey(final String pubKeyHex) {
        return (ECPrivateKey) pkMap.get(pubKeyHex);
    }

    public static KeyList getCompositeList(final Key key) {
        return key.hasKeyList() ? key.getKeyList() : key.getThresholdKey().getKeys();
    }

    public static EdDSAPrivateKey payerKey(final HapiSpecSetup setup) {
        if (StringUtils.isNotEmpty(setup.defaultPayerKey())) {
            return Ed25519Utils.keyFrom(com.swirlds.common.utility.CommonUtils.unhex(setup.defaultPayerKey()));
        } else if (StringUtils.isNotEmpty(setup.defaultPayerMnemonic())) {
            return mnemonicToEd25519Key(setup.defaultPayerMnemonic());
        } else if (StringUtils.isNotEmpty(setup.defaultPayerMnemonicFile())) {
            final var mnemonic = mnemonicFromFile(setup.defaultPayerMnemonicFile());
            return mnemonicToEd25519Key(mnemonic);
        } else {
            return Ed25519Utils.readKeyFrom(setup.defaultPayerPemKeyLoc(), setup.defaultPayerPemKeyPassphrase());
        }
    }

    public static String mnemonicFromFile(final String wordsLoc) {
        try {
            return java.nio.file.Files.lines(Paths.get(wordsLoc))
                    .map(String::strip)
                    .collect(Collectors.joining(" "))
                    .strip();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized Key generateSubjectTo(
            final HapiSpec spec, final SigControl controller, final KeyGenerator keyGen) {
        return new Generation(spec, controller, keyGen).outcome();
    }

    public synchronized Key generateSubjectTo(
            final HapiSpec spec, final SigControl controller, final KeyGenerator keyGen, final KeyLabel labels) {
        return new Generation(spec, controller, keyGen, labels).outcome();
    }

    private class Generation {
        private final SigControl.KeyAlgo[] algoChoices = {SigControl.KeyAlgo.ED25519, SigControl.KeyAlgo.SECP256K1};

        private final KeyLabel labels;
        private final SigControl control;
        private final HapiSpec spec;
        private final KeyGenerator keyGen;
        private final Map<String, Key> byLabel = new HashMap<>();

        private int nextUnspecifiedAlgo = 0;

        public Generation(final HapiSpec spec, final SigControl control, final KeyGenerator keyGen) {
            this(spec, control, keyGen, KeyLabel.uniquelyLabeling(control));
        }

        public Generation(
                final HapiSpec spec, final SigControl control, final KeyGenerator keyGen, final KeyLabel labels) {
            this.spec = spec;
            this.labels = labels;
            this.control = control;
            this.keyGen = keyGen;
        }

        public Key outcome() {
            return generate(control, labels, true);
        }

        private Key generate(final SigControl sc, final KeyLabel label, final boolean saving) {
            final Key generated;

            switch (sc.getNature()) {
                case PREDEFINED:
                    generated = registry.getKey(sc.predefined());
                    break;
                case CONTRACT_ID:
                    final var cid = asContractId(sc.contract(), spec);
                    generated = Key.newBuilder().setContractID(cid).build();
                    break;
                case DELEGATABLE_CONTRACT_ID:
                    final var dcid = asContractId(sc.delegatableContract(), spec);
                    generated = Key.newBuilder().setDelegatableContractId(dcid).build();
                    break;
                case LIST:
                    generated = Key.newBuilder()
                            .setKeyList(composing(label.getConstituents(), sc.getChildControls()))
                            .build();
                    break;
                case THRESHOLD:
                    final var tKey = ThresholdKey.newBuilder()
                            .setThreshold(sc.getThreshold())
                            .setKeys(composing(label.getConstituents(), sc.getChildControls()))
                            .build();
                    generated = Key.newBuilder().setThresholdKey(tKey).build();
                    break;
                default:
                    if (byLabel.containsKey(label.literally())) {
                        generated = byLabel.get(label.literally());
                    } else {
                        final SigControl.KeyAlgo choice;
                        if (sc.keyAlgo() == SigControl.KeyAlgo.UNSPECIFIED) {
                            final var defaultAlgo = spec.setup().defaultKeyAlgo();
                            if (defaultAlgo != SigControl.KeyAlgo.UNSPECIFIED) {
                                choice = defaultAlgo;
                            } else {
                                /* A spec run with unspecified default algorithm alternates between Ed25519 and ECDSA */
                                choice = algoChoices[nextUnspecifiedAlgo];
                                nextUnspecifiedAlgo = (nextUnspecifiedAlgo + 1) % algoChoices.length;
                            }
                        } else {
                            choice = sc.keyAlgo();
                        }
                        generated = generateByAlgo(choice);
                        byLabel.put(label.literally(), generated);
                    }
                    break;
            }
            if (saving) {
                controlMap.put(generated, sc);
            }
            return generated;
        }

        private Key generateByAlgo(final SigControl.KeyAlgo algo) {
            if (algo == SigControl.KeyAlgo.ED25519) {
                return keyGen.genEd25519AndUpdateMap(pkMap);
            } else if (algo == SigControl.KeyAlgo.SECP256K1) {
                return keyGen.genEcdsaSecp256k1AndUpdate(pkMap);
            } else {
                throw new IllegalArgumentException(algo + " not supported");
            }
        }

        private KeyList composing(final KeyLabel[] ls, final SigControl[] cs) {
            Assertions.assertEquals(ls.length, cs.length, "Incompatible ls and cs!");
            final int N = ls.length;
            return KeyList.newBuilder()
                    .addAllKeys(IntStream.range(0, N)
                            .mapToObj(i -> generate(cs[i], ls[i], false))
                            .collect(toList()))
                    .build();
        }
    }

    public Key generate(final HapiSpec spec, final KeyType type) {
        return generate(spec, type, DEFAULT_KEY_GEN);
    }

    public Key generate(final HapiSpec spec, final KeyType type, final KeyGenerator keyGen) {
        switch (type) {
            case THRESHOLD:
                return generateSubjectTo(
                        spec,
                        KeyShape.threshSigs(
                                setup.defaultThresholdM(),
                                IntStream.range(0, setup.defaultThresholdN())
                                        .mapToObj(ignore -> SigControl.ON)
                                        .toArray(SigControl[]::new)),
                        keyGen);
            case LIST:
                return generateSubjectTo(
                        spec,
                        KeyShape.listSigs(IntStream.range(0, setup.defaultListN())
                                .mapToObj(ignore -> SigControl.ON)
                                .toArray(SigControl[]::new)),
                        keyGen);
            default:
                return generateSubjectTo(spec, ON, keyGen);
        }
    }
}
