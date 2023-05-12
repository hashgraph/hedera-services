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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.stream.EventStreamType;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.crypto.KeyGeneratingException;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.crypto.PublicStores;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.test.consensus.GenerateConsensus;
import com.swirlds.signingtool.FileSignTool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;
import java.util.Random;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamSignTest {
    private static final byte[] EMPTY_ARRAY = new byte[] {};
    private static final String ORIG_SIG_FILE_APPENDIX = "_orig";

    @TempDir
    Path tmpDir;

    /**
     * Generates consensus events then writes them to an event stream with signatures. The original signatures are then
     * renamed and the stream files are signed again with the {@link FileSignTool}. The original signatures are compared
     * to the ones created by the tool.
     */
    @Test
    void testSignAllFiles()
            throws ConstructableRegistryException, KeyStoreException, KeyGeneratingException, NoSuchAlgorithmException,
                    NoSuchProviderException, InvalidKeyException, IOException {
        final Random random = RandomUtils.getRandomPrintSeed();
        final int numNodes = 10;
        final int numEvents = 100_000;
        final Duration eventStreamWindowSize = Duration.ofSeconds(1);

        // setup
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");

        // generate consensus events
        final Deque<ConsensusRound> rounds =
                GenerateConsensus.generateConsensusRounds(numNodes, numEvents, random.nextLong());
        if (rounds.isEmpty()) {
            Assertions.fail("events are excepted to reach consensus");
        }

        // generate keys
        final KeysAndCerts keysAndCerts =
                KeysAndCerts.generate("a-name", EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY, new PublicStores());
        final PlatformSigner signer = new PlatformSigner(keysAndCerts);

        // write event stream
        StreamUtils.writeRoundsToStream(tmpDir, signer, eventStreamWindowSize, rounds);

        // rename original sig files
        Arrays.stream(Objects.requireNonNull(tmpDir.toFile().listFiles()))
                .filter(f -> f.getName().endsWith(EventStreamType.EVENT_SIG_EXTENSION))
                .map(f -> f.renameTo(
                        tmpDir.resolve(f.getName() + ORIG_SIG_FILE_APPENDIX).toFile()))
                .reduce((b1, b2) -> b1 && b2)
                .orElseThrow();

        // sign the stream files with the tool
        FileSignTool.signAllFiles(
                tmpDir.toFile().getAbsolutePath(),
                tmpDir.toFile().getAbsolutePath(),
                EventStreamType.getInstance(),
                keysAndCerts.sigKeyPair());

        // now compare the new sigs to the original ones
        Arrays.stream(Objects.requireNonNull(tmpDir.toFile().listFiles()))
                .filter(f -> f.getName().endsWith(ORIG_SIG_FILE_APPENDIX))
                .forEach(f -> {
                    try {
                        assertEquals(
                                -1,
                                Files.mismatch(
                                        f.toPath(),
                                        tmpDir.resolve(f.getName()
                                                .substring(0, f.getName().length() - 5))),
                                "generated signature file should match expected signature file");
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
