/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.events;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.platform.consensus.ConsensusConstants.ROUND_FIRST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.hashing.DefaultEventHasher;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BirthRoundMigrationShimTests {

    @NonNull
    private PlatformEvent buildEvent(
            @NonNull final Random random,
            @NonNull final SemanticVersion softwareVersion,
            final long generation,
            final long birthRound) {

        final NodeId creatorId = NodeId.of(random.nextLong(1, 10));
        final PlatformEvent selfParent = new TestingEventBuilder(random)
                .setCreatorId(creatorId)
                .setBirthRound(random.nextLong(birthRound - 2, birthRound + 1)) /* realistic range */
                .build();

        final PlatformEvent event = new TestingEventBuilder(random)
                .setSoftwareVersion(softwareVersion)
                .setCreatorId(creatorId)
                .setBirthRound(birthRound)
                .setSelfParent(selfParent)
                /* chose parent generation to yield desired self generation */
                .overrideSelfParentGeneration(generation - 1)
                .build();

        new DefaultEventHasher().hashEvent(event);

        return event;
    }

    @Test
    void ancientEventsTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final BasicSoftwareVersion firstVersionInBirthRoundMode = new BasicSoftwareVersion(random.nextInt(10, 100));
        final long lastRoundBeforeBirthRoundMode = random.nextLong(100, 1_000);
        final long lowestJudgeGenerationBeforeBirthRoundMode = random.nextLong(100, 1_000);

        final BirthRoundMigrationShim shim = new DefaultBirthRoundMigrationShim(
                platformContext,
                firstVersionInBirthRoundMode,
                lastRoundBeforeBirthRoundMode,
                lowestJudgeGenerationBeforeBirthRoundMode);

        // Any event with a software version less than firstVersionInBirthRoundMode and a generation less than
        // lowestJudgeGenerationBeforeBirthRoundMode should have its birth round set to ROUND_FIRST.

        for (int i = 0; i < 100; i++) {
            final long birthRound = random.nextLong(100, 1000);
            final PlatformEvent event = buildEvent(
                    random,
                    SemanticVersion.newBuilder()
                            .major(firstVersionInBirthRoundMode.getSoftwareVersion() - random.nextInt(1, 100))
                            .build(),
                    lowestJudgeGenerationBeforeBirthRoundMode - random.nextInt(1, 100),
                    birthRound);

            assertEquals(birthRound, event.getBirthRound());
            final Hash originalHash = event.getHash();

            assertSame(event, shim.migrateEvent(event));
            assertEquals(ROUND_FIRST, event.getBirthRound());

            // The hash of the event should not have changed
            event.invalidateHash();
            new DefaultEventHasher().hashEvent(event);
            assertEquals(originalHash, event.getHash());
        }
    }

    @Test
    void barelyNonAncientEventsTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final BasicSoftwareVersion firstVersionInBirthRoundMode = new BasicSoftwareVersion(random.nextInt(10, 100));
        final long lastRoundBeforeBirthRoundMode = random.nextLong(100, 1_000);
        final long lowestJudgeGenerationBeforeBirthRoundMode = random.nextLong(100, 1_000);

        final BirthRoundMigrationShim shim = new DefaultBirthRoundMigrationShim(
                platformContext,
                firstVersionInBirthRoundMode,
                lastRoundBeforeBirthRoundMode,
                lowestJudgeGenerationBeforeBirthRoundMode);

        // Any event with a software version less than firstVersionInBirthRoundMode and a generation greater than
        // or equal to lowestJudgeGenerationBeforeBirthRoundMode should have its birth round set to
        // lastRoundBeforeBirthRoundMode.

        for (int i = 0; i < 100; i++) {
            final long birthRound = random.nextLong(100, 1000);
            final PlatformEvent event = buildEvent(
                    random,
                    SemanticVersion.newBuilder()
                            .major(firstVersionInBirthRoundMode.getSoftwareVersion() - random.nextInt(1, 100))
                            .build(),
                    lowestJudgeGenerationBeforeBirthRoundMode + random.nextInt(0, 10),
                    birthRound);

            assertEquals(birthRound, event.getBirthRound());
            final Hash originalHash = event.getHash();

            assertSame(event, shim.migrateEvent(event));
            assertEquals(lastRoundBeforeBirthRoundMode, event.getBirthRound());

            // The hash of the event should not have changed
            event.invalidateHash();
            new DefaultEventHasher().hashEvent(event);
            assertEquals(originalHash, event.getHash());
        }
    }

    @Test
    void unmodifiedEventsTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final BasicSoftwareVersion firstVersionInBirthRoundMode = new BasicSoftwareVersion(random.nextInt(10, 100));
        final long lastRoundBeforeBirthRoundMode = random.nextLong(100, 1_000);
        final long lowestJudgeGenerationBeforeBirthRoundMode = random.nextLong(100, 1_000);

        final BirthRoundMigrationShim shim = new DefaultBirthRoundMigrationShim(
                platformContext,
                firstVersionInBirthRoundMode,
                lastRoundBeforeBirthRoundMode,
                lowestJudgeGenerationBeforeBirthRoundMode);

        // Any event with a software greater than or equal to firstVersionInBirthRoundMode should not have its birth
        // round modified.

        for (int i = 0; i < 100; i++) {
            final long birthRound = random.nextLong(100, 1000);
            final PlatformEvent event = buildEvent(
                    random,
                    SemanticVersion.newBuilder()
                            .major(firstVersionInBirthRoundMode.getSoftwareVersion() + random.nextInt(1, 10))
                            .build(),
                    lowestJudgeGenerationBeforeBirthRoundMode - random.nextInt(-100, 100),
                    birthRound);

            assertEquals(birthRound, event.getBirthRound());
            final Hash originalHash = event.getHash();

            assertSame(event, shim.migrateEvent(event));
            assertEquals(birthRound, event.getBirthRound());

            // The hash of the event should not have changed
            event.invalidateHash();
            new DefaultEventHasher().hashEvent(event);
            assertEquals(originalHash, event.getHash());
        }
    }
}
