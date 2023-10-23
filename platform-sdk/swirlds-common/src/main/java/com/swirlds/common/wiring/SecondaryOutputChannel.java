/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Each wire has a primary output channel. When a component method bound to an intake channel returns data, that data is
 * passed to the primary output channel of the wire. This object provides a way for passing additional data out of a
 * component over additional channels.
 *
 * @param <T> the type of data that is transmitted over this channel
 */
public class SecondaryOutputChannel<T> extends OutputChannel<T> {

    /**
     * Constructor.
     *
     * @param model               the wiring model containing this output channel
     * @param name                the name of the parent wire
     * @param insertionIsBlocking when data is inserted into this channel, will it block until capacity is available?
     */
    protected SecondaryOutputChannel(@NonNull WiringModel model, @NonNull String name, boolean insertionIsBlocking) {
        super(model, name, false, insertionIsBlocking);
    }

    /**
     * Call this to push data out over this channel.
     *
     * <p>
     * It is a violation of convention to invoke this method from anywhere other than within the component being
     * executed on the parent wire. Don't do it.
     *
     * @param data the output data to forward
     */
    @Override
    public void forward(@NonNull final T data) {
        super.forward(data);
    }
}
