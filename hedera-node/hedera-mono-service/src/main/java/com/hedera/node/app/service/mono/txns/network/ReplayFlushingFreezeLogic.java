package com.hedera.node.app.service.mono.txns.network;

import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.service.mono.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.function.Function;
import java.util.function.Predicate;

public class ReplayFlushingFreezeLogic implements TransitionLogic {
    public static final String REPLAY_DIR_LOC = "replay-assets";
    private final ProcessLogic processLogic;
    private final FreezeTransitionLogic freezeTransitionLogic;

    public ReplayFlushingFreezeLogic(
            final ProcessLogic processLogic,
            final FreezeTransitionLogic freezeTransitionLogic) {
        this.processLogic = processLogic;
        this.freezeTransitionLogic = freezeTransitionLogic;
    }

    @Override
    public void doStateTransition() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        throw new AssertionError("Not implemented");
    }
}
