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

package com.swirlds.platform.bls.message;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.bls.protocol.CrsProtocol;
import com.swirlds.platform.bls.protocol.RandomGroupElements;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * The type of message sent in the second round of {@link CrsProtocol}, which opens elements previously committed to
 */
public class OpeningMessage extends AbstractBlsProtocolMessage {
    /** The random group elements being opened */
    @NonNull
    private final RandomGroupElements randomGroupElements;

    /**
     * Constructor
     *
     * @param randomGroupElements the random group elements being opened
     */
    public OpeningMessage(@NonNull final NodeId senderId, @NonNull final RandomGroupElements randomGroupElements) {
        super(senderId);

        this.randomGroupElements = Objects.requireNonNull(randomGroupElements, "randomGroupElements must not be null");
    }

    /**
     * Gets the random group elements
     *
     * @return the {@link #randomGroupElements} object
     */
    @NonNull
    public RandomGroupElements getRandomGroupElements() {
        return randomGroupElements;
    }
}
