package com.hedera.services.usage.consensus;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.google.protobuf.ByteString;
import com.hedera.services.test.AdapterUtils;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConsensusOpsUsageTest {
  private final int numSigs = 3, sigSize = 100, numPayerKeys = 1;
  private final long now = 1_234_567L;
  private final String aMemo = "The commonness of thoughts and images";
  private final String aMessage = "That have the frenzy of our Western seas";
  private final TopicID target = TopicID.newBuilder().setTopicNum(2345).build();
  private final SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);
  private final SigValueObj svo = new SigValueObj(numSigs, numPayerKeys, sigSize);
  private final AccountID payer = AccountID.newBuilder().setAccountNum(1234).build();

  private TransactionBody txn;

  private ConsensusOpsUsage subject = new ConsensusOpsUsage();

  @Test
  void matchesLegacyEstimate() {
    setupChunkSubmit(1, 2, txnId(payer, now - 123));
    // and:
    final var expected =
        FeeData.newBuilder()
            .setNetworkdata(
                FeeComponents.newBuilder().setConstant(1).setBpt(277).setVpt(3).setRbh(4))
            .setNodedata(FeeComponents.newBuilder().setConstant(1).setBpt(277).setVpt(1).setBpr(4))
            .setServicedata(FeeComponents.newBuilder().setConstant(1).setRbh(8))
            .build();

    // given:
    final var accum = new UsageAccumulator();
    // and:
    final var baseMeta = new BaseTransactionMeta(aMemo.length(), 0);
    final var submitMeta = new SubmitMessageMeta(aMessage.length());

    // when:
    subject.submitMessageUsage(sigUsage, submitMeta, baseMeta, accum);
    // and:
    final var actualLegacyRepr = AdapterUtils.feeDataFrom(accum);

    // then:
    Assertions.assertEquals(expected, actualLegacyRepr);
  }

  private void setupChunkSubmit(
      int totalChunks, int chunkNumber, TransactionID initialTransactionID) {
    ConsensusMessageChunkInfo chunkInfo =
        ConsensusMessageChunkInfo.newBuilder()
            .setInitialTransactionID(initialTransactionID)
            .setTotal(totalChunks)
            .setNumber(chunkNumber)
            .build();
    givenTransaction(getBasicValidTransactionBodyBuilder().setChunkInfo(chunkInfo));
  }

  private void givenTransaction(ConsensusSubmitMessageTransactionBody.Builder body) {
    txn =
        TransactionBody.newBuilder()
            .setMemo(aMemo)
            .setTransactionID(txnId(payer, now))
            .setConsensusSubmitMessage(body.build())
            .build();
  }

  private ConsensusSubmitMessageTransactionBody.Builder getBasicValidTransactionBodyBuilder() {
    return ConsensusSubmitMessageTransactionBody.newBuilder()
        .setTopicID(target)
        .setMessage(ByteString.copyFromUtf8(aMessage));
  }

  private TransactionID txnId(AccountID payer, long at) {
    return TransactionID.newBuilder()
        .setAccountID(payer)
        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(at))
        .build();
  }
}
