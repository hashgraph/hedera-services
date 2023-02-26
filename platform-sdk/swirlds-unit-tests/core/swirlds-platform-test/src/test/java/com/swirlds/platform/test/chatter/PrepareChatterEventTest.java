/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.platform.chatter.PrepareChatterEvent;
import com.swirlds.platform.event.GossipEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrepareChatterEventTest {
    @Mock
    Cryptography cryptography;

    @Mock
    GossipEvent event;

    @Mock
    BaseEventHashedData hashedData;

    @InjectMocks
    PrepareChatterEvent prepareChatterEvent;

    @Test
    void test() {
        Mockito.when(event.getHashedData()).thenReturn(hashedData);
        prepareChatterEvent.handleMessage(event);
        Mockito.verify(cryptography).digestSync(hashedData);
        Mockito.verify(event).buildDescriptor();
    }
}
