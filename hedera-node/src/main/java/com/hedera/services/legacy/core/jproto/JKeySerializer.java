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
package com.hedera.services.legacy.core.jproto;

import com.hedera.services.state.serdes.IoUtils;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/** Custom Serializer for JKey structure. */
public class JKeySerializer {
    private static final long LEGACY_VERSION = 1;
    private static final long BPACK_VERSION = 2;

    private JKeySerializer() {}

    public static byte[] serialize(Object rootObject) throws IOException {
        return IoUtils.byteStream(
                buffer -> {
                    buffer.writeLong(BPACK_VERSION);

                    JObjectType objectType = JObjectType.FC_KEY;

                    if (rootObject instanceof JKeyList) {
                        objectType = JObjectType.FC_KEY_LIST;
                    } else if (rootObject instanceof JThresholdKey) {
                        objectType = JObjectType.FC_THRESHOLD_KEY;
                    } else if (rootObject instanceof JEd25519Key) {
                        objectType = JObjectType.FC_ED25519_KEY;
                    } else if (rootObject instanceof JECDSASecp256k1Key) {
                        objectType = JObjectType.FC_ECDSA_SECP256K1_KEY;
                    } else if (rootObject instanceof JECDSA_384Key) {
                        objectType = JObjectType.FC_ECDSA384_KEY;
                    } else if (rootObject instanceof JRSA_3072Key) {
                        objectType = JObjectType.FC_RSA3072_KEY;
                    } else if (rootObject instanceof JDelegatableContractIDKey) {
                        objectType = JObjectType.FC_DELEGATE_CONTRACT_ID_KEY;
                    } else if (rootObject instanceof JContractIDKey) {
                        objectType = JObjectType.FC_CONTRACT_ID_KEY;
                    } else if (rootObject instanceof JDelegatableContractAliasKey) {
                        objectType = JObjectType.FC_DELEGATE_CONTRACT_ALIAS_KEY;
                    } else if (rootObject instanceof JContractAliasKey) {
                        objectType = JObjectType.FC_CONTRACT_ALIAS_KEY;
                    }

                    final JObjectType finalObjectType = objectType;
                    buffer.writeLong(objectType.longValue());

                    byte[] content =
                            IoUtils.byteStream(os -> pack(os, finalObjectType, rootObject));
                    int length = content.length;

                    buffer.writeLong(length);

                    if (length > 0) {
                        buffer.write(content);
                    }
                });
    }

    public static <T> T deserialize(SerializableDataInputStream stream) throws IOException {
        final var version = stream.readLong();
        if (version == LEGACY_VERSION) {
            throw new IllegalArgumentException("Pre-OA serialization format no longer supported");
        }

        final var objectType = stream.readLong();
        final var type = JObjectType.valueOf(objectType);
        if (type == null) {
            throw new IllegalStateException(
                    "Value " + objectType + " from stream is not a valid object type");
        }

        final var length = stream.readLong();
        return unpack(stream, type, length);
    }

    static void pack(final DataOutputStream stream, final JObjectType type, final Object object)
            throws IOException {
        if (JObjectType.FC_ED25519_KEY.equals(type)) {
            JKey jKey = (JKey) object;
            byte[] key = jKey.getEd25519();
            stream.write(key);
        } else if (JObjectType.FC_ECDSA384_KEY.equals(type)) {
            JKey jKey = (JKey) object;
            byte[] key = jKey.getECDSA384();
            stream.write(key);
        } else if (JObjectType.FC_ECDSA_SECP256K1_KEY.equals(type)) {
            JKey jKey = (JKey) object;
            byte[] key = jKey.getECDSASecp256k1Key();
            stream.write(key);
        } else if (JObjectType.FC_THRESHOLD_KEY.equals(type)) {
            JThresholdKey key = (JThresholdKey) object;
            stream.writeInt(key.getThreshold());
            stream.write(serialize(key.getKeys()));
        } else if (JObjectType.FC_KEY_LIST.equals(type)) {
            JKeyList list = (JKeyList) object;
            List<JKey> keys = list.getKeysList();

            stream.writeInt(keys.size());

            if (!keys.isEmpty()) {
                for (JKey key : keys) {
                    stream.write(serialize(key));
                }
            }
        } else if (JObjectType.FC_RSA3072_KEY.equals(type)) {
            JKey jKey = (JKey) object;
            byte[] key = jKey.getRSA3072();
            stream.write(key);
        } else if (JObjectType.FC_CONTRACT_ID_KEY.equals(type)) {
            JContractIDKey key = (JContractIDKey) object;
            stream.writeLong(key.getShardNum());
            stream.writeLong(key.getRealmNum());
            stream.writeLong(key.getContractNum());
        } else if (JObjectType.FC_DELEGATE_CONTRACT_ID_KEY.equals(type)) {
            final var key = (JDelegatableContractIDKey) object;
            stream.writeLong(key.getShardNum());
            stream.writeLong(key.getRealmNum());
            stream.writeLong(key.getContractNum());
        } else if (JObjectType.FC_CONTRACT_ALIAS_KEY.equals(type)) {
            JContractAliasKey key = (JContractAliasKey) object;
            stream.writeLong(key.getShardNum());
            stream.writeLong(key.getRealmNum());
            stream.write(key.getEvmAddress());
        } else if (JObjectType.FC_DELEGATE_CONTRACT_ALIAS_KEY.equals(type)) {
            JDelegatableContractAliasKey key = (JDelegatableContractAliasKey) object;
            stream.writeLong(key.getShardNum());
            stream.writeLong(key.getRealmNum());
            stream.write(key.getEvmAddress());
        } else {
            throw new IllegalStateException(
                    "Unknown type was encountered while writing to the output stream");
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T unpack(SerializableDataInputStream stream, JObjectType type, long length)
            throws IOException {
        if (JObjectType.FC_ED25519_KEY.equals(type)) {
            byte[] key = new byte[(int) length];
            stream.readFully(key);
            return (T) new JEd25519Key(key);
        } else if (JObjectType.FC_ECDSA384_KEY.equals(type)) {
            byte[] key = new byte[(int) length];
            stream.readFully(key);
            return (T) new JECDSA_384Key(key);
        } else if (JObjectType.FC_ECDSA_SECP256K1_KEY.equals(type)) {
            byte[] key = new byte[(int) length];
            stream.readFully(key);
            return (T) new JECDSASecp256k1Key(key);
        } else if (JObjectType.FC_THRESHOLD_KEY.equals(type)) {
            int threshold = stream.readInt();
            JKeyList keyList = deserialize(stream);
            return (T) new JThresholdKey(keyList, threshold);
        } else if (JObjectType.FC_KEY_LIST.equals(type)) {
            List<JKey> elements = new LinkedList<>();
            int size = stream.readInt();
            if (size > 0) {
                for (int i = 0; i < size; i++) {
                    elements.add(deserialize(stream));
                }
            }
            return (T) new JKeyList(elements);
        } else if (JObjectType.FC_RSA3072_KEY.equals(type)) {
            byte[] key = new byte[(int) length];
            stream.readFully(key);
            return (T) new JRSA_3072Key(key);
        } else if (JObjectType.FC_CONTRACT_ID_KEY.equals(type)) {
            long shard = stream.readLong();
            long realm = stream.readLong();
            long contract = stream.readLong();
            return (T) new JContractIDKey(shard, realm, contract);
        } else if (JObjectType.FC_DELEGATE_CONTRACT_ID_KEY.equals(type)) {
            long shard = stream.readLong();
            long realm = stream.readLong();
            long contract = stream.readLong();
            return (T) new JDelegatableContractIDKey(shard, realm, contract);
        } else if (JObjectType.FC_CONTRACT_ALIAS_KEY.equals(type)) {
            long shard = stream.readLong();
            long realm = stream.readLong();
            byte[] evmAddress = new byte[20];
            stream.readFully(evmAddress);
            return (T) new JContractAliasKey(shard, realm, evmAddress);
        } else if (JObjectType.FC_DELEGATE_CONTRACT_ALIAS_KEY.equals(type)) {
            long shard = stream.readLong();
            long realm = stream.readLong();
            byte[] evmAddress = new byte[20];
            stream.readFully(evmAddress);
            return (T) new JDelegatableContractAliasKey(shard, realm, evmAddress);
        } else {
            throw new IllegalStateException(
                    "Unknown type was encountered while reading from the input stream");
        }
    }

    @FunctionalInterface
    public interface StreamConsumer<T> {
        void accept(T stream) throws IOException;
    }
}
