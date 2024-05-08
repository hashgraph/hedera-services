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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.common;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.isDelegateCall;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.frame.MessageFrame;

@Singleton
public class CallAddressChecks {
    @Inject
    public CallAddressChecks() {
        // Dagger2
    }

    /**
     * Checks if the given frame's parent is a delegate call.
     *
     * @param frame the frame to check
     * @return true if the frame's parent is a delegate call
     */
    public boolean hasParentDelegateCall(@NonNull final MessageFrame frame) {
        return frame.getMessageFrameStack().size() > 1 && isDelegateCall(parentOf(frame));
    }

    private MessageFrame parentOf(@NonNull final MessageFrame frame) {
        final var iter = frame.getMessageFrameStack().iterator();
        iter.next();
        return iter.next();
    }
}
