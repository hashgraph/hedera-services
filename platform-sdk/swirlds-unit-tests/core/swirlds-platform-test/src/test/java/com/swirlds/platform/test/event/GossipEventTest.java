package com.swirlds.platform.test.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.platform.event.GossipEvent;
import com.hedera.pbj.runtime.ParseException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.io.InputOutputStream;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class GossipEventTest {

    @Test
    void gossipEventSerializationTest() throws IOException, ParseException {
        final Randotron r = Randotron.create();
        final Hash serializable = r.nextHash();
        final GossipEvent original = new TestingEventBuilder(r)
                .setAppTransactionCount(2)
                .setSystemTransactionCount(1)
                .setSelfParent(new TestingEventBuilder(r).build())
                .setOtherParent(new TestingEventBuilder(r).build())
                .build().getGossipEvent();

        try (final InputOutputStream io = new InputOutputStream()) {
            io.getOutput().writeSerializable(serializable, true);
            io.getOutput().writePbjRecord(original, GossipEvent.PROTOBUF);
            io.getOutput().writeSerializable(serializable, false);
            io.getOutput().writePbjRecord(original, GossipEvent.PROTOBUF);

            io.startReading();

            final Hash readSer1 = io.getInput().readSerializable(true, Hash::new);
            final GossipEvent deserialized1 = io.getInput().readPbjRecord(GossipEvent.PROTOBUF);
            final Hash readSer2 = io.getInput().readSerializable(false, Hash::new);
            final GossipEvent deserialized2 = io.getInput().readPbjRecord(GossipEvent.PROTOBUF);

            assertEquals(serializable, readSer1, "the serializable object should be the same as the one written");
            assertEquals(original, deserialized1, "the event should be the same as the one written");
            assertEquals(serializable, readSer2, "the serializable object should be the same as the one written");
            assertEquals(original, deserialized2, "the event should be the same as the one written");
        }
    }

}
