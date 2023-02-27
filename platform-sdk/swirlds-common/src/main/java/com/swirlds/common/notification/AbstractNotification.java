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

package com.swirlds.common.notification;

/**
 * Abstract base class provided for convenience of implementing {@link Notification} classes. Provides the basic sequence
 * support as required by the {@link Notification} interface.
 */
public abstract class AbstractNotification implements Notification {

    private long sequence;

    public AbstractNotification() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSequence() {
        return sequence;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSequence(final long id) {
        this.sequence = id;
    }
}
