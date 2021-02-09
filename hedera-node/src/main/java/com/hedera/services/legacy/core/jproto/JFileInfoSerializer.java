package com.hedera.services.legacy.core.jproto;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.legacy.exception.DeserializationException;
import com.hedera.services.legacy.exception.SerializationException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Custom Serializer for JFileInfo structure.
 * 
 * @author Hua Li Created on 2019-01-15
 */
public class JFileInfoSerializer {

  private static final long BPACK_VERSION = 1;

  private JFileInfoSerializer() {}

  /**
   * Serializes JFileInfo object.
   * 
   * @param fileInfObject JFileInfo object to be serialized
   * @return serialized bytes
   * @throws SerializationException
   */
  public static byte[] serialize(JFileInfo fileInfObject) throws SerializationException {
    try {
      return JKeySerializer.byteStream(buffer -> {
        buffer.writeLong(BPACK_VERSION);
        buffer.writeLong(JObjectType.JFileInfo.longValue());

        byte[] content = JKeySerializer.byteStream(os -> pack(os, fileInfObject));
        int length = (content != null) ? content.length : 0;

        buffer.writeLong(length);

        if (length > 0) {
          buffer.write(content);
        }
      });
    } catch (IOException e) {
      throw new SerializationException(
          "Error in serialization of JFileInfo :: " + fileInfObject.toString(), e);
    }
  }

  /**
   * Deserializes JFileInfo object.
   * 
   * @param stream data input stream to be deserialized
   * @return the deserialized JFileInfo object
   * @throws DeserializationException
   */
  public static JFileInfo deserialize(DataInputStream stream) throws DeserializationException {
    long version;
    try {
      version = stream.readLong();
      long objectType = stream.readLong();
      long length = stream.readLong();

      if (objectType != JObjectType.JFileInfo.longValue()) {
        throw new IllegalStateException(
            "Illegal JObjectType was read from the stream! read objectType long value = "
                + objectType);
      } else if (version != BPACK_VERSION) {
        throw new IllegalStateException(
            "Illegal version was read from the stream! read version = " + version);
      }

      return unpack(stream, length);
    } catch (IOException e) {
      throw new DeserializationException("Error in deserialization of JFileInfo!", e);
    }
  }

  private static void pack(DataOutputStream stream, JFileInfo jfi)
      throws IOException {
    stream.writeBoolean(jfi.isDeleted());
    stream.writeLong(jfi.getExpirationTimeSeconds());
    stream.write(JKeySerializer.serialize(jfi.getWacl()));
  }

  private static JFileInfo unpack(DataInputStream stream, long length)
      throws IOException {
    boolean deleted = stream.readBoolean();
    long expirationTime = stream.readLong();
    byte[] key = stream.readAllBytes();
    JKey wacl = JKeySerializer.deserialize(new DataInputStream(new ByteArrayInputStream(key)));
    JFileInfo jfi = new JFileInfo(deleted, wacl, expirationTime);
    return jfi;
  }

  @FunctionalInterface
  interface StreamConsumer<T> {
    void apply(T stream) throws IOException;
  }
}
