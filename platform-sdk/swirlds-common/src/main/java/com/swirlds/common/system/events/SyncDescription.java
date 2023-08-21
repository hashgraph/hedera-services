package com.swirlds.common.system.events;

import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;

public record SyncDescription(
        long myMinimumGenerationNonExpired,
        long myMinimumGenerationNonAncient,
        @NonNull NodeId peerId,
        long peerMinimumGenerationNonExpired,
        long peerMinimumGenerationNonAncient) {



}
