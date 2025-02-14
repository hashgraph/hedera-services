// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.event.signing;

import com.swirlds.component.framework.component.InputWireLabel;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.system.events.UnsignedEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Signs self events.
 */
public interface SelfEventSigner {

    /**
     * Signs an event and then returns it.
     *
     * @param event the event to sign
     * @return the signed event
     */
    @InputWireLabel("self events")
    @NonNull
    PlatformEvent signEvent(@NonNull UnsignedEvent event);
}
