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

import com.google.protobuf.ByteString;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(JUnitPlatform.class)
public class TxnReceiptTest {
  private TopicID getTopicId(long shard, long realm, long num) {
    return TopicID.newBuilder().setShardNum(shard).setRealmNum(realm).setTopicNum(num).build();
  }

  private EntityId getTopicJAccountId(long shard, long realm, long num) {
    return new EntityId(shard, realm, num);
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
    final var cut = new TxnReceipt(
            null, null, null, null, null, null,
            topicId, sequenceNumber, null);

    assertAll(() -> assertEquals(topicId, cut.getTopicId()),
            () -> assertEquals(sequenceNumber, cut.getTopicSequenceNumber()),
            () -> assertNull(cut.getTopicRunningHash())
    );
  }

  @Test
  public void constructorPostConsensusSubmitMessage() {
    final var sequenceNumber = 55555L;
    final var cut = new TxnReceipt(
            null, null, null, null, null, null, null,
            sequenceNumber, getSha384Hash());

    assertAll(() -> assertNull(cut.getTopicId()),
            () -> assertEquals(sequenceNumber, cut.getTopicSequenceNumber()),
            () -> assertArrayEquals(getSha384Hash(), cut.getTopicRunningHash())
    );
  }

  @Test
  public void setRunning() {
    final var cut = new TxnReceipt();
    cut.topicRunningHash = getSha384Hash();
    assertArrayEquals(getSha384Hash(), cut.getTopicRunningHash());
  }

  @Test
  public void convertToJTransactionReceiptPostConsensusCreateTopic() {
    final var topicId = getTopicId(1L, 22L, 333L);
    final var receipt = TransactionReceipt.newBuilder()
            .setExchangeRate(new ExchangeRates().toGrpc())
            .setTopicID(topicId).build();
    final var cut = TxnReceipt.fromGrpc(receipt);

    assertAll(() -> assertEquals(EntityId.ofNullableTopicId(topicId), cut.getTopicId()),
            () -> assertNull(cut.getAccountId()),
            () -> assertNull(cut.getFileId()),
            () -> assertNull(cut.getContractId()),
            () -> assertEquals(new ExchangeRates(), cut.getExchangeRates()),
            () -> assertEquals(0L, cut.getTopicSequenceNumber()),
            () -> assertNull(cut.getTopicRunningHash())
    );
  }

  @Test
  public void postConsensusSubmitMessageInterconversionWorks() {
    final var topicSequenceNumber = 4444L;
    final var topicRunningHash = getSha384Hash();

    final var receipt = TransactionReceipt.newBuilder()
            .setExchangeRate(new ExchangeRates().toGrpc())
            .setTopicSequenceNumber(topicSequenceNumber)
            .setTopicRunningHash(ByteString.copyFrom(topicRunningHash))
            .setTopicRunningHashVersion(2L)
            .build();
    final var cut = TxnReceipt.fromGrpc(receipt);
    final var back = TxnReceipt.convert(cut);

    assertEquals(receipt, back);
  }

  @Test
  public void postConsensusTokenMintBurnWipeInterconversionWorks() {
    final var totalSupply = 12345L;

    final var receipt = TransactionReceipt.newBuilder()
            .setExchangeRate(new ExchangeRates().toGrpc())
            .setNewTotalSupply(totalSupply)
            .build();
    final var cut = TxnReceipt.fromGrpc(receipt);
    final var back = TxnReceipt.convert(cut);

    assertEquals(receipt, back);
  }

  @Test
  public void postConsensusTokenCreationInterconversionWorks() {
    final TokenID.Builder tokenIdBuilder = TokenID.newBuilder().setTokenNum(1001L).setRealmNum(0).setShardNum(0);

    final var receipt = TransactionReceipt.newBuilder()
            .setExchangeRate(new ExchangeRates().toGrpc())
            .setTokenID(tokenIdBuilder)
            .build();
    final var cut = TxnReceipt.fromGrpc(receipt);
    final var back = TxnReceipt.convert(cut);

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
    final var cut = TxnReceipt.fromGrpc(receipt);

    assertAll(
            () -> assertEquals(2L, cut.getRunningHashVersion()),
            () -> assertNull(cut.getTopicId()),
            () -> assertEquals(topicSequenceNumber, cut.getTopicSequenceNumber()),
            () -> assertArrayEquals(topicRunningHash, cut.getTopicRunningHash())
    );
  }

  @Test
  public void convertToTransactionReceiptPostConsensusCreateTopic() {
    final var topicId = getTopicJAccountId(1L, 22L, 333L);
    final var receipt = new TxnReceipt();
    receipt.status = "OK";
    receipt.topicId = topicId;
    final var cut = TxnReceipt.convert(receipt);

    assertAll(() -> assertEquals(topicId.shard(), cut.getTopicID().getShardNum()),
            () -> assertEquals(topicId.realm(), cut.getTopicID().getRealmNum()),
            () -> assertEquals(topicId.num(), cut.getTopicID().getTopicNum()),
            () -> assertEquals(0L, cut.getTopicSequenceNumber()),
            () -> assertEquals(0, cut.getTopicRunningHash().size())
    );
  }

  @Test
  public void convertToTransactionReceiptPostConsensusSubmitMessage() {
    final var sequenceNumber = 666666L;
    final var receipt = new TxnReceipt();
    receipt.status = "OK";
    receipt.topicSequenceNumber = sequenceNumber;
    receipt.topicRunningHash = getSha384Hash();
    final var cut = TxnReceipt.convert(receipt);

    assertAll(() -> assertFalse(cut.hasTopicID()),
            () -> assertEquals(sequenceNumber, cut.getTopicSequenceNumber()),
            () -> assertArrayEquals(getSha384Hash(), cut.getTopicRunningHash().toByteArray())
    );
  }


  @Test
  public void equalsDefaults() {
    assertEquals(new TxnReceipt(), new TxnReceipt());
  }

  @Test
  public void hashCodeWithNulls() {
    final var cut = new TxnReceipt();
    assertNull(cut.getTopicId());
    assertNull(cut.getTopicRunningHash());

    Assertions.assertDoesNotThrow(() -> cut.hashCode());
  }

  @Test
  public void toStringWithNulls() {
    final var cut = new TxnReceipt();
    assertNull(cut.getTopicId());
    assertNull(cut.getTopicRunningHash());

    assertAll(() -> Assertions.assertDoesNotThrow(() -> cut.toString()),
            () -> assertNotNull(cut.toString()));
  }

  @Test
  public void hcsConstructor() {
    final var topicId = EntityId.ofNullableTopicId(TopicID.newBuilder().setTopicNum(1L).build());
    final var sequenceNumber = 2L;
    final var runningHash = new byte[3];
    final var cut = new TxnReceipt(
            "SUCCESS", null, null, null, null, null,
            topicId, sequenceNumber, runningHash);

    assertEquals(topicId, cut.getTopicId());
    assertEquals(sequenceNumber, cut.getTopicSequenceNumber());
    assertEquals(runningHash, cut.getTopicRunningHash());

    assertAll(() -> Assertions.assertDoesNotThrow(() -> cut.toString()),
            () -> assertNotNull(cut.toString()));
  }
  @Test
  public void tokenConstructorWithTokenId() {
    final var tokenId = EntityId.ofNullableTokenId(
            TokenID.newBuilder().setTokenNum(1001L).setRealmNum(0).setShardNum(0).build());
    final var cut = new TxnReceipt(
            "SUCCESS", null, null, null, tokenId, null,
            null, 0L, null);

    assertEquals(tokenId, cut.getTokenId());

    assertAll(() -> Assertions.assertDoesNotThrow(() -> cut.toString()),
            () -> assertNotNull(cut.toString()));
  }

  @Test
  public void tokenConstructorWithTotalSupply() {
    final var tokenId = EntityId.ofNullableTokenId(
            TokenID.newBuilder().setTokenNum(1001L).setRealmNum(0).setShardNum(0).build());
    final var cut = new TxnReceipt(
            "SUCCESS", null, null, null, null, null,
            null, 0L, null, TxnReceipt.MISSING_RUNNING_HASH_VERSION, 1000L);

    assertEquals(1000L, cut.getNewTotalSupply());

    assertAll(() -> Assertions.assertDoesNotThrow(() -> cut.toString()),
            () -> assertNotNull(cut.toString()));
  }

}
