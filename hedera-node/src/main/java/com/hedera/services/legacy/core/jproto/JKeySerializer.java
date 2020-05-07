package com.hedera.services.legacy.core.jproto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.SerializationUtils;

/**
 * Custom Serializer for JKey structure.
 * 
 * @author Nathan Klick
 * @Date : 11/30/2018
 */
public class JKeySerializer {
  private static final long LEGACY_VERSION = 1;
  private static final long BPACK_VERSION = 2;

  private JKeySerializer() {}

  public static byte[] serialize(Object rootObject) throws IOException {
    return byteStream(buffer -> {
      buffer.writeLong(BPACK_VERSION);

      JObjectType objectType = JObjectType.JKey;

      if (rootObject instanceof JKeyList) {
        objectType = JObjectType.JKeyList;
      } else if (rootObject instanceof JThresholdKey) {
        objectType = JObjectType.JThresholdKey;
      } else if (rootObject instanceof JEd25519Key) {
        objectType = JObjectType.JEd25519Key;
      } else if (rootObject instanceof JECDSA_384Key) {
        objectType = JObjectType.JECDSA_384Key;
      } else if (rootObject instanceof JRSA_3072Key) {
        objectType = JObjectType.JRSA_3072Key;
      } else if (rootObject instanceof JContractIDKey) {
        objectType = JObjectType.JContractIDKey;
      }

      final JObjectType finalObjectType = objectType;
      buffer.writeLong(objectType.longValue());

      byte[] content = byteStream(os -> pack(os, finalObjectType, rootObject));
      int length = (content != null) ? content.length : 0;

      buffer.writeLong(length);

      if (length > 0) {
        buffer.write(content);
      }
    });
  }

  public static <T> T deserialize(DataInputStream stream) throws IOException {
    long version = stream.readLong();
    long objectType = stream.readLong();
    long length = stream.readLong();

    if (version == LEGACY_VERSION) {
      byte[] content = new byte[(int) length];
      return SerializationUtils.deserialize(content);
    }

    JObjectType type = JObjectType.valueOf(objectType);

    if (objectType < 0 || type == null) {
      throw new IllegalStateException("Illegal JObjectType was read from the stream");
    }

    return unpack(stream, type, length);
  }

  private static void pack(DataOutputStream stream, JObjectType type, Object object) throws IOException {
    if (JObjectType.JEd25519Key.equals(type) || JObjectType.JECDSA_384Key.equals(type)) {
      JKey jKey = (JKey)object;
      byte[] key = (jKey.hasEd25519Key()) ? jKey.getEd25519() : jKey.getECDSA384();
      stream.write(key);
    } else if (JObjectType.JThresholdKey.equals(type)) {
      JThresholdKey key = (JThresholdKey) object;
      stream.writeInt(key.getThreshold());
      stream.write(serialize(key.getKeys()));
    } else if (JObjectType.JKeyList.equals(type)) {
      JKeyList list = (JKeyList) object;
      List<JKey> keys = list.getKeysList();

      stream.writeInt(keys.size());

      if (keys.size() > 0) {
        for (JKey key : keys) {
          stream.write(serialize(key));
        }
      }
    } else if (JObjectType.JRSA_3072Key.equals(type)) {
      JKey jKey = (JKey) object;
      byte[] key = jKey.getRSA3072();
      stream.write(key);
    } else if (JObjectType.JContractIDKey.equals(type)) {
      JContractIDKey key = (JContractIDKey) object;
      stream.writeLong(key.getShardNum());
      stream.writeLong(key.getRealmNum());
      stream.writeLong(key.getContractNum());
    } else {
      throw new IllegalStateException(
          "Unknown type was encountered while writing to the output stream");
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T unpack(DataInputStream stream, JObjectType type, long length) throws IOException {
    if (JObjectType.JEd25519Key.equals(type) || JObjectType.JECDSA_384Key.equals(type)) {
      byte[] key = new byte[(int) length];
      stream.readFully(key);

      return (JObjectType.JEd25519Key.equals(type)) ? (T) new JEd25519Key(key)
          : (T) new JECDSA_384Key(key);
    } else if (JObjectType.JThresholdKey.equals(type)) {
      int threshold = stream.readInt();
      JKeyList keyList = deserialize(stream);

      return (T) new JThresholdKey(keyList, threshold);
    } else if (JObjectType.JKeyList.equals(type)) {
      List<JKey> elements = new LinkedList<>();

      int size = stream.readInt();

      if (size > 0) {
        for (int i = 0; i < size; i++) {
          elements.add(deserialize(stream));
        }
      }

      return (T) new JKeyList(elements);
    } else if (JObjectType.JRSA_3072Key.equals(type)) {
      byte[] key = new byte[(int) length];
      stream.readFully(key);

      return (T) new JRSA_3072Key(key);
    } else if (JObjectType.JContractIDKey.equals(type)) {
      long shard = stream.readLong();
      long realm = stream.readLong();
      long contract = stream.readLong();

      return (T) new JContractIDKey(shard, realm, contract);
    } else {
      throw new IllegalStateException(
          "Unknown type was encountered while reading from the input stream");
    }
  }

  protected static byte[] byteStream(StreamConsumer<DataOutputStream> consumer) throws IOException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      try (DataOutputStream dos = new DataOutputStream(bos)) {
        consumer.apply(dos);

        dos.flush();
        bos.flush();

        return bos.toByteArray();
      }
    }
  }

	@FunctionalInterface
	public static interface StreamConsumer<T> {

	  void apply(T stream) throws IOException;
	}
}
