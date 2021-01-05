package com.hedera.services.txns.schedule;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ScheduleDeleteTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(ScheduleCreateTransitionLogic.class);

    private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

    ScheduleStore store;
    TransactionContext txnCtx;

    public ScheduleDeleteTransitionLogic(
            ScheduleStore store,
            TransactionContext txnCtx) {
        this.store = store;
        this.txnCtx = txnCtx;
    }

    @Override
    public void doStateTransition() {
        try {
            transitionFor(txnCtx.accessor().getTxn().getScheduleDelete());
        } catch (Exception e) {
            log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxn4Log(), e);
            txnCtx.setStatus(FAIL_INVALID);
        }
    }

    private void transitionFor(ScheduleDeleteTransactionBody op) {
        var outcome = store.delete(op.getSchedule());
        txnCtx.setStatus((outcome == OK) ? SUCCESS : outcome);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasScheduleDelete;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
        return SYNTAX_CHECK;
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        ScheduleDeleteTransactionBody op = txnBody.getScheduleDelete();

        if (!op.hasSchedule()) {
            return INVALID_SCHEDULE_ID;
        }
        return OK;
    }
}
