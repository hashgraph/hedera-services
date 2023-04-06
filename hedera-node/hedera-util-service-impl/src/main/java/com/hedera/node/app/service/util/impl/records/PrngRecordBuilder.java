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

package com.hedera.node.app.service.util.impl.records;

import com.hedera.node.app.spi.records.RecordBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A {@code RecordBuilder} specialization for tracking the side-effects of a
 * {@code ConsensusCreateTopic} transaction.
 */
public interface PrngRecordBuilder extends RecordBuilder<PrngRecordBuilder> {
    /**
     * Tracks the random number generated within the range provided in
     * {@link com.hedera.hapi.node.util.UtilPrngTransactionBody} if range is greater than 0.
     *
     * @param num the random number generated within range
     * @return this builder
     */
    @NonNull
    PrngRecordBuilder setGeneratedRandomNumber(int num);

    /**
     * Tracks the pseudorandom 384-bit string generated when no output range is provided
     * or range of 0 is provided in {@link com.hedera.hapi.node.util.UtilPrngTransactionBody}
     * @param prngBytes the pseudorandom 384-bit string
     * @return this builder
     */
    @NonNull
    PrngRecordBuilder setGeneratedRandomBytes(Bytes prngBytes);

    /**
     * Returns the number of the created topic.
     *
     * @return the number of the created topic
     */
    OptionalInt getGeneratedNumber();

    /**
     * Returns the generated pseudorandom bytes, when no range is provided
     * in {@link com.hedera.hapi.node.util.UtilPrngTransactionBody}.
     * @return the generated pseudorandom bytes
     */
    Optional<Bytes> getGeneratedBytes();

    default boolean hasPrngNumber() {
        return getGeneratedNumber().isPresent();
    }
}
