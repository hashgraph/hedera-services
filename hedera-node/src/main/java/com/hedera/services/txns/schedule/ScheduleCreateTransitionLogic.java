package com.hedera.services.txns.schedule;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.schedules.ScheduleStore;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.txns.validation.ScheduleChecks.checkAdminKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class ScheduleCreateTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(ScheduleCreateTransitionLogic.class);

    private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

    private final OptionValidator validator;
    private final ScheduleStore store;
    private final HederaLedger ledger;
    private final TransactionContext txnCtx;

    public ScheduleCreateTransitionLogic(
            OptionValidator validator,
            ScheduleStore store,
            HederaLedger ledger,
            TransactionContext txnCtx) {
        this.validator = validator;
        this.store = store;
        this.ledger = ledger;
        this.txnCtx = txnCtx;
    }

    @Override
    public void doStateTransition() {
        try {
            transitionFor(txnCtx.accessor().getTxn().getScheduleCreate());
        } catch (Exception e) {
            log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxn4Log(), e);
            abortWith(FAIL_INVALID);
        }
    }

    private void transitionFor(ScheduleCreateTransactionBody op) {
        // TODO: Implement transitionFor() functionality
        throw new UnsupportedOperationException();
    }

    private void abortWith(ResponseCodeEnum cause) {
        // TODO: Implement abortWith() failure functionality
        throw new UnsupportedOperationException();
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasScheduleCreate;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
        return SYNTAX_CHECK;
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        var validity = OK;

        ScheduleCreateTransactionBody op = txnBody.getScheduleCreate();

        validity = checkAdminKey(
                op.hasAdminKey(), op.getAdminKey()
        );
        if (validity != OK) {
            return validity;
        }

        return validity;
    }
}
