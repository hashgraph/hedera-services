package com.swirlds.platform.event.runninghash;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

class RunningEventHasherTests {

    // TODO
    //  - sequence of regular rounds produce deterministic results
    //  - different initial hash (empty/full)
    //  - different round number (empty/full)
    //  - different event order
    //  - different event time stamp (nanos and seconds)
    //  - different event base hash
    //  - missing event
    //  - extra event


    private static ConsensusRound buildRound(final long roundNumber, @NonNull final List<EventImpl> events) {
        final ConsensusSnapshot snapshot = mock(ConsensusSnapshot.class);
        when(snapshot.round()).thenReturn(roundNumber);

        return new ConsensusRound(
                mock(AddressBook.class),
                events,
                mock(EventImpl.class),
                mock(Generations.class),
                mock(NonAncientEventWindow.class),
                snapshot);
    }

}
