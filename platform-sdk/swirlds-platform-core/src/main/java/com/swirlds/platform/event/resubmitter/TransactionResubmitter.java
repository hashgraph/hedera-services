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

package com.swirlds.platform.event.resubmitter;

import com.hedera.hapi.platform.event.EventTransaction;
import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.PlatformEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A simple utility responsible for resubmitting stale transactions.
 */
public interface TransactionResubmitter {

    /**
     * Resubmit transactions that have gone stale.
     *
     * @param event the event that has gone stale
     * @return a list of transactions that should be resubmitted
     */
    @InputWireLabel("stale events")
    @NonNull
    List<EventTransaction> resubmitStaleTransactions(@NonNull PlatformEvent event);

    /**
     * Update the current event window. The transaction resubmitter may use this information to decide which
     * transactions are worth resubmitting.
     *
     * @param eventWindow the new event window
     */
    @InputWireLabel("event window")
    void updateEventWindow(@NonNull final EventWindow eventWindow);
}
