/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.chatter;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.platform.chatter.protocol.MessageHandler;
import com.swirlds.platform.event.GossipEvent;

/**
 * Prepares a {@link GossipEvent} received from a peer for further handling
 */
public class PrepareChatterEvent implements MessageHandler<GossipEvent> {
    private final Cryptography cryptography;

    public PrepareChatterEvent(final Cryptography cryptography) {
        this.cryptography = cryptography;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleMessage(final GossipEvent event) {
        final BaseEventHashedData hashedData = event.getHashedData();
        cryptography.digestSync(hashedData);
        event.buildDescriptor();
    }
}
