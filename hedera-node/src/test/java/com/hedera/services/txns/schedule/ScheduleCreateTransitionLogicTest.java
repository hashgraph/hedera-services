package com.hedera.services.txns.schedule;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.KeysHelper;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
public class ScheduleCreateTransitionLogicTest {
    long thisSecond = 1_234_567L;
    private Instant now = Instant.ofEpochSecond(thisSecond);
    private byte[] transactionBody = TxnUtils.randomUtf8Bytes(6);

    private final Optional<ScheduleID> EMPTY_SCHEDULE = Optional.empty();
    private final Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
    private final Key invalidKey = Key.newBuilder().build();
    private Optional<JKey> jAdminKey;
    private final boolean NO = false;
    private final boolean YES = true;
    private final ResponseCodeEnum NOT_OK = null;

    private ScheduleStore store;
    private PlatformTxnAccessor accessor;
    private TransactionContext txnCtx;

    private AccountID payer = IdUtils.asAccount("1.2.3");
    private ScheduleID schedule = IdUtils.asSchedule("2.4.6");

    private TransactionBody scheduleCreateTxn;
    private SignatureMap sigMap;
    private Set<JKey> jKeySet;

    private ScheduleCreateTransitionLogic subject;

    @BeforeEach
    private void setup() {
        store = mock(ScheduleStore.class);
        accessor = mock(PlatformTxnAccessor.class);

        txnCtx = mock(TransactionContext.class);
        given(txnCtx.activePayer()).willReturn(payer);

        subject = new ScheduleCreateTransitionLogic(store, txnCtx);
    }

    @Test
    public void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(scheduleCreateTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    public void followsHappyPath() {
        givenValidTxnCtx();

        given(store.getScheduleID(transactionBody, payer)).willReturn(EMPTY_SCHEDULE);
        given(store.createProvisionally(
                eq(transactionBody),
                eq(payer),
                eq(payer),
                eq(RichInstant.fromJava(now)),
                argThat(jKey -> true)))
                .willReturn(CreationResult.success(schedule));
        // and:
        given(store.addSigners(
                eq(schedule),
                argThat(jKeySet -> true))).willReturn(OK);

        // when:
        subject.doStateTransition();

        // then:
        verify(store).getScheduleID(transactionBody, payer);
        // and:
        verify(store).createProvisionally(
                eq(transactionBody),
                eq(payer),
                eq(payer),
                eq(RichInstant.fromJava(now)),
                argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())));
        // and:
        verify(store).commitCreation();
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    public void capturesPendingScheduledTransaction() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.getScheduleID(transactionBody, payer)).willReturn(Optional.of(schedule));
        given(store.addSigners(
                eq(schedule),
                argThat(jKeySet -> true))).willReturn(OK);

        // when:
        subject.doStateTransition();

        // then:
        verify(store).getScheduleID(transactionBody, payer);
        // and:
        verify(store, never()).createProvisionally( eq(transactionBody),
                eq(payer),
                eq(payer),
                eq(RichInstant.fromJava(now)),
                argThat(jKey -> true));
        // and:
        verify(store).commitCreation();
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    public void capturesFailingCreateProvisionally() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.getScheduleID(transactionBody, payer)).willReturn(EMPTY_SCHEDULE);
        given(store.createProvisionally(
                eq(transactionBody),
                eq(payer),
                eq(payer),
                eq(RichInstant.fromJava(now)),
                argThat(jKey -> true)))
                .willReturn(CreationResult.failure(INVALID_ADMIN_KEY));

        // when:
        subject.doStateTransition();

        // then:
        verify(store).getScheduleID(transactionBody, payer);
        // and:
        verify(store).createProvisionally(
                eq(transactionBody),
                eq(payer),
                eq(payer),
                eq(RichInstant.fromJava(now)),
                argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())));
        verify(store, never()).addSigners(schedule, jKeySet);
        verify(store, never()).commitCreation();
        verify(txnCtx, never()).setStatus(SUCCESS);
    }

    @Test
    public void setsFailInvalidIfUnhandledException() {
        givenValidTxnCtx();
        // and:
        given(store.getScheduleID(transactionBody, payer)).willThrow(IllegalArgumentException.class);

        // when:
        subject.doStateTransition();

        // then:
        verify(store).getScheduleID(transactionBody, payer);
        // and:
        verify(txnCtx).setStatus(FAIL_INVALID);
    }

    @Test
    public void failsOnInvalidAdminKey() {
        givenCtx(
                true,
                false);

        // expect:
        assertEquals(INVALID_ADMIN_KEY, subject.validate(scheduleCreateTxn));
    }

    @Test
    public void acceptsValidTxn() {
        givenValidTxnCtx();

        assertEquals(OK, subject.syntaxCheck().apply(scheduleCreateTxn));
    }

    @Test
    public void rejectsInvalidAdminKey() {
        givenCtx(true, false);

        assertEquals(INVALID_ADMIN_KEY, subject.syntaxCheck().apply(scheduleCreateTxn));
    }

    private void givenValidTxnCtx() {
        givenCtx(
                false,
                false);
    }

    private void givenCtx(
            boolean invalidAdminKey,
            boolean invalidPubKey
            ) {
        var pair = new KeyPairGenerator().generateKeyPair();
        byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
        if (invalidPubKey) {
            pubKey = "asd".getBytes();
        }
        this.sigMap = SignatureMap.newBuilder().addSigPair(
                SignaturePair.newBuilder()
                        .setPubKeyPrefix(ByteString.copyFrom(pubKey))
        ).build();

        try {
            jAdminKey = asUsableFcKey(key);
            jKeySet = new HashSet<>();
            for (SignaturePair signaturePair : this.sigMap.getSigPairList()) {
                    jKeySet.add(KeysHelper.ed25519ToJKey(signaturePair.getPubKeyPrefix()));
            }
        } catch (DecoderException e) {
            e.printStackTrace();
        }

        var builder = TransactionBody.newBuilder();
        var scheduleCreate = ScheduleCreateTransactionBody.newBuilder()
                .setSigMap(sigMap)
                .setAdminKey(key)
                .setPayerAccountID(payer)
                .setTransactionBody(ByteString.copyFrom(transactionBody));

        if (invalidAdminKey) {
            scheduleCreate.setAdminKey(invalidKey);
        }
        builder.setScheduleCreate(scheduleCreate);

        this.scheduleCreateTxn = builder.build();
        given(accessor.getTxn()).willReturn(this.scheduleCreateTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.activePayer()).willReturn(payer);
        given(txnCtx.consensusTime()).willReturn(now);
        given(store.isCreationPending()).willReturn(true);
    }
}
