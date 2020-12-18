package com.hedera.services.txns.schedule;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.suites.utils.keypairs.Ed25519PrivateKey;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.Optional;

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
    private final CreationResult<ScheduleID> EMPTY_CREATION_RESULT = null;
    private final Optional<ScheduleID> EMPTY_SCHEDULE = Optional.empty();
    private final Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
    private final Key invalidKey = Key.newBuilder().build();
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
    private CreationResult<ScheduleID> scheduleCreationResult = CreationResult.success(schedule);

    private TransactionBody scheduleCreateTxn;
    private SignatureMap sigMap;

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

        // and:
        given(store.getScheduleIDByTransactionBody(any())).willReturn(EMPTY_SCHEDULE);
        given(store.createProvisionally(any(), any(), any(), any(), any())).willReturn(scheduleCreationResult);
        given(store.addSigners(any(), any())).willReturn(OK);

        // when:
        subject.doStateTransition();

        // then:
        verify(store).createProvisionally(any(), any(), any(), any(), any());
        verify(store).addSigners(any(), any());
        verify(store).commitCreation();
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    public void capturesPendingScheduledTransaction() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.getScheduleIDByTransactionBody(any())).willReturn(Optional.of(schedule));

        // when:
        subject.doStateTransition();

        // then:
        verify(store, never()).createProvisionally(any(), any(), any(), any(), any());
        verify(store).addSigners(eq(schedule), any());
        verify(store, never()).commitCreation();
        verify(txnCtx, never()).setStatus(SUCCESS);
    }

    @Test
    public void capturesFailingCreateProvisionally() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.getScheduleIDByTransactionBody(any())).willReturn(EMPTY_SCHEDULE);
        given(store.createProvisionally(any(), any(), any(), any(), any())).willReturn(EMPTY_CREATION_RESULT);

        subject.doStateTransition();

        // then:
        verify(store).createProvisionally(any(), any(), any(), any(), any());
        verify(store, never()).addSigners(any(), any());
        verify(store, never()).commitCreation();
        verify(txnCtx, never()).setStatus(SUCCESS);
    }

    @Test
    public void capturesFailingSignersAddition() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.getScheduleIDByTransactionBody(any())).willReturn(EMPTY_SCHEDULE);
        given(store.createProvisionally(any(), any(), any(), any(), any())).willReturn(CreationResult.success(schedule));
        given(store.addSigners(any(), any())).willReturn(NOT_OK);

        subject.doStateTransition();

        // then:
        verify(store).createProvisionally(any(), any(), any(), any(), any());
        verify(store).addSigners(any(), any());
        verify(store, never()).commitCreation();
        verify(txnCtx, never()).setStatus(SUCCESS);
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
                        .build()
        ).build();

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
        var c = scheduleCreate.build();
        builder.setScheduleCreation(c);

        this.scheduleCreateTxn = builder.build();
        given(accessor.getTxn()).willReturn(this.scheduleCreateTxn);
        given(txnCtx.accessor()).willReturn(accessor);
    }
}
