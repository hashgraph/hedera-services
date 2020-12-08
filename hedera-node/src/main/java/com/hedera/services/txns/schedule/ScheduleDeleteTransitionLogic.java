package com.hedera.services.txns.schedule;

import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.function.Function;
import java.util.function.Predicate;

// TODO: Implement Deletion Transition Logic
public class ScheduleDeleteTransitionLogic implements TransitionLogic {
    @Override
    public void doStateTransition() {

    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return null;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
        return null;
    }
}
