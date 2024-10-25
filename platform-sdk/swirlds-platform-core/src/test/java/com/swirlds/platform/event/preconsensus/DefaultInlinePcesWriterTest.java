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

package com.swirlds.platform.event.preconsensus;

import static com.swirlds.common.units.DataUnit.UNIT_BYTES;
import static com.swirlds.common.units.DataUnit.UNIT_KILOBYTES;
import static com.swirlds.platform.event.AncientMode.GENERATION_THRESHOLD;
import static com.swirlds.platform.system.transaction.TransactionWrapperUtils.createAppPayloadWrapper;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.TransactionGenerator;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultInlinePcesWriterTest {

    @TempDir
    private Path tempDir;

    private final AncientMode ancientMode = GENERATION_THRESHOLD;
    private PlatformContext platformContext;

    @BeforeEach
    void beforeEach() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue(PcesConfig_.DATABASE_DIRECTORY, tempDir.toString())
                .getOrCreateConfig();
        platformContext = buildContext(configuration);
    }

    @NonNull
    private PlatformContext buildContext(@NonNull final Configuration configuration) {
        return TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .withTime(new FakeTime(Duration.ofMillis(1)))
                .build();
    }

    /**
     * Build a transaction generator.
     */
    private static TransactionGenerator buildTransactionGenerator() {

        final int transactionCount = 10;
        final int averageTransactionSizeInKb = 10;
        final int transactionSizeStandardDeviationInKb = 5;

        return (final Random random) -> {
            final TransactionWrapper[] transactions = new TransactionWrapper[transactionCount];
            for (int index = 0; index < transactionCount; index++) {

                final int transactionSize = (int) UNIT_KILOBYTES.convertTo(
                        Math.max(
                                1,
                                averageTransactionSizeInKb
                                        + random.nextDouble() * transactionSizeStandardDeviationInKb),
                        UNIT_BYTES);
                final byte[] bytes = new byte[transactionSize];
                random.nextBytes(bytes);

                transactions[index] = createAppPayloadWrapper(bytes);
            }
            return transactions;
        };
    }

    /**
     * Build an event generator.
     */
    static StandardGraphGenerator buildGraphGenerator(
            @NonNull final PlatformContext platformContext, @NonNull final Random random) {
        Objects.requireNonNull(platformContext);
        final TransactionGenerator transactionGenerator = buildTransactionGenerator();

        return new StandardGraphGenerator(
                platformContext,
                random.nextLong(),
                new StandardEventSource().setTransactionGenerator(transactionGenerator),
                new StandardEventSource().setTransactionGenerator(transactionGenerator),
                new StandardEventSource().setTransactionGenerator(transactionGenerator),
                new StandardEventSource().setTransactionGenerator(transactionGenerator));
    }

    @Test
    void test() throws Exception {
        final Random random = RandomUtils.getRandomPrintSeed();
        final NodeId selfId = NodeId.of(0);

        final StandardGraphGenerator generator = buildGraphGenerator(platformContext, random);
        final PlatformEvent event = generator.generateEvent().getBaseEvent();

        final PcesFileTracker pcesFiles = new PcesFileTracker(ancientMode);

        final PcesFileManager fileManager = new PcesFileManager(platformContext, pcesFiles, selfId, 0);
        final DefaultInlinePcesWriter writer = new DefaultInlinePcesWriter(platformContext, fileManager);

        writer.beginStreamingNewEvents();
        final PlatformEvent actual = writer.writeEvent(event);

        final String[] fileList = tempDir.toFile().list();
        assertThat(actual).isEqualTo(event);
        assertThat(fileList).isNotNull();
        assertThat(fileList.length).isEqualTo(1);
    }
}
