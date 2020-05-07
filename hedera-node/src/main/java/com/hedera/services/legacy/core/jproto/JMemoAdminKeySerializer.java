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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import com.hedera.services.legacy.exception.DeserializationException;
import com.hedera.services.legacy.exception.SerializationException;

/**
 * Custom Serializer for JMemoAdminKey structure.
 * 
 * @author Hua Li
 * Created on 2019-06-25
 */
public class JMemoAdminKeySerializer {

  private static final Charset MEMO_CHARSET = StandardCharsets.UTF_8;
  private static final long BPACK_VERSION = 1;

  private JMemoAdminKeySerializer() {}

  /**
   * Serializes JMemoAdminKey object.
   * 
   * @param fileInfObject JMemoAdminKey object to be serialized
   * @return serialized bytes
   * @throws SerializationException
   */
  public static byte[] serialize(JMemoAdminKey fileInfObject) throws SerializationException {
    try {
      return JKeySerializer.byteStream(buffer -> {
        buffer.writeLong(BPACK_VERSION);
        buffer.writeLong(JObjectType.JMemoAdminKey.longValue());

        byte[] content = JKeySerializer.byteStream(os -> pack(os, fileInfObject));
        int length = (content != null) ? content.length : 0;

        buffer.writeLong(length);

        if (length > 0) {
          buffer.write(content);
        }
      });
    } catch (IOException e) {
      throw new SerializationException(
          "Error in serialization of JMemoAdminKey :: " + fileInfObject.toString(), e);
    }
  }

  /**
   * Deserializes JMemoAdminKey object.
   * 
   * @param stream data input stream to be deserialized
   * @return the deserialized JMemoAdminKey object
   * @throws DeserializationException
   */
  public static JMemoAdminKey deserialize(DataInputStream stream) throws DeserializationException {
    long version;
    try {
      version = stream.readLong();
      long objectType = stream.readLong();
      long length = stream.readLong();

      if (objectType != JObjectType.JMemoAdminKey.longValue()) {
        throw new IllegalStateException(
            "Illegal JObjectType was read from the stream! read objectType long value = "
                + objectType);
      } else if (version != BPACK_VERSION) {
        throw new IllegalStateException(
            "Illegal version was read from the stream! read version = " + version);
      }

      return unpack(stream, length);
    } catch (IOException e) {
      throw new DeserializationException("Error in deserialization of JMemoAdminKey!", e);
    }
  }

  private static void pack(DataOutputStream stream, JMemoAdminKey jfi)
      throws IOException {
    byte[] memoBytes = jfi.getMemo().getBytes(MEMO_CHARSET);
    stream.writeInt(memoBytes.length);
    if(memoBytes.length > 0)
      stream.write(memoBytes);
    if(jfi.getAdminKey() == null)
      stream.writeBoolean(false);
    else {
      stream.writeBoolean(true);
      stream.write(JKeySerializer.serialize(jfi.getAdminKey()));
    }
  }

  private static JMemoAdminKey unpack(DataInputStream stream, long length)
      throws IOException {
    int len = stream.readInt();
    byte[] memoBytes = new byte[len];
    if(len > 0) {
      stream.readFully(memoBytes);
    }
    String memo = new String(memoBytes, MEMO_CHARSET);

    JKey adminKey = null;    
    boolean hasAdminKey = stream.readBoolean();
    if(hasAdminKey) {
      byte[] key = stream.readAllBytes();
      adminKey = JKeySerializer.deserialize(new DataInputStream(new ByteArrayInputStream(key)));
    }
    JMemoAdminKey jfi = new JMemoAdminKey(memo, adminKey);
    return jfi;
  }

  @FunctionalInterface
  interface StreamConsumer<T> {
    void apply(T stream) throws IOException;
  }
}
