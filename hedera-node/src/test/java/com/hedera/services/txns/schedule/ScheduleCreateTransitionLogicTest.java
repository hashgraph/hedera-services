package com.hedera.services.txns.schedule;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.schedules.ScheduleStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import proto.ScheduleCreate;

import java.time.Instant;

import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
public class ScheduleCreateTransitionLogicTest {
    long thisSecond = 1_234_567L;
    private Instant now = Instant.ofEpochSecond(thisSecond);
    private OptionValidator validator;
    private ScheduleStore store;
    private HederaLedger ledger;
    private PlatformTxnAccessor accessor;
    private TransactionContext txnCtx;

    private AccountID payer = IdUtils.asAccount("1.2.3");
    private AccountID signer = IdUtils.asAccount("0.0.2");
    private AccountID anotherSigner = IdUtils.asAccount("0.0.3");

    private TransactionBody scheduleCreateTxn;
    final private Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();

    private final boolean no = false;
    private final boolean yes = true;

    private SignatureMap sigMap;
    private ScheduleCreate.ThresholdAccounts signers;

    private ScheduleCreateTransitionLogic subject;

    @BeforeEach
    private void setup() {
        validator = mock(OptionValidator.class);
        store = mock(ScheduleStore.class);
        ledger = mock(HederaLedger.class);
        accessor = mock(PlatformTxnAccessor.class);

        txnCtx = mock(TransactionContext.class);
        given(txnCtx.activePayer()).willReturn(payer);
        given(txnCtx.consensusTime()).willReturn(Instant.now());

        subject = new ScheduleCreateTransitionLogic(validator, store, ledger, txnCtx);
    }

    @Test
    public void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(scheduleCreateTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    private void givenValidTxnCtx() {
        sigMap = SignatureMap.newBuilder().addSigPair(SignaturePair.newBuilder().build()).build();
        signers = ScheduleCreate.ThresholdAccounts.newBuilder()
                .addAccounts(signer)
                .addAccounts(anotherSigner)
                .build();

        var builder = TransactionBody.newBuilder()
                .setScheduleCreation(ScheduleCreate.ScheduleCreateTransactionBody.newBuilder()
                        .setAdminKey(key)
                        .setSigMap(sigMap)
                        .setSigners(signers)
                        .setExecuteImmediately(no));

        scheduleCreateTxn = builder.build();
        given(accessor.getTxn()).willReturn(scheduleCreateTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(now);
    }
}
