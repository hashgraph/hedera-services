// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.eventhandling;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.event.PlatformEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Performs the prehandling of transactions
 */
public interface TransactionPrehandler {
    /**
     * Prehandles application transactions
     *
     * @param event the event to prehandle
     */
    @InputWireLabel("PlatformEvent")
    void prehandleApplicationTransactions(@NonNull PlatformEvent event);
}
