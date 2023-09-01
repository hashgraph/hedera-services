package com.swirlds.platform.recovery.internal;

import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;

public record RecoveredState(@NonNull ReservedSignedState state, @NonNull GossipEvent judge) {
}
