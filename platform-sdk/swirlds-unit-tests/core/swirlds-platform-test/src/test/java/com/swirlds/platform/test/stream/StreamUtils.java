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

package com.swirlds.platform.test.stream;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.stream.EventStreamType;
import com.swirlds.common.stream.RunningHashCalculatorForStream;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.stream.internal.LinkedObjectStream;
import com.swirlds.common.stream.internal.TimestampStreamFileWriter;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;

/**
 * Test utilities for the event stream
 */
public final class StreamUtils {
    /**
     * Writes consensus rounds to an event stream
     *
     * @param dir
     * 		the directory to write to
     * @param signer
     * 		signs the files
     * @param eventStreamWindowSize
     * 		the windows after which a new stream file will be created
     * @param rounds
     * 		the consensus rounds to write
     */
    public static void writeRoundsToStream(
            final Path dir,
            final Signer signer,
            final Duration eventStreamWindowSize,
            final Collection<ConsensusRound> rounds) {
        final LinkedObjectStream<EventImpl> stream =
                new RunningHashCalculatorForStream<>(new TimestampStreamFileWriter<>(
                        dir.toAbsolutePath().toString(),
                        eventStreamWindowSize.toMillis(),
                        signer,
                        false,
                        EventStreamType.getInstance()));
        stream.setRunningHash(new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]));
        rounds.stream().flatMap(r -> r.getConsensusEvents().stream()).forEach(stream::addObject);
        stream.close();
    }
}
