package com.hedera.node.app.roster;

import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.RosterStateId;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

public interface ReadableRosterStore {
    @Nullable
    Roster getCandidateRoster();

    Roster getActiveRoster();
    Roster get(ProtoBytes protoBytes);

}
