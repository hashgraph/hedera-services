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
package com.swirlds.platform.bls.protocol;

import com.swirlds.platform.bls.message.ProtocolMessage;
import java.util.List;

/** A functional interface representing a single round of a {@link BlsProtocol} */
@FunctionalInterface
public interface BlsProtocolRound {
    /**
     * A function which executes a single protocol round
     *
     * @param inputMessages the messages required as input for the protocol round
     * @return the message produced by the protocol round
     */
    ProtocolMessage execute(List<ProtocolMessage> inputMessages);
}
