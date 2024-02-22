/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.common.scratchpad.Scratchpad;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.dispatch.triggers.control.HaltRequestedConsumer;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.iss.IssScratchpad;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.system.state.notifications.IssNotification.IssType;
import com.swirlds.platform.test.fixtures.SimpleScratchpad;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IssHandler Tests")
class IssHandlerTests {
    @Test
    @DisplayName("Other ISS Always Freeze")
    void otherIssAlwaysFreeze() {
        final Configuration configuration =
                new TestConfigBuilder().withValue("state.haltOnAnyIss", true).getOrCreateConfig();
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler =
                new IssHandler(stateConfig, haltRequestedConsumer, fatalErrorConsumer, simpleScratchpad);

        handler.issObserved(new IssNotification(1234L, IssType.OTHER_ISS));

        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Once frozen, this should become a no-op
        handler.issObserved(new IssNotification(1234L, IssType.OTHER_ISS));

        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Another node ISSed, we will not record that on the scratchpad.
        assertNull(simpleScratchpad.get(IssScratchpad.LAST_ISS_ROUND));
    }

    @Test
    @DisplayName("Other ISS No Action")
    void otherIssNoAction() {
        final Configuration configuration =
                new TestConfigBuilder().withValue("state.haltOnAnyIss", false).getOrCreateConfig();
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler =
                new IssHandler(stateConfig, haltRequestedConsumer, fatalErrorConsumer, simpleScratchpad);

        handler.issObserved(new IssNotification(1234L, IssType.OTHER_ISS));

        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        assertNull(simpleScratchpad.get(IssScratchpad.LAST_ISS_ROUND));
    }

    @Test
    @DisplayName("Self ISS Automated Recovery")
    void selfIssAutomatedRecovery() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue("state.haltOnAnyIss", false)
                .withValue("state.automatedSelfIssRecovery", true)
                .getOrCreateConfig();
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler =
                new IssHandler(stateConfig, haltRequestedConsumer, fatalErrorConsumer, simpleScratchpad);

        handler.issObserved(new IssNotification(1234L, IssType.SELF_ISS));

        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(1, shutdownCount.get(), "unexpected shutdown count");

        final SerializableLong issRound = simpleScratchpad.get(IssScratchpad.LAST_ISS_ROUND);
        assertNotNull(issRound);
        assertEquals(issRound.getValue(), 1234L);
    }

    @Test
    @DisplayName("Self ISS No Action")
    void selfIssNoAction() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue("state.haltOnAnyIss", false)
                .withValue("state.automatedSelfIssRecovery", false)
                .getOrCreateConfig();
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler =
                new IssHandler(stateConfig, haltRequestedConsumer, fatalErrorConsumer, simpleScratchpad);

        handler.issObserved(new IssNotification(1234L, IssType.SELF_ISS));

        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        final SerializableLong issRound = simpleScratchpad.get(IssScratchpad.LAST_ISS_ROUND);
        assertNotNull(issRound);
        assertEquals(issRound.getValue(), 1234L);
    }

    @Test
    @DisplayName("Self ISS Always Freeze")
    void selfIssAlwaysFreeze() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue("state.haltOnAnyIss", true)
                .withValue("state.automatedSelfIssRecovery", false)
                .getOrCreateConfig();
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler =
                new IssHandler(stateConfig, haltRequestedConsumer, fatalErrorConsumer, simpleScratchpad);

        handler.issObserved(new IssNotification(1234L, IssType.SELF_ISS));

        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Once frozen, this should become a no-op
        handler.issObserved(new IssNotification(1234L, IssType.SELF_ISS));

        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        final SerializableLong issRound = simpleScratchpad.get(IssScratchpad.LAST_ISS_ROUND);
        assertNotNull(issRound);
        assertEquals(issRound.getValue(), 1234L);
    }

    @Test
    @DisplayName("Catastrophic ISS No Action")
    void catastrophicIssNoAction() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue("state.haltOnAnyIss", false)
                .withValue("state.haltOnCatastrophicIss", false)
                .getOrCreateConfig();
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler =
                new IssHandler(stateConfig, haltRequestedConsumer, fatalErrorConsumer, simpleScratchpad);

        handler.issObserved(new IssNotification(1234L, IssType.CATASTROPHIC_ISS));

        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        final SerializableLong issRound = simpleScratchpad.get(IssScratchpad.LAST_ISS_ROUND);
        assertNotNull(issRound);
        assertEquals(issRound.getValue(), 1234L);
    }

    @Test
    @DisplayName("Catastrophic ISS Always Freeze")
    void catastrophicIssAlwaysFreeze() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue("state.haltOnAnyIss", true)
                .withValue("state.haltOnCatastrophicIss", false)
                .getOrCreateConfig();
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler =
                new IssHandler(stateConfig, haltRequestedConsumer, fatalErrorConsumer, simpleScratchpad);

        handler.issObserved(new IssNotification(1234L, IssType.CATASTROPHIC_ISS));

        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Once frozen, this should become a no-op
        handler.issObserved(new IssNotification(1234L, IssType.CATASTROPHIC_ISS));

        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        final SerializableLong issRound = simpleScratchpad.get(IssScratchpad.LAST_ISS_ROUND);
        assertNotNull(issRound);
        assertEquals(issRound.getValue(), 1234L);
    }

    @Test
    @DisplayName("Catastrophic ISS Freeze On Catastrophic")
    void catastrophicIssFreezeOnCatastrophic() {
        final Configuration configuration = new TestConfigBuilder()
                .withValue("state.haltOnAnyIss", false)
                .withValue("state.haltOnCatastrophicIss", true)
                .getOrCreateConfig();
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler =
                new IssHandler(stateConfig, haltRequestedConsumer, fatalErrorConsumer, simpleScratchpad);

        handler.issObserved(new IssNotification(1234L, IssType.CATASTROPHIC_ISS));

        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Once frozen, this should become a no-op
        handler.issObserved(new IssNotification(1234L, IssType.CATASTROPHIC_ISS));

        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        final SerializableLong issRound = simpleScratchpad.get(IssScratchpad.LAST_ISS_ROUND);
        assertNotNull(issRound);
        assertEquals(issRound.getValue(), 1234L);
    }
}
