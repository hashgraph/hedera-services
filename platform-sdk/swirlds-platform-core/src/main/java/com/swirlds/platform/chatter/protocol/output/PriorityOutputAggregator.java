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

package com.swirlds.platform.chatter.protocol.output;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.metrics.extensions.CountPerSecond;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import java.util.List;

/**
 * Holds a list of message providers in order of priority. When a message is requested, it will check the providers in
 * priority order until it finds one that has messages to be sent.
 */
public class PriorityOutputAggregator implements MessageProvider {
    private final List<MessageProvider> providers;
    private final CountPerSecond msgsPerSec;

    public PriorityOutputAggregator(final List<MessageProvider> providers, final CountPerSecond msgsPerSec) {
        this.providers = providers;
        this.msgsPerSec = msgsPerSec;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SelfSerializable getMessage() {
        for (final MessageProvider provider : providers) {
            final SelfSerializable message = provider.getMessage();
            if (message != null) {
                msgsPerSec.count();
                return message;
            }
        }
        return null;
    }

    @Override
    public void clear() {
        for (final MessageProvider provider : providers) {
            provider.clear();
        }
    }
}
