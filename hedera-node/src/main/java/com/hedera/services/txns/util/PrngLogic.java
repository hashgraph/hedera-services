/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.util;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PRNG_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.common.math.LongMath;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.Hash;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class PrngLogic {
    private static final Logger log = LogManager.getLogger(PrngLogic.class);

    public static final byte[] MISSING_BYTES = new byte[0];
    private final Supplier<RecordsRunningHashLeaf> runningHashLeafSupplier;
    private final SideEffectsTracker sideEffectsTracker;
    private final GlobalDynamicProperties properties;

    @Inject
    public PrngLogic(
            final GlobalDynamicProperties properties,
            final Supplier<RecordsRunningHashLeaf> runningHashLeafSupplier,
            final SideEffectsTracker sideEffectsTracker) {
        this.properties = properties;
        this.runningHashLeafSupplier = runningHashLeafSupplier;
        this.sideEffectsTracker = sideEffectsTracker;
    }

    public void generatePseudoRandom(final int range) {
        if (!properties.isUtilPrngEnabled()) {
            return;
        }

        final byte[] pseudoRandomBytes = getNMinus3RunningHashBytes();
        if (pseudoRandomBytes == null || pseudoRandomBytes.length == 0) {
            return;
        }
        if (range > 0) {
            // generate pseudorandom number in the given range
            final int pseudoRandomNumber = randomNumFromBytes(pseudoRandomBytes, range);
            sideEffectsTracker.trackRandomNumber(pseudoRandomNumber);
        } else {
            sideEffectsTracker.trackRandomBytes(pseudoRandomBytes);
        }
    }

    public ResponseCodeEnum validateSemantics(final TransactionBody prngTxn) {
        final var range = prngTxn.getUtilPrng().getRange();
        if (range < 0) {
            return INVALID_PRNG_RANGE;
        }
        return OK;
    }

    public final int randomNumFromBytes(final byte[] pseudoRandomBytes, final int range) {
        final var initialBitsValue = ByteBuffer.wrap(pseudoRandomBytes, 0, 4).getInt();
        return LongMath.mod(initialBitsValue, range);
    }

    public final byte[] getNMinus3RunningHashBytes() {
        Hash nMinus3RunningHash;
        try {
            // Use n-3 running hash instead of n-1 running hash for processing transactions quickly
            nMinus3RunningHash = runningHashLeafSupplier.get().nMinusThreeRunningHash();
            if (nMinus3RunningHash == null
                    || Arrays.equals(nMinus3RunningHash.getValue(), new byte[48])) {
                log.info("No n-3 record running hash available to generate random number");
                return MISSING_BYTES;
            }
            // generate binary string from the running hash of records
            return nMinus3RunningHash.getValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted when computing n-3 running hash");
        }
    }
}
