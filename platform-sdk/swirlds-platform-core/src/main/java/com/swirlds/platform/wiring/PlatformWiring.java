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
import com.swirlds.common.wiring.WiringModel;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.validation.EventValidator;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Encapsulates wiring for {@link com.swirlds.platform.SwirldsPlatform}.
 */
public class PlatformWiring {

    // Note: this class is currently a placeholder; it's not functional in its current form.
    //       As we migrate the platform into the new framework, we should expand this class.

    private final WiringModel model;

    private final EventSignatureValidationScheduler eventSignatureValidationScheduler;
    private final OrphanBufferScheduler orphanBufferScheduler;
    private final boolean cyclicalBackpressurePresent;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param time            provides wall clock time
     */
    public PlatformWiring(@NonNull final PlatformContext platformContext, @NonNull final Time time) {
        model = WiringModel.create(platformContext, time);

        orphanBufferScheduler = new OrphanBufferScheduler(model);
        eventSignatureValidationScheduler = new EventSignatureValidationScheduler(model);

        wire();

        // Logs if there is cyclical back pressure.
        // Do not throw -- in theory we might survive this, so no need to crash.
        cyclicalBackpressurePresent = model.checkForCyclicalBackpressure();
    }

    /**
     * Get the wiring model.
     *
     * @return the wiring model
     */
    @NonNull
    public WiringModel getModel() {
        return model;
    }

    /**
     * Check if cyclical backpressure is present in the model.
     *
     * @return true if cyclical backpressure is present, false otherwise
     */
    public boolean isCyclicalBackpressurePresent() {
        return cyclicalBackpressurePresent;
    }

    /**
     * Wire the components together.
     */
    private void wire() {
        eventSignatureValidationScheduler.getEventOutput().solderTo(orphanBufferScheduler.getEventInput());
        // FUTURE WORK: solder all the things!
    }

    /**
     * Bind an orphan buffer to this wiring.
     *
     * @param orphanBuffer the orphan buffer to bind
     */
    public void bind(@NonNull final EventValidator eventValidator, @NonNull final OrphanBuffer orphanBuffer) {
        eventSignatureValidationScheduler.bind(eventValidator);
        orphanBufferScheduler.bind(orphanBuffer);
    }
}
