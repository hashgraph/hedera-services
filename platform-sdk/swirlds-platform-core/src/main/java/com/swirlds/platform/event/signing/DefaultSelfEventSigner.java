/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.signing;

import com.swirlds.common.crypto.Signature;
import com.swirlds.common.event.PlatformEvent;
import com.swirlds.common.system.event.UnsignedEvent;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A default implementation of {@link SelfEventSigner}.
 */
public class DefaultSelfEventSigner implements SelfEventSigner {

    private final KeysAndCerts keysAndCerts;

    /**
     * Constructor.
     *
     * @param keysAndCerts the platform's keys and certificates
     */
    public DefaultSelfEventSigner(@NonNull final KeysAndCerts keysAndCerts) {
        this.keysAndCerts = Objects.requireNonNull(keysAndCerts);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PlatformEvent signEvent(@NonNull final UnsignedEvent event) {
        final Signature signature = new PlatformSigner(keysAndCerts).sign(event.getHash());
        return new PlatformEvent(event, signature.getBytes());
    }
}
