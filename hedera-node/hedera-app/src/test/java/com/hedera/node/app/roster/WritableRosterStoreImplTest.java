package com.hedera.node.app.roster;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;

class WritableRosterStoreImplTest extends RosterStoreTestBase {
    private static final Roster ROSTER_3 = Roster.newBuilder().rosterEntries(
            RosterEntry.newBuilder().nodeId(7).build(),
            RosterEntry.newBuilder().nodeId(8).build(),
            RosterEntry.newBuilder().nodeId(9).build())
            .build();
    private static final Bytes ROSTER_3_HASH;
    static {
        try {
            ROSTER_3_HASH = Bytes.wrap(sha384(Roster.PROTOBUF.toBytes(ROSTER_3).toByteArray()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("")
    void putsNewNonCandidateRoster() {
        final var subject = new WritableRosterStoreImpl(newDefault());

        subject.putRoster(ROSTER_3, false);
        final var retrieved = subject.getRoster(ROSTER_3_HASH.toByteArray());
        Assertions.assertThat(retrieved).isEqualTo(ROSTER_3);
        Assertions.assertThat(subject.getCandidateRoster()).isNotEqualTo(ROSTER_3);
    }

    @Test
    @DisplayName("")
    void putsNewCandidateRoster() {
        final var subject = new WritableRosterStoreImpl(newDefault());

        subject.putRoster(ROSTER_3, true);
        final var retrieved = subject.getRoster(ROSTER_3_HASH.toByteArray());
        Assertions.assertThat(retrieved).isEqualTo(ROSTER_3);
        Assertions.assertThat(subject.getCandidateRoster()).isEqualTo(ROSTER_3);
    }

    @Test
    @DisplayName("")
    void putsExistingCandidateRoster() {
        //???
    }

    @Test
    @DisplayName("")
    void failsOnNullRoster() {
        final var subject = new WritableRosterStoreImpl(newDefault());

        //noinspection DataFlowIssue
        Assertions.assertThatThrownBy(() -> subject.putRoster(null, false))
                .isInstanceOf(NullPointerException.class);
    }
}
