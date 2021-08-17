package com.hedera.services.txns.token;

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

import static com.hedera.services.store.tokens.TokenStore.MISSING_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.Optional;
import javax.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(LogCaptureExtension.class)
class TokenFeeScheduleUpdateTransitionLogicTest {
  private long thisSecond = 1_234_567L;
  private Instant now = Instant.ofEpochSecond(thisSecond);
  private TokenID target = IdUtils.asToken("1.2.666");
  private JKey adminKey = new JEd25519Key("w/e".getBytes());
  private TokenFeeScheduleUpdateTransactionBody tokenFeeScheduleUpdateTxn;
  private TransactionBody tokenFeeScheduleUpdateTxnBody;
  private MerkleToken token;

  private TokenStore store;
  private TransactionContext txnCtx;
  private PlatformTxnAccessor accessor;

  @Inject private LogCaptor logCaptor;

  @LoggingSubject private TokenFeeScheduleUpdateTransitionLogic subject;

  @BeforeEach
  void setup() {
    store = mock(TokenStore.class);
    accessor = mock(PlatformTxnAccessor.class);
    txnCtx = mock(TransactionContext.class);

    token = mock(MerkleToken.class);
    given(token.adminKey()).willReturn(Optional.of(adminKey));
    given(store.resolve(target)).willReturn(target);
    given(store.get(target)).willReturn(token);

    subject = new TokenFeeScheduleUpdateTransitionLogic(store, txnCtx);
  }

  @Test
  void happyPathWorks() {
    givenValidTxnCtx();
    given(token.isDeleted()).willReturn(false);
    given(store.updateFeeSchedule(any())).willReturn(OK);

    subject.doStateTransition();

    verify(txnCtx).setStatus(SUCCESS);
  }

  @Test
  void failsOnMissingTokenId() {
    givenValidTxnCtx();
    given(store.resolve(target)).willReturn(MISSING_TOKEN);

    subject.doStateTransition();

    verify(txnCtx).setStatus(INVALID_TOKEN_ID);
  }

  @Test
  void failsOnAlreadyDeletedToken() {
    givenValidTxnCtx();
    given(token.isDeleted()).willReturn(true);

    subject.doStateTransition();

    verify(txnCtx).setStatus(TOKEN_WAS_DELETED);
  }

  @Test
  void setsFailInvalidIfUnhandledException() {
    givenValidTxnCtx();
    given(store.updateFeeSchedule(any())).willThrow(IllegalStateException.class);

    subject.doStateTransition();

    verify(txnCtx).setStatus(FAIL_INVALID);
    assertThat(
        logCaptor.warnLogs(),
        contains(Matchers.startsWith("Unhandled error while processing :: ")));
  }

  @Test
  void returnsFailingStatusFromUpdatingFeeScheduleInStore() {
    givenValidTxnCtx();
    given(store.updateFeeSchedule(any())).willReturn(CUSTOM_FEES_LIST_TOO_LONG);

    subject.doStateTransition();

    verify(txnCtx).setStatus(CUSTOM_FEES_LIST_TOO_LONG);
  }

  private void givenValidTxnCtx() {
    final var builder = TokenFeeScheduleUpdateTransactionBody.newBuilder().setTokenId(target);
    tokenFeeScheduleUpdateTxn = builder.build();
    TransactionBody txn = mock(TransactionBody.class);
    given(txnCtx.accessor()).willReturn(accessor);
    given(accessor.getTxn()).willReturn(txn);

    given(txn.getTokenFeeScheduleUpdate()).willReturn(tokenFeeScheduleUpdateTxn);

    given(txnCtx.consensusTime()).willReturn(now);
  }

  @Test
  void rejectsInvalidTokenId() {
    givenInvalidTokenId();

    assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenFeeScheduleUpdateTxnBody));
  }

  @Test
  void acceptsValidTokenId() {
    givenValidTokenId();

    assertEquals(OK, subject.semanticCheck().apply(tokenFeeScheduleUpdateTxnBody));
  }

  @Test
  void hasCorrectApplicability() {
    givenValidTokenId();

    assertTrue(subject.applicability().test(tokenFeeScheduleUpdateTxnBody));
    assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
  }

  private void givenInvalidTokenId() {
    tokenFeeScheduleUpdateTxnBody =
        TransactionBody.newBuilder()
            .setTokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.newBuilder())
            .build();
  }

  private void givenValidTokenId() {
    tokenFeeScheduleUpdateTxnBody =
        TransactionBody.newBuilder()
            .setTokenFeeScheduleUpdate(
                TokenFeeScheduleUpdateTransactionBody.newBuilder().setTokenId(target))
            .build();
  }
}
