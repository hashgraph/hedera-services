/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.keys;

import com.google.protobuf.ByteString;
import com.hedera.node.app.keys.impl.HederaEd25519Key;
import com.hedera.node.app.keys.impl.HederaKeys;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.node.app.keys.impl.HederaKeys.asHederaKey;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class HederaKeysTest {
    @Test
    void canConvertToHederaKey() {
        final var input =
                Key.newBuilder()
                        .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                        .build();

        final var subject = asHederaKey(input);

        assertTrue(subject.isPresent());

        final var key = subject.get();
        Assertions.assertTrue(key.isPrimitive());
    }

    @Test
    void returnsEmptyIfKeyInvalid() {
        var input =
                Key.newBuilder()
                        .setEd25519(ByteString.copyFromUtf8("test"))
                        .build();

        var subject = asHederaKey(input);

        assertFalse(subject.isPresent());

        input = TxnUtils.nestKeys(Key.newBuilder(), 20).build();
        subject = asHederaKey(input);

        assertFalse(subject.isPresent());
    }

    @Test
    void toBeImplementedSolutions() {
        final var input =
                Key.newBuilder()
                        .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                        .build();
        final var key = new HederaEd25519Key();

        assertThrows(NotImplementedException.class, () -> HederaKeys.fromProto(input, 1));
        assertThrows(NotImplementedException.class, () -> HederaKeys.toProto(key));
    }
}
