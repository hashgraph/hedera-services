package com.hedera.node.app.roster;

import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_KEY;
import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_STATES_KEY;

import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.test.fixtures.MapWritableKVState;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

class RosterStoreTestBase {
    private ReadableRosterStoreImpl subject;


   protected MapWritableKVState<ProtoBytes, Roster> newRosters1And2() {
       return MapWritableKVState.<ProtoBytes, Roster>builder(ROSTER_KEY)
               .value(ProtoBytes.newBuilder().value(Bytes.wrap(ROSTER_1_HASH)).build(), ROSTER_1)
               .value(ProtoBytes.newBuilder().value(Bytes.wrap(ROSTER_2_HASH)).build(), ROSTER_2)
               .build();
   }

   protected WritableSingletonState<RosterState> newCurrentRosters() {
       return new TestableSingletonState<>(RosterState.newBuilder().roundRosterPairs(
               RoundRosterPair.newBuilder().roundNumber(ROUND_20).activeRosterHash(
                       Bytes.wrap(ROSTER_2_HASH)).build()).candidateRosterHash(
               Bytes.wrap(ROSTER_1_HASH)).build());
    }

    protected MapWritableStates newDefault() {
        return from(newRosters1And2(), newCurrentRosters());
    }

    protected static MapWritableStates from(final MapWritableKVState<ProtoBytes, Roster> rosters, final WritableSingletonState<RosterState> currentRosters) {
        return new MapWritableStates(Map.of(ROSTER_KEY, rosters, ROSTER_STATES_KEY, currentRosters));
    }

    protected static byte[] sha384(final byte[] bytes) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-384").digest(bytes);
    }

    protected static final Roster ROSTER_1 = Roster.newBuilder().rosterEntries(
            RosterEntry.newBuilder().nodeId(1).build(),
            RosterEntry.newBuilder().nodeId(2).build(),
            RosterEntry.newBuilder().nodeId(3).build()
    ).build();
    protected static final Roster ROSTER_2 = Roster.newBuilder().rosterEntries(
            RosterEntry.newBuilder().nodeId(4).build(),
            RosterEntry.newBuilder().nodeId(5).build(),
            RosterEntry.newBuilder().nodeId(6).build()
    ).build();

    protected static final byte[] ROSTER_1_HASH;
    protected static final byte[] ROSTER_2_HASH;
    static {
        try {
            ROSTER_1_HASH = sha384(Roster.PROTOBUF.toBytes(ROSTER_1).toByteArray());
            ROSTER_2_HASH = sha384(Roster.PROTOBUF.toBytes(ROSTER_2).toByteArray());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    protected static final int ROUND_20 = 20;
    protected static class TestableSingletonState<T> implements ReadableSingletonState<T>, WritableSingletonState<T> {


       private T value;
       private int readCount = 0;
        private boolean isModified = false;

        TestableSingletonState(T value) {
           this.value = value;
       }

       @NonNull
       @Override
       public String getStateKey() {
           return ROSTER_STATES_KEY;
       }

       @Nullable
       @Override
       public T get() {
              readCount++;
           return value;
       }

       @Override
       public boolean isRead() {
           return readCount > 0;
       }

        @Override
        public void put(@Nullable T value) {
           this.isModified = true;
           this.value = value;
        }

        @Override
        public boolean isModified() {
            return false;
        }
    }

}
