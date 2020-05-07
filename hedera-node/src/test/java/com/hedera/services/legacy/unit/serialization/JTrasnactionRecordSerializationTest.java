package com.hedera.services.legacy.unit.serialization;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.legacy.core.jproto.JTransactionRecordSerializer;
import com.hedera.services.legacy.exception.DeserializationException;
import com.hedera.services.legacy.exception.SerializationException;
import java.time.Instant;
import org.junit.Test;

public class JTrasnactionRecordSerializationTest {

  public static void main(String args[]) {
    JTrasnactionRecordSerializationTest tr = new JTrasnactionRecordSerializationTest();
    tr.testSerializationAndDeserialization();

  }

  @Test
  public void testSerializationAndDeserialization() {
    TransactionRecord tr = TransactionRecord.newBuilder()
        .setConsensusTimestamp(RequestBuilder.getTimestamp(Instant.now()))
        .setMemo("Testing Transaction record Srialization and Deserialization")
        .setTransactionFee(1000l)
        .setTransactionHash(ByteString.copyFromUtf8("HashgraphConsensusAlgorithm"))
        .build();

    System.out.print("The Transaction Record is Initial ::: " + tr);
    byte[] byteStr;
    try {
      byteStr = JTransactionRecordSerializer.serialize(tr);
      TransactionRecord trD = JTransactionRecordSerializer.deserialize(byteStr);
      System.out.print("The Transaction Record is Final ::: " + trD);
      assertEquals(tr.getMemo(), trD.getMemo());
      assertEquals(tr.getTransactionFee(), trD.getTransactionFee());
      assertEquals(tr.getTransactionHash(), trD.getTransactionHash());
    } catch (SerializationException | DeserializationException e) {
      e.printStackTrace();
    }


  }

}
