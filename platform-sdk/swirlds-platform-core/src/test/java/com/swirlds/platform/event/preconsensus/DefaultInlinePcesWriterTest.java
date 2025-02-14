// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.event.preconsensus;

import static com.swirlds.platform.event.AncientMode.GENERATION_THRESHOLD;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.test.fixtures.event.PcesWriterTestUtils;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultInlinePcesWriterTest {

    @TempDir
    private Path tempDir;

    private final AncientMode ancientMode = GENERATION_THRESHOLD;
    private final int numEvents = 1_000;
    private final NodeId selfId = NodeId.of(0);

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

    @Test
    void standardOperationTest() throws Exception {
        final Random random = RandomUtils.getRandomPrintSeed();

        final StandardGraphGenerator generator = PcesWriterTestUtils.buildGraphGenerator(platformContext, random);

        final List<PlatformEvent> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEventWithoutIndex().getBaseEvent());
        }

        final PcesFileTracker pcesFiles = new PcesFileTracker(ancientMode);

        final PcesFileManager fileManager = new PcesFileManager(platformContext, pcesFiles, selfId, 0);
        final DefaultInlinePcesWriter writer = new DefaultInlinePcesWriter(platformContext, fileManager, selfId);

        writer.beginStreamingNewEvents();
        for (PlatformEvent event : events) {
            writer.writeEvent(event);
        }

        PcesWriterTestUtils.verifyStream(selfId, events, platformContext, 0, ancientMode);
    }

    @Test
    void ancientEventTest() throws Exception {

        final Random random = RandomUtils.getRandomPrintSeed();

        final StandardGraphGenerator generator = PcesWriterTestUtils.buildGraphGenerator(platformContext, random);

        final int stepsUntilAncient = random.nextInt(50, 100);
        final PcesSequencer sequencer = new DefaultPcesSequencer();
        final PcesFileTracker pcesFiles = new PcesFileTracker(ancientMode);

        final PcesFileManager fileManager = new PcesFileManager(platformContext, pcesFiles, selfId, 0);
        final DefaultInlinePcesWriter writer = new DefaultInlinePcesWriter(platformContext, fileManager, selfId);
        final AtomicLong latestDurableSequenceNumber = new AtomicLong();

        // We will add this event at the very end, it should be ancient by then
        final PlatformEvent ancientEvent = generator.generateEventWithoutIndex().getBaseEvent();

        final List<PlatformEvent> events = new LinkedList<>();
        for (int i = 0; i < numEvents; i++) {
            events.add(generator.generateEventWithoutIndex().getBaseEvent());
        }

        writer.beginStreamingNewEvents();

        final Collection<PlatformEvent> rejectedEvents = new HashSet<>();

        long lowerBound = ancientMode.selectIndicator(0, 1);
        final Iterator<PlatformEvent> iterator = events.iterator();
        while (iterator.hasNext()) {
            final PlatformEvent event = iterator.next();

            sequencer.assignStreamSequenceNumber(event);
            writer.writeEvent(event);
            lowerBound = Math.max(lowerBound, event.getAncientIndicator(ancientMode) - stepsUntilAncient);

            writer.updateNonAncientEventBoundary(new EventWindow(1, lowerBound, lowerBound, ancientMode));

            if (event.getAncientIndicator(ancientMode) < lowerBound) {
                // Although it's not common, it's actually possible that the generator will generate
                // an event that is ancient (since it isn't aware of what we consider to be ancient)
                rejectedEvents.add(event);
                iterator.remove();
            }
        }

        // Add the ancient event
        sequencer.assignStreamSequenceNumber(ancientEvent);
        if (lowerBound > ancientEvent.getAncientIndicator(ancientMode)) {
            // This is probably not possible... but just in case make sure this event is ancient
            try {
                writer.updateNonAncientEventBoundary(new EventWindow(
                        1,
                        ancientEvent.getAncientIndicator(ancientMode) + 1,
                        ancientEvent.getAncientIndicator(ancientMode) + 1,
                        ancientMode));
            } catch (final IllegalArgumentException e) {
                // ignore, more likely than not this event is way older than the actual ancient threshold
            }
        }

        rejectedEvents.add(ancientEvent);

        rejectedEvents.forEach(
                event -> assertFalse(latestDurableSequenceNumber.get() >= event.getStreamSequenceNumber()));

        PcesWriterTestUtils.verifyStream(selfId, events, platformContext, 0, ancientMode);
    }
}
