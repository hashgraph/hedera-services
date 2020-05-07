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

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(JUnitPlatform.class)
public class JTransactionReceiptTest {
  private TopicID getTopicId(long shard, long realm, long num) {
    return TopicID.newBuilder().setShardNum(shard).setRealmNum(realm).setTopicNum(num).build();
  }

  private JAccountID getTopicJAccountId(long shard, long realm, long num) {
    return new JAccountID(shard, realm, num);
  }

  private byte[] getSha384Hash() {
    final var hash = new byte[48];
    for (var i = 0; i < hash.length; ++i) {
      hash[i] = (byte)i;
    }
    return hash;
  }

  @Test
  public void constructorPostConsensusCreateTopic() {
    final var topicId = getTopicJAccountId(1L, 22L, 333L);
    final var sequenceNumber = 0L;
    final var cut = new JTransactionReceipt(null, null, null, null, null, topicId, sequenceNumber, null);

    assertAll(() -> assertEquals(topicId, cut.getTopicID()),
            () -> assertEquals(sequenceNumber, cut.getTopicSequenceNumber()),
            () -> assertNull(cut.getTopicRunningHash())
    );
  }

  @Test
  public void constructorPostConsensusSubmitMessage() {
    final var sequenceNumber = 55555L;
    final var cut = new JTransactionReceipt(null, null, null, null, null, null, sequenceNumber, getSha384Hash());

    assertAll(() -> assertNull(cut.getTopicID()),
            () -> assertEquals(sequenceNumber, cut.getTopicSequenceNumber()),
            () -> assertArrayEquals(getSha384Hash(), cut.getTopicRunningHash())
    );
  }

  @Test
  public void setRunningHashNull() {
    final var cut = new JTransactionReceipt();
    cut.setTopicRunningHash(null);
    assertNull(cut.getTopicRunningHash());
  }

  @Test
  public void setRunningHashEmpty() {
    final var cut = new JTransactionReceipt();
    cut.setTopicRunningHash(new byte[0]);
    assertNull(cut.getTopicRunningHash());
  }

  @Test
  public void setRunning() {
    final var cut = new JTransactionReceipt();
    cut.setTopicRunningHash(getSha384Hash());
    assertArrayEquals(getSha384Hash(), cut.getTopicRunningHash());
  }

  @Test
  public void convertToJTransactionReceiptPostConsensusCreateTopic() {
    final var topicId = getTopicId(1L, 22L, 333L);
    final var receipt = TransactionReceipt.newBuilder().setTopicID(topicId).build();
    final var cut = JTransactionReceipt.convert(receipt);

    assertAll(() -> assertEquals(JAccountID.convert(topicId), cut.getTopicID()),
            () -> assertNull(cut.getAccountID()),
            () -> assertNull(cut.getFileID()),
            () -> assertNull(cut.getContractID()),
            () -> assertNull(cut.getExchangeRate()),
            () -> assertEquals(0L, cut.getTopicSequenceNumber()),
            () -> assertNull(cut.getTopicRunningHash())
    );
  }

  @Test
  public void postConsensusSubmitMessageInterconversionWorks() {
    final var topicSequenceNumber = 4444L;
    final var topicRunningHash = getSha384Hash();

    final var receipt = TransactionReceipt.newBuilder()
            .setTopicSequenceNumber(topicSequenceNumber)
            .setTopicRunningHash(ByteString.copyFrom(topicRunningHash))
            .setTopicRunningHashVersion(2L)
            .build();
    final var cut = JTransactionReceipt.convert(receipt);
    final var back = JTransactionReceipt.convert(cut);

    assertEquals(receipt, back);
  }

  @Test
  public void convertToJTransactionReceiptPostConsensusSubmitMessage() {
    final var topicSequenceNumber = 4444L;
    final var topicRunningHash = getSha384Hash();

    final var receipt = TransactionReceipt.newBuilder()
            .setTopicSequenceNumber(topicSequenceNumber)
            .setTopicRunningHash(ByteString.copyFrom(topicRunningHash))
            .setTopicRunningHashVersion(2L)
            .build();
    final var cut = JTransactionReceipt.convert(receipt);

    assertAll(
            () -> assertEquals(2L, cut.getRunningHashVersion()),
            () -> assertNull(cut.getTopicID()),
            () -> assertEquals(topicSequenceNumber, cut.getTopicSequenceNumber()),
            () -> assertArrayEquals(topicRunningHash, cut.getTopicRunningHash())
    );
  }

  @Test
  public void convertToTransactionReceiptPostConsensusCreateTopic() {
    final var topicId = getTopicJAccountId(1L, 22L, 333L);
    final var receipt = new JTransactionReceipt();
    receipt.setStatus("OK");
    receipt.setTopicID(topicId);
    final var cut = JTransactionReceipt.convert(receipt);

    assertAll(() -> assertEquals(topicId.getShardNum(), cut.getTopicID().getShardNum()),
            () -> assertEquals(topicId.getRealmNum(), cut.getTopicID().getRealmNum()),
            () -> assertEquals(topicId.getAccountNum(), cut.getTopicID().getTopicNum()),
            () -> assertEquals(0L, cut.getTopicSequenceNumber()),
            () -> assertEquals(0, cut.getTopicRunningHash().size())
    );
  }

  @Test
  public void convertToTransactionReceiptPostConsensusSubmitMessage() {
    final var sequenceNumber = 666666L;
    final var receipt = new JTransactionReceipt();
    receipt.setStatus("OK");
    receipt.setTopicSequenceNumber(sequenceNumber);
    receipt.setTopicRunningHash(getSha384Hash());
    final var cut = JTransactionReceipt.convert(receipt);

    assertAll(() -> assertFalse(cut.hasTopicID()),
            () -> assertEquals(sequenceNumber, cut.getTopicSequenceNumber()),
            () -> assertArrayEquals(getSha384Hash(), cut.getTopicRunningHash().toByteArray())
    );
  }

  @Test
  public void serializeDeserializeDefault() throws IOException {
    final var expected = new JTransactionReceipt();

    byte[] serialized;
    JTransactionReceipt actual;

    // Serialize
    try (ByteArrayOutputStream bs = new ByteArrayOutputStream()) {
      try (FCDataOutputStream os = new FCDataOutputStream(bs)) {
        expected.copyTo(os);
        serialized = bs.toByteArray();
      }
    }

    // Deserialize
    try (ByteArrayInputStream bs = new ByteArrayInputStream(serialized)) {
      try (FCDataInputStream is = new FCDataInputStream(bs)) {
        actual = JTransactionReceipt.deserialize(is);
      }
    }

    assertEquals(expected, actual);
  }

  @Test
  public void serUsesCurrentVersionIfNotMissingHashVersion() throws IOException {
    // given:
    final var fcReceipt = new JTransactionReceipt(
            "OK", null, null, null, null,
            getTopicJAccountId(1L, 22L, 333L), 4444L,
            getSha384Hash(), 3L);

    // when:
    byte[] serialized;
    try (ByteArrayOutputStream bs = new ByteArrayOutputStream()) {
      try (FCDataOutputStream os = new FCDataOutputStream(bs)) {
        fcReceipt.copyTo(os);
        serialized = bs.toByteArray();
      }
    }
    // and:
    long version = Longs.fromByteArray(Arrays.copyOfRange(serialized, 0, 8));
    // and:
    long runningHashVersion = Longs.fromByteArray(
            Arrays.copyOfRange(serialized, serialized.length - 8, serialized.length));

    // then:
    assertEquals(JTransactionReceipt.CURRENT_VERSION, version);
    assertEquals(3L, runningHashVersion);
  }

  @Test
  public void serUsesOldVersionIfMissingHashVersion() throws IOException {
    // given:
    final var fcReceipt = new JTransactionReceipt(
            "OK", null, null, null, null,
            getTopicJAccountId(1L, 22L, 333L), 4444L,
            getSha384Hash());

    // when:
    byte[] serialized;
    try (ByteArrayOutputStream bs = new ByteArrayOutputStream()) {
      try (FCDataOutputStream os = new FCDataOutputStream(bs)) {
        fcReceipt.copyTo(os);
        serialized = bs.toByteArray();
      }
    }
    // and:
    long version = Longs.fromByteArray(Arrays.copyOfRange(serialized, 0, 8));

    // then:
    assertEquals(JTransactionReceipt.VERSION_WITHOUT_EXPLICIT_RUNNING_HASH_VERSION, version);
  }

  @Test
  public void serdeWorks() throws IOException {
    final var expected = new JTransactionReceipt(
            "OK", null, null, null, null,
            getTopicJAccountId(1L, 22L, 333L), 4444L,
            getSha384Hash(), 2L);

    byte[] serialized;
    JTransactionReceipt actual;

    // Serialize
    try (ByteArrayOutputStream bs = new ByteArrayOutputStream()) {
      try (FCDataOutputStream os = new FCDataOutputStream(bs)) {
        expected.copyTo(os);
        serialized = bs.toByteArray();
      }
    }

    // Deserialize
    try (ByteArrayInputStream bs = new ByteArrayInputStream(serialized)) {
      try (FCDataInputStream is = new FCDataInputStream(bs)) {
        actual = JTransactionReceipt.deserialize(is);
      }
    }

    assertEquals(expected, actual);
  }

  @Test
  public void equalsDefaults() {
    assertEquals(new JTransactionReceipt(), new JTransactionReceipt());
  }

  @Test
  public void hashCodeWithNulls() {
    final var cut = new JTransactionReceipt();
    assertNull(cut.getTopicID());
    assertNull(cut.getTopicRunningHash());

    Assertions.assertDoesNotThrow(() -> cut.hashCode());
  }

  @Test
  public void toStringWithNulls() {
    final var cut = new JTransactionReceipt();
    assertNull(cut.getTopicID());
    assertNull(cut.getTopicRunningHash());

    assertAll(() -> Assertions.assertDoesNotThrow(() -> cut.toString()),
            () -> assertNotNull(cut.toString()));
  }

  @Test
  public void hcsConstructor() {
    final var topicId = JAccountID.convert(TopicID.newBuilder().setTopicNum(1L).build());
    final var sequenceNumber = 2L;
    final var runningHash = new byte[3];
    final var cut = new JTransactionReceipt("SUCCESS", null, null, null, null,
            topicId, sequenceNumber, runningHash);

    assertEquals(topicId, cut.getTopicID());
    assertEquals(sequenceNumber, cut.getTopicSequenceNumber());
    assertEquals(runningHash, cut.getTopicRunningHash());

    assertAll(() -> Assertions.assertDoesNotThrow(() -> cut.toString()),
            () -> assertNotNull(cut.toString()));
  }
}
