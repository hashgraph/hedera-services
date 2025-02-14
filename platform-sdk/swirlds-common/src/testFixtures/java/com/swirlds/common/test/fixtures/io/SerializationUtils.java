// SPDX-License-Identifier: Apache-2.0
package com.swirlds.common.test.fixtures.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.io.SelfSerializable;
import java.io.IOException;

public class SerializationUtils {

    public static <T extends SelfSerializable> T serializeDeserialize(T ss) throws IOException {
        try (InputOutputStream io = new InputOutputStream()) {
            io.getOutput().writeSerializable(ss, true);
            io.startReading();
            return io.getInput().readSerializable();
        }
    }

    public static <T extends SelfSerializable> void checkSerializeDeserializeEqual(T ss) throws IOException {
        assertEquals(ss, serializeDeserialize(ss));
    }
}
