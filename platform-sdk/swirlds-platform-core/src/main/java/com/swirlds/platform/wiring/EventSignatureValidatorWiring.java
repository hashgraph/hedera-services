/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.validation.AddressBookUpdate;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link EventSignatureValidator}.
 *
 * @param eventInput                 the input wire for events with unvalidated signatures
 * @param nonAncientEventWindowInput the input wire for the minimum non-ancient threshold
 * @param addressBookUpdateInput     the input wire for address book updates
 * @param eventOutput                the output wire for events with validated signatures
 * @param flushRunnable              the runnable to flush the validator
 */
public record EventSignatureValidatorWiring(
        @NonNull InputWire<GossipEvent> eventInput,
        @NonNull InputWire<NonAncientEventWindow> nonAncientEventWindowInput,
        @NonNull InputWire<AddressBookUpdate> addressBookUpdateInput,
        @NonNull OutputWire<GossipEvent> eventOutput,
        @NonNull Runnable flushRunnable) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this validator
     * @return the new wiring instance
     */
    public static EventSignatureValidatorWiring create(@NonNull final TaskScheduler<GossipEvent> taskScheduler) {
        return new EventSignatureValidatorWiring(
                taskScheduler.buildInputWire("events with unvalidated signatures"),
                taskScheduler.buildInputWire("non-ancient event window"),
                taskScheduler.buildInputWire("address book update"),
                taskScheduler.getOutputWire(),
                taskScheduler::flush);
    }

    /**
     * Bind a signature validator to this wiring.
     *
     * @param eventSignatureValidator the event signature validator to bind
     */
    public void bind(@NonNull final EventSignatureValidator eventSignatureValidator) {
        ((BindableInputWire<GossipEvent, GossipEvent>) eventInput).bind(eventSignatureValidator::validateSignature);
        ((BindableInputWire<NonAncientEventWindow, GossipEvent>) nonAncientEventWindowInput)
                .bindConsumer(eventSignatureValidator::setNonAncientEventWindow);
        ((BindableInputWire<AddressBookUpdate, GossipEvent>) addressBookUpdateInput)
                .bindConsumer(eventSignatureValidator::updateAddressBooks);
    }
}
