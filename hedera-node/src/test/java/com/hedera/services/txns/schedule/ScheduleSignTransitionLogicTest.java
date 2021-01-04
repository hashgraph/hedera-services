package com.hedera.services.txns.schedule;

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.KeysHelper;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
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

import java.util.HashSet;
import java.util.Set;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KEY_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
public class ScheduleSignTransitionLogicTest {
    private ScheduleStore store;
    private PlatformTxnAccessor accessor;
    private TransactionContext txnCtx;

    private TransactionBody scheduleSignTxn;

    private SignatureMap.Builder sigMap;
    private Set<JKey> jKeySet;

    private ScheduleSignTransitionLogic subject;
    private ScheduleID schedule = IdUtils.asSchedule("1.2.3");

    @BeforeEach
    private void setup() {
        store = mock(ScheduleStore.class);
        accessor = mock(PlatformTxnAccessor.class);

        txnCtx = mock(TransactionContext.class);

        subject = new ScheduleSignTransitionLogic(store, txnCtx);
    }

    @Test
    public void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(scheduleSignTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    public void followsHappyPath() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.addSigners(eq(schedule), argThat(jKeySet -> true))).willReturn(OK);

        // when:
        subject.doStateTransition();

        // then
        verify(store).addSigners(eq(schedule), argThat((Set<JKey> set) -> {
            assertEquals(set.size(), jKeySet.size());
            var setIterator = set.iterator();
            var jKeySetIterator = set.iterator();
            while (setIterator.hasNext()) {
                assertTrue(equalUpToDecodability(setIterator.next(), jKeySetIterator.next()));
            }
            return true;
        }));
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    public void setsFailInvalidIfUnhandledException() {
        givenValidTxnCtx();
        // and:
        given(store.addSigners(eq(schedule), any())).willThrow(IllegalArgumentException.class);

        // when:
        subject.doStateTransition();

        // then:
        verify(store).addSigners(eq(schedule), any());
        // and:
        verify(txnCtx).setStatus(FAIL_INVALID);
    }

    @Test
    public void capturesInvalidScheduleId() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.addSigners(eq(schedule), any())).willReturn(INVALID_SCHEDULE_ID);

        // when:
        subject.doStateTransition();

        // then
        verify(store).addSigners(eq(schedule), any());
        verify(txnCtx).setStatus(INVALID_SCHEDULE_ID);
    }

    @Test
    public void capturesDeletedSchedule() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.addSigners(eq(schedule), any())).willReturn(SCHEDULE_WAS_DELETED);

        // when:
        subject.doStateTransition();

        // then
        verify(store).addSigners(eq(schedule), any());
        verify(txnCtx).setStatus(SCHEDULE_WAS_DELETED);
    }

    @Test
    public void failsOnInvalidScheduleId() {
        givenCtx(true, false);

        // expect:
        assertEquals(INVALID_SCHEDULE_ID, subject.validate(scheduleSignTxn));
    }

    @Test
    public void failsOnInvalidKeyEncoding() {
        givenCtx(false, true);

        // expect:
        assertEquals(INVALID_KEY_ENCODING, subject.validate(scheduleSignTxn));
    }

    @Test
    public void acceptsValidTxn() {
        givenValidTxnCtx();

        assertEquals(OK, subject.syntaxCheck().apply(scheduleSignTxn));
    }

    @Test
    public void rejectsInvalidScheduleId() {
        givenCtx(true, false);

        assertEquals(INVALID_SCHEDULE_ID, subject.syntaxCheck().apply(scheduleSignTxn));
    }

    @Test
    public void rejectsInvalidKeyEncoding() {
        givenCtx(false, true);

        assertEquals(INVALID_KEY_ENCODING, subject.syntaxCheck().apply(scheduleSignTxn));
    }

    private void givenValidTxnCtx() {
        givenCtx(false, false);
    }

    private void givenCtx(
            boolean invalidScheduleId,
            boolean invalidKeyEncoding
    ) {
        var pair = new KeyPairGenerator().generateKeyPair();
        byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
        this.sigMap = SignatureMap.newBuilder().addSigPair(
                SignaturePair.newBuilder()
                        .setPubKeyPrefix(ByteString.copyFrom(pubKey))
                        .build()
        );

        try {
            jKeySet = new HashSet<>();
            for (SignaturePair signaturePair : this.sigMap.getSigPairList()) {
                jKeySet.add(KeysHelper.ed25519ToJKey(signaturePair.getPubKeyPrefix()));
            }
        } catch (DecoderException e) {
            e.printStackTrace();
        }

        var builder = TransactionBody.newBuilder();
        var scheduleSign = ScheduleSignTransactionBody.newBuilder()
                .setSigMap(sigMap)
                .setSchedule(schedule);

        if (invalidScheduleId) {
            scheduleSign.clearSchedule();
        }

        if (invalidKeyEncoding) {
            this.sigMap.clear().addSigPair(SignaturePair.newBuilder().setEd25519(ByteString.copyFromUtf8("some-invalid-signature")).build());
            scheduleSign.setSigMap(this.sigMap);
        }

        builder.setScheduleSign(scheduleSign);

        scheduleSignTxn = builder.build();
        given(accessor.getTxn()).willReturn(scheduleSignTxn);
        given(txnCtx.accessor()).willReturn(accessor);
    }
}
