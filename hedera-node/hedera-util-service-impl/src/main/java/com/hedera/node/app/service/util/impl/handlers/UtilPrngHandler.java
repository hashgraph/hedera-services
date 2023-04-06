/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.util.impl.handlers;

import static java.util.Objects.requireNonNull;

import com.google.common.math.LongMath;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.util.UtilPrngTransactionBody;
import com.hedera.node.app.service.util.impl.config.PrngConfig;
import com.hedera.node.app.service.util.impl.records.PrngRecordBuilder;
import com.hedera.node.app.service.util.impl.records.UtilPrngRecordBuilder;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#UTIL_PRNG}.
 */
@Singleton
public class UtilPrngHandler implements TransactionHandler {
    private static final Logger log = LogManager.getLogger(UtilPrngHandler.class);
    private static final byte[] MISSING_BYTES = new byte[0];

    @Inject
    public UtilPrngHandler() {
        // Dagger2
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, warms the cache, and creates the PreHandleResult that is used in
     * the handle stage.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *     passed to {@code handle()}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        checkRange(context.getTxn().utilPrngOrThrow().range());
        // Payer key is fetched in PreHandleWorkflow
    }

    private void checkRange(int range) throws PreCheckException {
        if (range < 0) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_PRNG_RANGE);
        }
    }

    public void handle(
            @NonNull final UtilPrngTransactionBody op,
            @NonNull final PrngConfig prngConfig,
            @NonNull final PrngRecordBuilder recordBuilder,
            @NonNull final Hash nMinusThreeRunningHash) {
        final var range = op.range();

        if (!prngConfig.isPrngEnabled()) {
            return;
        }

        final byte[] pseudoRandomBytes = getNMinus3RunningHashBytes(nMinusThreeRunningHash);
        if (pseudoRandomBytes == null || pseudoRandomBytes.length == 0) {
            return;
        }

        if (range > 0) {
            // generate pseudorandom number in the given range
            final int pseudoRandomNumber = randomNumFromBytes(pseudoRandomBytes, range);
            recordBuilder.setGeneratedRandomNumber(pseudoRandomNumber);
        } else {
            recordBuilder.setGeneratedRandomBytes(Bytes.wrap(pseudoRandomBytes));
        }
    }

    private byte[] getNMinus3RunningHashBytes(final Hash nMinus3RunningHash) {
        // Use n-3 running hash instead of n-1 running hash for processing transactions quickly
        if (nMinus3RunningHash == null || Arrays.equals(nMinus3RunningHash.getValue(), new byte[48])) {
            log.info("No n-3 record running hash available to generate random number");
            return MISSING_BYTES;
        }
        // generate binary string from the running hash of records
        return nMinus3RunningHash.getValue();
    }

    public final int randomNumFromBytes(final byte[] pseudoRandomBytes, final int range) {
        final var initialBitsValue = ByteBuffer.wrap(pseudoRandomBytes, 0, 4).getInt();
        return LongMath.mod(initialBitsValue, range);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PrngRecordBuilder newRecordBuilder() {
        return new UtilPrngRecordBuilder();
    }
}
