package com.hedera.services.txns.schedule;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.suites.utils.keypairs.Ed25519PrivateKey;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.KeysHelper;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.hedera.services.keys.KeysHelper.ed25519ToJKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
public class ScheduleCreateTransitionLogicTest {
    long thisSecond = 1_234_567L;
    private Instant now = Instant.ofEpochSecond(thisSecond);

    private final CreationResult<ScheduleID> EMPTY_CREATION_RESULT = null;
    private final Optional<ScheduleID> EMPTY_SCHEDULE = Optional.empty();
    private final Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
    private final Key invalidKey = Key.newBuilder().build();
    private JKey jAdminKey;
    private final boolean NO = false;
    private final boolean YES = true;
    private final ResponseCodeEnum NOT_OK = null;

    private OptionValidator validator;
    private ScheduleStore store;
    private HederaLedger ledger;
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
        validator = mock(OptionValidator.class);
        store = mock(ScheduleStore.class);
        ledger = mock(HederaLedger.class);
        accessor = mock(PlatformTxnAccessor.class);

        txnCtx = mock(TransactionContext.class);
        given(txnCtx.activePayer()).willReturn(payer);

        subject = new ScheduleCreateTransitionLogic(validator, store, ledger, txnCtx);
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
        // given:
        givenValidTxnCtx();

        var result = CreationResult.success(schedule);

        // and:
        given(store.getScheduleID(
                scheduleCreateTxn.getScheduleCreation().toByteArray(),
                payer)).willReturn(EMPTY_SCHEDULE);

        given(store.createProvisionally(
                eq(scheduleCreateTxn.getScheduleCreation().toByteArray()),
                eq(Optional.of(payer)),
                eq(payer),
                eq(RichInstant.fromJava(now)),
                eq(Optional.of(jAdminKey)))).willReturn(result);

        given(store.addSigners(schedule, jKeySet)).willReturn(OK);

        // when:
        subject.doStateTransition();

        // then:
        verify(store).getScheduleID(scheduleCreateTxn.getScheduleCreation().toByteArray(), payer);
        verify(store).createProvisionally(
                eq(scheduleCreateTxn.getScheduleCreation().toByteArray()),
                eq(Optional.of(payer)),
                eq(payer),
                eq(RichInstant.fromJava(now)),
                eq(Optional.of(jAdminKey)));
        verify(store).addSigners(schedule, jKeySet);
        verify(store).commitCreation();
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    public void capturesPendingScheduledTransaction() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.getScheduleID(scheduleCreateTxn.getScheduleCreation().toByteArray(), payer)).willReturn(Optional.of(schedule));

        // when:
        subject.doStateTransition();

        // then:
        verify(store, never()).createProvisionally(any(), any(), any(), any(), any());
        verify(store).addSigners(schedule, jKeySet);
        verify(store, never()).commitCreation();
        verify(txnCtx, never()).setStatus(SUCCESS);
    }

    @Test
    public void capturesFailingCreateProvisionally() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.getScheduleID(scheduleCreateTxn.getScheduleCreation().toByteArray(), payer)).willReturn(EMPTY_SCHEDULE);
        given(store.createProvisionally(
                scheduleCreateTxn.getScheduleCreation().toByteArray(),
                Optional.of(payer),
                payer,
                RichInstant.fromJava(now),
                Optional.of(jAdminKey))).willReturn(EMPTY_CREATION_RESULT);

        subject.doStateTransition();

        // then:
        verify(store).createProvisionally(scheduleCreateTxn.getScheduleCreation().toByteArray(),
                Optional.of(payer),
                payer,
                RichInstant.fromJava(now),
                Optional.of(jAdminKey));
        verify(store, never()).addSigners(any(), any());
        verify(store, never()).commitCreation();
        verify(txnCtx, never()).setStatus(SUCCESS);
    }

    @Test
    public void capturesFailingSignersAddition() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.getScheduleID(any(), any())).willReturn(EMPTY_SCHEDULE);
        given(store.createProvisionally(scheduleCreateTxn.getScheduleCreation().toByteArray(),
                Optional.of(payer),
                payer,
                RichInstant.fromJava(now),
                Optional.of(jAdminKey))).willReturn(CreationResult.success(schedule));
        given(store.addSigners(schedule, jKeySet)).willReturn(NOT_OK);

        subject.doStateTransition();

        // then:
        verify(store).createProvisionally(scheduleCreateTxn.getScheduleCreation().toByteArray(),
                Optional.of(payer),
                payer,
                RichInstant.fromJava(now),
                Optional.of(jAdminKey));
        verify(store).addSigners(schedule, jKeySet);
        verify(store, never()).commitCreation();
        verify(txnCtx).setStatus(NOT_OK);
        verify(store).rollbackCreation();
    }

    @Test
    public void failsOnExecuteImmediatelyFalse() {
        givenCtx(
                true,
                false);

        // expect:
        assertEquals(NOT_SUPPORTED, subject.validate(scheduleCreateTxn));
    }

    @Test
    public void failsOnInvalidAdminKey() {
        givenCtx(
                false,
                true);

        // expect:
        assertEquals(INVALID_ADMIN_KEY, subject.validate(scheduleCreateTxn));
    }

    @Test
    public void acceptsValidTxn() {
        givenValidTxnCtx();

        assertEquals(OK, subject.syntaxCheck().apply(scheduleCreateTxn));
    }

    @Test
    public void rejectsInvalidExecuteImmediately() {
        givenCtx(true, false);

        assertEquals(NOT_SUPPORTED, subject.syntaxCheck().apply(scheduleCreateTxn));
    }

    @Test
    public void rejectsInvalidAdminKey() {
        givenCtx(false, true);

        assertEquals(INVALID_ADMIN_KEY, subject.syntaxCheck().apply(scheduleCreateTxn));
    }

    private void givenValidTxnCtx() {
        givenCtx(
                false,
                false);
    }

    private void givenCtx(
            boolean invalidExecuteImmediately,
            boolean invalidAdminKey
            ) {
        var keyPair = Ed25519PrivateKey.generate();
        this.sigMap = SignatureMap.newBuilder().addSigPair(
                SignaturePair.newBuilder()
                        .setPubKeyPrefix(ByteString.copyFrom(keyPair.getPublicKey().toBytes()))
        ).build();

        try {
            jAdminKey = JKey.mapKey(key);
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
                .setExecuteImmediately(YES)
                .setPayer(payer);

        if (invalidExecuteImmediately) {
            scheduleCreate.setExecuteImmediately(NO);
        }

        if (invalidAdminKey) {
            scheduleCreate.setAdminKey(invalidKey);
        }
        builder.setScheduleCreation(scheduleCreate);

        this.scheduleCreateTxn = builder.build();
        given(accessor.getTxn()).willReturn(this.scheduleCreateTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.activePayer()).willReturn(payer);
        given(txnCtx.consensusTime()).willReturn(now);
        given(store.isCreationPending()).willReturn(true);
    }
}
