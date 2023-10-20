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

package com.swirlds.platform.wiring;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.validation.EventValidator;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Encapsulates wiring for {@link com.swirlds.platform.SwirldsPlatform}.
 */
public class PlatformWiring {

    // Note: this class is currently a placeholder; it's not functional in its current form.
    //       As we migrate the platform into the new framework, we should expand this class.

    private final EventSignatureValidationWire eventSignatureValidationWire;
    private final OrphanBufferWire orphanBufferWire;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param time            provides wall clock time
     */
    public PlatformWiring(@NonNull final PlatformContext platformContext, @NonNull final Time time) {

        orphanBufferWire = new OrphanBufferWire(platformContext, time);
        eventSignatureValidationWire = new EventSignatureValidationWire(platformContext, time);
    }

    /**
     * Wire the components together.
     */
    private void wire() {
        eventSignatureValidationWire.getEventOutput().solderTo(orphanBufferWire.getEventInput());

        // FUTURE WORK: solder all the things!
    }

    /**
     * Bind an orphan buffer to this wiring.
     *
     * @param orphanBuffer the orphan buffer to bind
     */
    public void bind(@NonNull final EventValidator eventValidator, @NonNull final OrphanBuffer orphanBuffer) {

        eventSignatureValidationWire.bind(eventValidator);
        orphanBufferWire.bind(orphanBuffer);
    }
}
