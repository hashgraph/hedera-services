/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.swirlds.state.merkle.disk;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableSingletonStateBase;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.swirlds.state.merkle.logging.StateLogger.logSingletonRead;
import static java.util.Objects.requireNonNull;

public class OnDiskReadableSingletonState<T> extends ReadableSingletonStateBase<T> {

    @NonNull
    private final VirtualMap virtualMap;

    @NonNull
    private final Codec<T> valueCodec;

    public OnDiskReadableSingletonState(
            @NonNull final String serviceName,
            @NonNull final String stateKey,
            @NonNull final Codec<T> valueCodec,
            @NonNull final VirtualMap virtualMap) {
        super(serviceName, stateKey);

        this.valueCodec = requireNonNull(valueCodec);
        this.virtualMap = requireNonNull(virtualMap);
    }

    // TODO: refactor? is is duplicated in OnDiskWritableSingletonState
    /** {@inheritDoc} */
    @Override
    protected T readFromDataSource() {
        final var value = virtualMap.get(getVirtualMapKey(), valueCodec);
        // Log to transaction state log, what was read
        logSingletonRead(getLabel(), value);
        return value;
    }

    // TODO: refactor? is is duplicated in OnDiskWritableSingletonState
    // TODO: test this method
    /**
     * Generates a 2-byte big-endian key identifying this singleton state in the Virtual Map.
     * <p>
     * The underlying state ID (unsigned 16-bit) must be in [0..65535], and is written in big-endian order.
     * </p>
     *
     * @return a {@link Bytes} object containing exactly 2 bytes in big-endian order
     * @throws IllegalArgumentException if the state ID is outside [0..65535]
     */
    protected Bytes getVirtualMapKey() {
        final int stateId = getStateId();

        if (stateId < 0 || stateId > 65535) {
            throw new IllegalArgumentException("State ID " + stateId + " must fit in [0..65535]");
        }

        final ByteBuffer buffer = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) stateId);

        return Bytes.wrap(buffer.array());
    }
}
