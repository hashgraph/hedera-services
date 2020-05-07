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

import com.hedera.services.legacy.exception.DeserializationException;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.legacy.exception.SerializationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JTransactionRecordSerializer {

  private static final long LEGACY_VERSION = 1;
  private static final long CURRENT_VERSION = 1; // For this release current and legacy version are same

  private static final Logger log = LogManager.getLogger(JTransactionRecordSerializer.class);

  public static TransactionRecord deserialize(byte[] byteStream) throws DeserializationException {
    try {
      DataInputStream stream = new DataInputStream(new ByteArrayInputStream(byteStream));
      long version = stream.readLong();
      if (version == LEGACY_VERSION) {
        return TransactionRecord.parseFrom(stream.readAllBytes());
      } else {
        //  other versions , will be implemented later, currently only Proto object is supported
        throw new DeserializationException("Invalid Object Version was read from Stream ");
      }
    } catch (Exception ie) {
      if (log.isDebugEnabled()) log.debug("Error in serialization of Transaction Record: = ", ie);
      throw new DeserializationException(
          "Exception occurred while desrializing TransactionRecord " + ie.getMessage());
    }
  }


  public static byte[] serialize(TransactionRecord transactionRecord)
      throws SerializationException {
    byte[] rv = null;
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
      try (DataOutputStream buffer = new DataOutputStream(byteArrayOutputStream)) {
        buffer.writeLong(CURRENT_VERSION);
        if (CURRENT_VERSION
            == LEGACY_VERSION) { // Store Proto Object directly into file system after converting to byte array)
          buffer.write(transactionRecord.toByteArray());
        } // else part will be for other versions will be implemented later

        buffer.flush();
        byteArrayOutputStream.flush();
        rv = byteArrayOutputStream.toByteArray();
      } catch (IOException ie) {
        log.warn("Error in serialization of Transaction Record ::" + transactionRecord, ie);
        throw new SerializationException(
            "Exception occurred while serializing TransationRecord " + ie.getMessage());
      }
    } catch (Exception e) {
      if (log.isDebugEnabled()) log.debug("Error in serialization of Transaction Record: = " + transactionRecord, e);
      throw new SerializationException(
          "Exception occurred while serializing TransationRecord " + e.getMessage());
    }
    return rv;
  }
}
