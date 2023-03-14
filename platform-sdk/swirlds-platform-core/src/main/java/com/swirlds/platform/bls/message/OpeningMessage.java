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

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.bls.protocol.CrsProtocol;
import com.swirlds.platform.bls.protocol.RandomGroupElements;

/**
 * The type of message sent in the second round of {@link CrsProtocol}, which opens elements
 * previously committed to
 */
public class OpeningMessage extends AbstractBlsProtocolMessage {
    /** The random group elements being opened */
    private final RandomGroupElements randomGroupElements;

    /**
     * Constructor
     *
     * @param randomGroupElements the random group elements being opened
     */
    public OpeningMessage(final NodeId senderId, final RandomGroupElements randomGroupElements) {
        super(senderId);

        this.randomGroupElements = throwArgNull(randomGroupElements, "randomGroupElements");
    }

    /**
     * Gets the random group elements
     *
     * @return the {@link #randomGroupElements} object
     */
    public RandomGroupElements getRandomGroupElements() {
        return randomGroupElements;
    }
}
