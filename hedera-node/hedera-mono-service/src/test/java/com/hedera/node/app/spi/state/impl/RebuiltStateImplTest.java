/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.state.impl;

import com.google.protobuf.ByteString;
import com.hedera.services.utils.EntityNum;
import com.swirlds.fchashmap.FCHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static com.hedera.node.app.spi.state.StateKeys.ALIASES_STORE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RebuiltStateImplTest {
    private final Instant lastModifiedTime = Instant.ofEpochSecond(1_234_567L);

    private final ByteString alias = ByteString.copyFromUtf8("testAlias");
    private final EntityNum account = EntityNum.fromLong(10001L);

    @Mock private FCHashMap<ByteString, EntityNum> aliases;

    private RebuiltStateImpl subject;

    @BeforeEach
    void setUp() {
        subject = new RebuiltStateImpl<>(ALIASES_STORE, aliases, lastModifiedTime);
    }

    @Test
    void gettersWork() {
        assertEquals(lastModifiedTime, subject.getLastModifiedTime());
        assertEquals(ALIASES_STORE, subject.getStateKey());
    }

    @Test
    void readsValueFromAliasesFcHashMap() {
        given(aliases.get(alias)).willReturn(account);

        assertEquals(Optional.of(account), subject.get(alias));
    }

    @Test
    void cachesReadKeysFromState() {
        final var unknownKey = ByteString.copyFromUtf8("unknown alias");
        given(aliases.get(alias)).willReturn(account);
        assertEquals(Optional.of(account), subject.get(alias));

        verify(aliases).get(alias);
        assertTrue(subject.getReadKeys().containsKey(alias));

        assertEquals(Optional.of(account), subject.get(alias));
        verify(aliases, times(1)).get(alias);

        assertEquals(Optional.empty(), subject.get(unknownKey));
        assertFalse(subject.getReadKeys().containsKey(unknownKey));
    }
}
