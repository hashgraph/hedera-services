package com.swirlds.platform.gui.internal;

import com.swirlds.platform.state.signed.ReservedSignedState;

record StateInfo(long round, int numSigs, boolean isComplete) {
    static StateInfo from(final ReservedSignedState state) {
        return new StateInfo(state.get().getRound(), state.get().getSigSet().size(), state.get().isComplete());
    }
}
