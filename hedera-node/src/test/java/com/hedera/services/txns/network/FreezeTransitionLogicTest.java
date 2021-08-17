package com.hedera.services.txns.network;

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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.files.interceptors.MockFileNumbers;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FreezeTransitionLogicTest {
  private final AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();

  private Instant consensusTime;
  private FreezeTransitionLogic.LegacyFreezer delegate;
  private TransactionBody freezeTxn;
  private TransactionContext txnCtx;
  private PlatformTxnAccessor accessor;
  FreezeTransitionLogic subject;
  FileNumbers fileNums = new MockFileNumbers();

  @BeforeEach
  private void setup() {
    consensusTime = Instant.now();

    delegate = mock(FreezeTransitionLogic.LegacyFreezer.class);
    txnCtx = mock(TransactionContext.class);
    given(txnCtx.consensusTime()).willReturn(consensusTime);
    accessor = mock(PlatformTxnAccessor.class);

    subject = new FreezeTransitionLogic(fileNums, delegate, txnCtx);
  }

  @Test
  void hasCorrectApplicability() {
    givenTxnCtx();

    assertTrue(subject.applicability().test(freezeTxn));
    assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
  }

  @Test
  void capturesBadFreeze() {
    TransactionRecord freezeRec =
        TransactionRecord.newBuilder()
            .setReceipt(
                TransactionReceipt.newBuilder().setStatus(INVALID_FREEZE_TRANSACTION_BODY).build())
            .build();
    givenTxnCtx();
    given(delegate.perform(freezeTxn, consensusTime)).willReturn(freezeRec);

    subject.doStateTransition();

    verify(txnCtx).setStatus(INVALID_FREEZE_TRANSACTION_BODY);
  }

  @Test
  void followsHappyPathWithOverrides() {
    TransactionRecord freezeRec =
        TransactionRecord.newBuilder()
            .setReceipt(TransactionReceipt.newBuilder().setStatus(SUCCESS).build())
            .build();
    givenTxnCtx();
    given(delegate.perform(freezeTxn, consensusTime)).willReturn(freezeRec);

    subject.doStateTransition();

    verify(txnCtx).setStatus(SUCCESS);
  }

  @Test
  void acceptsOkSyntax() {
    givenTxnCtx();

    assertEquals(OK, subject.semanticCheck().apply(freezeTxn));
  }

  @Test
  void rejectsInvalidTime() {
    givenTxnCtx(false, Optional.empty(), Optional.empty(), false);

    assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
  }

  @Test
  void acceptValidFreezeStartTimeStamp() {
    givenTxnCtx(true, Optional.empty(), Optional.empty(), true);

    assertEquals(OK, subject.semanticCheck().apply(freezeTxn));
  }

  @Test
  void rejectsInvalidFreezeStartTimeStamp() {
    givenTxnCtx(false, Optional.empty(), Optional.empty(), true);

    assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
  }

  @Test
  void rejectsInvalidUpdateTarget() {
    givenTxnCtx(
        true, Optional.of(fileNums.toFid(fileNums.feeSchedules())), Optional.empty(), false);

    assertEquals(INVALID_FILE_ID, subject.semanticCheck().apply(freezeTxn));
  }

  @Test
  void rejectsMissingFileHash() {
    givenTxnCtx(
        true, Optional.of(fileNums.toFid(fileNums.softwareUpdateZip())), Optional.empty(), false);

    assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
  }

  @Test
  void translatesUnknownException() {
    givenTxnCtx();
    given(delegate.perform(any(), any())).willThrow(IllegalStateException.class);

    subject.doStateTransition();

    verify(txnCtx).setStatus(FAIL_INVALID);
  }

  private void givenTxnCtx() {
    givenTxnCtx(true, Optional.empty(), Optional.empty(), false);
  }

  private void givenTxnCtx(
      final boolean validTime,
      final Optional<FileID> updateTarget,
      final Optional<ByteString> fileHash,
      final boolean useTimeStamp) {
    final var txn = TransactionBody.newBuilder().setTransactionID(ourTxnId());

    final var op = FreezeTransactionBody.newBuilder();
    if (!useTimeStamp) {
      if (validTime) {
        plusValidTime(op);
      } else {
        plusInvalidTime(op);
      }
    } else {
      if (validTime) {
        setValidFreezeStartTimeStamp(op);
      } else {
        setInvalidFreezeStartTimeStamp(op);
      }
    }
    updateTarget.ifPresent(op::setUpdateFile);
    fileHash.ifPresent(op::setFileHash);

    txn.setFreeze(op);
    freezeTxn = txn.build();
    given(accessor.getTxn()).willReturn(freezeTxn);
    given(txnCtx.accessor()).willReturn(accessor);
  }

  private void plusValidTime(final FreezeTransactionBody.Builder op) {
    op.setStartHour(15).setStartMin(15).setEndHour(15).setEndMin(20);
  }

  private void plusInvalidTime(final FreezeTransactionBody.Builder op) {
    op.setStartHour(24).setStartMin(15).setEndHour(15).setEndMin(20);
  }

  private void setValidFreezeStartTimeStamp(final FreezeTransactionBody.Builder op) {
    final var validFreezeStartTime = Instant.now().plusSeconds(120);
    op.setStartTime(
        Timestamp.newBuilder()
            .setSeconds(validFreezeStartTime.getEpochSecond())
            .setNanos(validFreezeStartTime.getNano()));
  }

  private void setInvalidFreezeStartTimeStamp(final FreezeTransactionBody.Builder op) {
    final var inValidFreezeStartTime = Instant.now().minusSeconds(60);
    op.setStartTime(
        Timestamp.newBuilder()
            .setSeconds(inValidFreezeStartTime.getEpochSecond())
            .setNanos(inValidFreezeStartTime.getNano()));
  }

  private TransactionID ourTxnId() {
    return TransactionID.newBuilder()
        .setAccountID(payer)
        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
        .build();
  }
}
