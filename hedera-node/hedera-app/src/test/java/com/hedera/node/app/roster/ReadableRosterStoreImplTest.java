package com.hedera.node.app.roster;

import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_KEY;
import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_STATES_KEY;

import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.test.fixtures.MapReadableKVState;
import com.swirlds.state.test.fixtures.MapReadableStates;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

class ReadableRosterStoreImplTest extends RosterStoreTestBase {
    private ReadableRosterStoreImpl subject;

    @BeforeEach
    void setUp() {
        // initialize state:
        subject = new ReadableRosterStoreImpl(newDefault());
    }



    @Test
    @DisplayName("Retrieving a roster by its hash works as expected")
    void getRosterByHash() {
        subject = new ReadableRosterStoreImpl(newDefault());

        var actual = subject.getRoster(ROSTER_1_HASH);
        Assertions.assertThat(actual).isEqualTo(ROSTER_1);
    }

    @Test
    @DisplayName("Retrieving a roster that doesn't exist returns null")
    void getRosterNotFound() {
        subject = new ReadableRosterStoreImpl(new MapReadableStates(Map.of(ROSTER_KEY, new MapReadableKVState<ProtoBytes, Roster>(ROSTER_KEY, Map.of()), ROSTER_STATES_KEY,
                new TestableSingletonState<RosterState>(null))));

        var actual = subject.getRoster(ROSTER_1_HASH);
        Assertions.assertThat(actual).isNull();
    }

   @Test
   @DisplayName("Null roster hash throws an exception")
   void rosterHashMustBeNonNull() {
       //noinspection DataFlowIssue
       Assertions.assertThatThrownBy(() -> new ReadableRosterStoreImpl(newDefault()).getRoster(null))
                .isInstanceOf(NullPointerException.class);
   }

   @Test
   @DisplayName("Getting the candidate roster works as expected")
   void getExistingCandidateRoster() {
        final var subject = new ReadableRosterStoreImpl(newDefault());

        final var actual = subject.getCandidateRoster();
        Assertions.assertThat(actual).isEqualTo(ROSTER_1);
   }

   @Test
   @DisplayName("Getting the candidate roster when it's missing throws an exception")
   void getMissingCandidateRoster() {
        final var invalidCurrentState = RosterState.newBuilder().candidateRosterHash(Bytes.EMPTY).roundRosterPairs(RoundRosterPair.newBuilder().roundNumber(10).activeRosterHash(Bytes.wrap(ROSTER_2_HASH)).build()).build();
        final var subject = new ReadableRosterStoreImpl(from(newRosters1And2(),
                new TestableSingletonState<>(invalidCurrentState)));

        Assertions.assertThatThrownBy(subject::getCandidateRoster)
                .isInstanceOf(IllegalStateException.class);
   }

   @Test
   @DisplayName("Getting an active roster by an existing round works as expected")
   void getActiveRoster() {
       final var subject = new ReadableRosterStoreImpl(newDefault());

       final var actual = subject.getActiveRoster(ROUND_20);
       Assertions.assertThat(actual).isEqualTo(ROSTER_2);
   }

   @Test
   @DisplayName("Getting an active roster by an unmapped round number throws an exception")
   void getActiveRosterMissing() {
        final var subject = new ReadableRosterStoreImpl(newDefault());

        // Round 10 doesn't have an active round in our test data
        Assertions.assertThatThrownBy(() -> subject.getActiveRoster(10))
                .isInstanceOf(IllegalStateException.class);
   }
}
