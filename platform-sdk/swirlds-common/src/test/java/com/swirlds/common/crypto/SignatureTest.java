/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.fixtures.io.InputOutputStream;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SignatureTest {
    @BeforeAll
    public static void setUp() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.common.crypto");
    }

    @Test
    public void serializeDeserializeTest() throws IOException {
        SignatureType signatureType = SignatureType.RSA;
        byte[] sigBytes = new byte[signatureType.signatureLength()];
        ThreadLocalRandom.current().nextBytes(sigBytes);
        Signature signature = new Signature(signatureType, sigBytes);
        Signature deserialized = serializeDeserialize(signature);
        assertEquals(signature, deserialized);
    }

    private Signature serializeDeserialize(final Signature signature) throws IOException {
        try (final InputOutputStream io = new InputOutputStream()) {
            signature.serialize(io.getOutput(), true);
            io.startReading();
            return Signature.deserialize(io.getInput(), true);
        }
    }
}
