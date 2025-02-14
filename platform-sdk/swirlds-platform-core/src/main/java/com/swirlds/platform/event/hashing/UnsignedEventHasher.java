// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.event.hashing;

import com.swirlds.platform.system.events.UnsignedEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Hashes unsigned events.
 */
public interface UnsignedEventHasher {

    /**
     * Hashes the event and builds the event descriptor.
     *
     * @param event the event to hash
     */
    void hashUnsignedEvent(@NonNull final UnsignedEvent event);
}
