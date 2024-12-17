/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.statedumpers.legacy;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Maps to proto Key. */
public abstract class JKey implements HederaKey {
    static final int MAX_KEY_DEPTH = 15;

    private static final byte[] MISSING_RSA_3072_KEY = new byte[0];
    private static final byte[] MISSING_ED25519_KEY = new byte[0];
    private static final byte[] MISSING_ECDSA_384_KEY = new byte[0];
    private static final byte[] MISSING_ECDSA_SECP256K1_KEY = new byte[0];

    private boolean forScheduledTxn = false;

    /**
     * Returns whether the given key denotes an immutable entity. (I.e., is exactly an empty key list.)
     * @param key the key to check
     * @return whether the key denotes an immutable entity
     */
    public static boolean denotesImmutableEntity(@NonNull final JKey key) {
        return key instanceof JKeyList keyList && keyList.isEmpty();
    }

    /**
     * Maps a proto Key to Jkey.
     *
     * @param key the proto Key to be converted
     * @return the generated JKey instance
     * @throws InvalidKeyException If the key is not a valid key type
     */
    public static JKey mapKey(Key key) throws InvalidKeyException {
        return convertKey(key, 1);
    }

    /**
     * Maps a proto Key to Jkey.
     *
     * @param key the proto Key to be converted
     * @return the generated JKey instance
     */
    public static JKey mapKey(@NonNull final com.hedera.hapi.node.base.Key key) throws InvalidKeyException {
        return convertKey(key, 1);
    }

    /**
     * Converts a key up to a given level of depth. Both the signature and the key may be complex
     * with multiple levels.
     *
     * @param key the current proto Key to be converted
     * @param depth current level that is to be verified. The first level has a value of 1.
     * @return the converted JKey instance
     * @throws InvalidKeyException If the key is not a valid key type or exceeds the allowable depth of nesting.
     */
    public static JKey convertKey(Key key, int depth) throws InvalidKeyException {
        if (depth > MAX_KEY_DEPTH) {
            throw new InvalidKeyException("Exceeding max expansion depth of " + MAX_KEY_DEPTH);
        }

        if (!(key.hasThresholdKey() || key.hasKeyList())) {
            return convertBasic(key);
        } else if (key.hasThresholdKey()) {
            List<Key> tKeys = key.getThresholdKey().getKeys().getKeysList();
            List<JKey> jkeys = new ArrayList<>();
            for (Key aKey : tKeys) {
                JKey res = convertKey(aKey, depth + 1);
                jkeys.add(res);
            }
            JKeyList keys = new JKeyList(jkeys);
            int thd = key.getThresholdKey().getThreshold();
            return new JThresholdKey(keys, thd);
        } else {
            List<Key> tKeys = key.getKeyList().getKeysList();
            List<JKey> jkeys = new ArrayList<>();
            for (Key aKey : tKeys) {
                JKey res = convertKey(aKey, depth + 1);
                jkeys.add(res);
            }
            return new JKeyList(jkeys);
        }
    }

    /**
     * Converts a key up to a given level of depth. Both the signature and the key may be complex
     * with multiple levels.
     *
     * @param key the current proto Key to be converted
     * @param depth current level that is to be verified. The first level has a value of 1.
     * @return the converted JKey instance
     * @throws InvalidKeyException If the key is not a valid key type or exceeds the allowable depth of nesting.
     */
    public static JKey convertKey(@NonNull final com.hedera.hapi.node.base.Key key, final int depth)
            throws InvalidKeyException {
        if (depth > MAX_KEY_DEPTH) {
            throw new InvalidKeyException("Exceeding max expansion depth of " + MAX_KEY_DEPTH);
        }

        if (!(key.hasThresholdKey() || key.hasKeyList())) {
            return convertBasic(key);
        }

        if (key.hasThresholdKey()) {
            final var thresholdKey = key.thresholdKeyOrThrow();
            List<com.hedera.hapi.node.base.Key> tKeys = thresholdKey.keys().keys();
            List<JKey> jkeys = new ArrayList<>();
            for (var aKey : tKeys) {
                JKey res = convertKey(aKey, depth + 1);
                jkeys.add(res);
            }
            JKeyList keys = new JKeyList(jkeys);
            int thd = thresholdKey.threshold();
            return new JThresholdKey(keys, thd);
        }

        final var keyList = key.keyListOrThrow();
        List<com.hedera.hapi.node.base.Key> tKeys = keyList.keys();
        List<JKey> jkeys = new ArrayList<>();
        for (var aKey : tKeys) {
            JKey res = convertKey(aKey, depth + 1);
            jkeys.add(res);
        }
        return new JKeyList(jkeys);
    }

    /**
     * Converts a basic key.
     *
     * @param key proto Key to be converted
     * @return the converted JKey instance
     * @throws InvalidKeyException If the key is not a valid key type
     */
    private static JKey convertBasic(Key key) throws InvalidKeyException {
        JKey rv;
        if (!key.getEd25519().isEmpty()) {
            byte[] pubKeyBytes = key.getEd25519().toByteArray();
            rv = new JEd25519Key(pubKeyBytes);
        } else if (!key.getECDSA384().isEmpty()) {
            byte[] pubKeyBytes = key.getECDSA384().toByteArray();
            rv = new JECDSA_384Key(pubKeyBytes);
        } else if (!key.getRSA3072().isEmpty()) {
            byte[] pubKeyBytes = key.getRSA3072().toByteArray();
            rv = new JRSA_3072Key(pubKeyBytes);
        } else if (key.getContractID().getContractNum() != 0) {
            rv = new JContractIDKey(key.getContractID());
        } else if (!key.getECDSASecp256K1().isEmpty()) {
            byte[] pubKeyBytes = key.getECDSASecp256K1().toByteArray();
            rv = new JECDSASecp256k1Key(pubKeyBytes);
        } else if (key.getDelegatableContractId().getContractNum() != 0) {
            rv = new JDelegatableContractIDKey(key.getDelegatableContractId());
        } else if (!key.getContractID().getEvmAddress().isEmpty()) {
            rv = new JContractAliasKey(key.getContractID());
        } else if (!key.getDelegatableContractId().getEvmAddress().isEmpty()) {
            rv = new JDelegatableContractAliasKey(key.getDelegatableContractId());
        } else {
            throw new InvalidKeyException("Key type not implemented: key=" + key);
        }

        return rv;
    }

    /**
     * Converts a basic key.
     *
     * @param key proto Key to be converted
     * @return the converted JKey instance
     * @throws InvalidKeyException If the key is not a valid key type
     */
    private static JKey convertBasic(final com.hedera.hapi.node.base.Key key) throws InvalidKeyException {
        final var oneOf = key.key();
        return switch (oneOf.kind()) {
            case ED25519 -> {
                byte[] pubKeyBytes = asBytes(oneOf.as());
                yield new JEd25519Key(pubKeyBytes);
            }
            case ECDSA_384 -> {
                byte[] pubKeyBytes = asBytes(oneOf.as());
                yield new JECDSA_384Key(pubKeyBytes);
            }
            case RSA_3072 -> {
                byte[] pubKeyBytes = asBytes(oneOf.as());
                yield new JRSA_3072Key(pubKeyBytes);
            }
            case ECDSA_SECP256K1 -> {
                byte[] pubKeyBytes = asBytes(oneOf.as());
                yield new JECDSASecp256k1Key(pubKeyBytes);
            }
            case CONTRACT_ID -> {
                final ContractID id = oneOf.as();
                if (id.hasContractNum()) {
                    yield new JContractIDKey(id.shardNum(), id.realmNum(), id.contractNumOrThrow());
                } else if (id.hasEvmAddress()) {
                    final var proto = com.hederahashgraph.api.proto.java.ContractID.newBuilder()
                            .setShardNum(id.shardNum())
                            .setRealmNum(id.realmNum())
                            .setEvmAddress(ByteString.copyFrom(asBytes(id.evmAddressOrThrow())))
                            .build();
                    yield new JContractIDKey(proto);
                } else {
                    throw new InvalidKeyException("Unable to decode contract key=" + key);
                }
            }
            case DELEGATABLE_CONTRACT_ID -> {
                final ContractID id = oneOf.as();
                if (id.hasContractNum()) {
                    yield new JDelegatableContractIDKey(id.shardNum(), id.realmNum(), id.contractNumOrThrow());
                } else if (id.hasEvmAddress()) {
                    final var proto = com.hederahashgraph.api.proto.java.ContractID.newBuilder()
                            .setShardNum(id.shardNum())
                            .setRealmNum(id.realmNum())
                            .setEvmAddress(ByteString.copyFrom(asBytes(id.evmAddressOrThrow())))
                            .build();
                    yield new JDelegatableContractIDKey(proto);
                } else {
                    throw new InvalidKeyException("Unable to decode contract key=" + key);
                }
            }
            default -> throw new InvalidKeyException("Key type not implemented: key=" + key);
        };
    }

    /**
     * Converts a basic JKey to proto Key.
     *
     * @param jkey JKey object to be converted
     * @return the converted proto Key instance
     * @throws InvalidKeyException If the key is not a valid key type
     */
    static Key convertJKeyBasic(JKey jkey) throws InvalidKeyException {
        Key rv;
        if (jkey.hasEd25519Key()) {
            rv = Key.newBuilder()
                    .setEd25519(ByteString.copyFrom(jkey.getEd25519()))
                    .build();
        } else if (jkey.hasECDSA384Key()) {
            rv = Key.newBuilder()
                    .setECDSA384(ByteString.copyFrom(jkey.getECDSA384()))
                    .build();
        } else if (jkey.hasRSA3072Key()) {
            rv = Key.newBuilder()
                    .setRSA3072(ByteString.copyFrom(jkey.getRSA3072()))
                    .build();
        } else if (jkey.hasContractID()) {
            rv = Key.newBuilder()
                    .setContractID(jkey.getContractIDKey().getContractID())
                    .build();
        } else if (jkey.hasECDSAsecp256k1Key()) {
            rv = Key.newBuilder()
                    .setECDSASecp256K1(ByteString.copyFrom(jkey.getECDSASecp256k1Key()))
                    .build();
        } else if (jkey.hasDelegatableContractId()) {
            rv = Key.newBuilder()
                    .setDelegatableContractId(jkey.getDelegatableContractIdKey().getContractID())
                    .build();
        } else if (jkey.hasContractAlias()) {
            rv = Key.newBuilder()
                    .setContractID(jkey.getContractAliasKey().getContractID())
                    .build();
        } else if (jkey.hasDelegatableContractAlias()) {
            rv = Key.newBuilder()
                    .setDelegatableContractId(
                            jkey.getDelegatableContractAliasKey().getContractID())
                    .build();
        } else {
            // Warning: Do Not allow anything that calls toString, equals, or hashCode on JKey here.
            //          Object.toString calls hashCode, and equals and hashCode both call this method
            //          so you would create an infinite recursion.
            throw new InvalidKeyException("Key type not implemented.");
        }
        return rv;
    }

    /**
     * Converts a JKey to proto Key for up to a given level of depth.
     *
     * @param jkey the current JKey to be converted
     * @param depth current level that is to be verified. The first level has a value of 1.
     * @return the converted proto Key instance
     * @throws InvalidKeyException If the key is not a valid key type or exceeds the allowable depth of nesting.
     */
    public static Key convertJKey(JKey jkey, int depth) throws InvalidKeyException {
        if (depth > MAX_KEY_DEPTH) {
            throw new InvalidKeyException("Exceeding max expansion depth of " + MAX_KEY_DEPTH);
        }
        if (jkey.isEmpty()) {
            return jkey.convertJKeyEmpty();
        } else if (!(jkey.hasThresholdKey() || jkey.hasKeyList())) {
            return convertJKeyBasic(jkey);
        } else if (jkey.hasThresholdKey()) {
            List<JKey> jKeys = jkey.getThresholdKey().getKeys().getKeysList();
            List<Key> tkeys = new ArrayList<>();
            for (JKey aKey : jKeys) {
                Key res = convertJKey(aKey, depth + 1);
                tkeys.add(res);
            }
            KeyList keys = KeyList.newBuilder().addAllKeys(tkeys).build();
            int thd = jkey.getThresholdKey().getThreshold();
            Key result = Key.newBuilder()
                    .setThresholdKey(ThresholdKey.newBuilder().setKeys(keys).setThreshold(thd))
                    .build();
            return result;
        } else if (jkey.hasKeyList()) {
            List<JKey> jKeys = jkey.getKeyList().getKeysList();
            List<Key> tkeys = new ArrayList<>();
            for (JKey aKey : jKeys) {
                Key res = convertJKey(aKey, depth + 1);
                tkeys.add(res);
            }
            KeyList keys = KeyList.newBuilder().addAllKeys(tkeys).build();
            return Key.newBuilder().setKeyList(keys).build();
        } else {
            return Key.newBuilder().build();
        }
    }

    /**
     * Convert an empty JKey to an appropriate empty Key.
     * Typically this just creates a new Key, but subclasses may override with specific behavior.
     * @return An empty Key.
     */
    protected Key convertJKeyEmpty() {
        return Key.newBuilder().build();
    }

    public static boolean equalUpToDecodability(JKey a, JKey b) {
        Key aKey = null;
        Key bKey = null;
        try {
            aKey = mapJKey(a);
        } catch (Exception ignore) {
        }
        try {
            bKey = mapJKey(b);
        } catch (Exception ignore) {
        }
        return Objects.equals(aKey, bKey);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        try {
            return Objects.equals(mapJKey(this), mapJKey((JKey) other));
        } catch (InvalidKeyException ignore) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        try {
            return Objects.hashCode(mapJKey(this));
        } catch (InvalidKeyException ignore) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Maps a JKey instance to a proto Key instance.
     *
     * @param jkey the JKey to be converted
     * @return the converted proto Key instance
     * @throws InvalidKeyException If the key is not a valid key type
     */
    public static Key mapJKey(JKey jkey) throws InvalidKeyException {
        return convertJKey(jkey, 1);
    }

    public byte[] serialize() throws IOException {
        return JKeySerializer.serialize(this);
    }

    public abstract boolean isEmpty();

    /**
     * Expected to return {@code false} if the key is empty
     *
     * @return whether the key is valid
     */
    public abstract boolean isValid();

    public void setForScheduledTxn(boolean flag) {
        forScheduledTxn = flag;
    }

    public boolean isForScheduledTxn() {
        return forScheduledTxn;
    }

    public boolean hasEd25519Key() {
        return false;
    }

    public boolean hasWildcardECDSAKey() {
        return false;
    }

    public boolean hasECDSA384Key() {
        return false;
    }

    public boolean hasECDSAsecp256k1Key() {
        return false;
    }

    public boolean hasRSA3072Key() {
        return false;
    }

    public boolean hasKeyList() {
        return false;
    }

    public boolean hasThresholdKey() {
        return false;
    }

    public boolean hasContractID() {
        return false;
    }

    public boolean hasContractAlias() {
        return false;
    }

    public boolean hasDelegatableContractAlias() {
        return false;
    }

    public boolean hasDelegatableContractId() {
        return false;
    }

    public JContractIDKey getContractIDKey() {
        return null;
    }

    public JWildcardECDSAKey getWildcardECDSAKey() {
        return null;
    }

    public JContractAliasKey getContractAliasKey() {
        return null;
    }

    public JDelegatableContractAliasKey getDelegatableContractAliasKey() {
        return null;
    }

    public JDelegatableContractIDKey getDelegatableContractIdKey() {
        return null;
    }

    public JThresholdKey getThresholdKey() {
        return null;
    }

    public JKeyList getKeyList() {
        return null;
    }

    public byte[] getEd25519() {
        return MISSING_ED25519_KEY;
    }

    public byte[] getECDSA384() {
        return MISSING_ECDSA_384_KEY;
    }

    public byte[] getECDSASecp256k1Key() {
        return MISSING_ECDSA_SECP256K1_KEY;
    }

    public byte[] getRSA3072() {
        return MISSING_RSA_3072_KEY;
    }

    public JKey duplicate() {
        try {
            var buf = serialize();
            try (var bs = new ByteArrayInputStream(buf)) {
                try (var is = new SerializableDataInputStream(bs)) {
                    return JKeySerializer.deserialize(is);
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    public byte[] primitiveKeyIfPresent() {
        if (hasEd25519Key()) {
            return getEd25519();
        } else if (hasECDSAsecp256k1Key()) {
            return getECDSASecp256k1Key();
        } else {
            return MISSING_ECDSA_SECP256K1_KEY;
        }
    }

    private static byte[] asBytes(Bytes b) {
        final var buf = new byte[(int) b.length()];
        b.getBytes(0, buf);
        return buf;
    }
}
