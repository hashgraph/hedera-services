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

import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.scratchpad.Scratchpad;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.components.state.output.IssConsumer;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.dispatch.triggers.control.HaltRequestedConsumer;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.iss.IssScratchpad;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.test.fixtures.SimpleScratchpad;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IssHandler Tests")
class IssHandlerTests {

    @Test
    @DisplayName("Hash Disagreement From Self")
    void hashDisagreementFromSelf() {
        final Configuration configuration =
                new TestConfigBuilder().withValue("state.haltOnAnyIss", true).getOrCreateConfig();

        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final long selfId = 0;

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler = new IssHandler(
                stateConfig,
                new NodeId(selfId),
                mock(StatusActionSubmitter.class),
                haltRequestedConsumer,
                fatalErrorConsumer,
                (r, type, otherId) -> {},
                simpleScratchpad);

        handler.stateHashValidityObserver(1234L, new NodeId(selfId), randomHash(), randomHash());

        assertEquals(0, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");
        assertNull(simpleScratchpad.get(IssScratchpad.LAST_ISS_ROUND));
    }

    @Test
    @DisplayName("Hash Disagreement Always Freeze")
    void hashDisagreementAlwaysFreeze() {
        final Configuration configuration =
                new TestConfigBuilder().withValue("state.haltOnAnyIss", true).getOrCreateConfig();
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final long selfId = 0;

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler = new IssHandler(
                stateConfig,
                new NodeId(selfId),
                mock(StatusActionSubmitter.class),
                haltRequestedConsumer,
                fatalErrorConsumer,
                (r, type, otherId) -> {},
                simpleScratchpad);

        handler.stateHashValidityObserver(1234L, new NodeId(selfId + 1), randomHash(), randomHash());

        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Once frozen, this should become a no-op
        handler.stateHashValidityObserver(1234L, new NodeId(selfId + 1), randomHash(), randomHash());

        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Another node ISSed, we will not record that on the scratchpad.
        assertNull(simpleScratchpad.get(IssScratchpad.LAST_ISS_ROUND));
    }

    @Test
    @DisplayName("Hash Disagreement No Action")
    void hashDisagreementNoAction() {
        final Configuration configuration =
                new TestConfigBuilder().withValue("state.haltOnAnyIss", false).getOrCreateConfig();
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final long selfId = 0;

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler = new IssHandler(
                stateConfig,
                new NodeId(selfId),
                mock(StatusActionSubmitter.class),
                haltRequestedConsumer,
                fatalErrorConsumer,
                (r, type, otherId) -> {},
                simpleScratchpad);

        handler.stateHashValidityObserver(1234L, new NodeId(selfId + 1), randomHash(), randomHash());

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
        final long selfId = 0;

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler = new IssHandler(
                stateConfig,
                new NodeId(selfId),
                mock(StatusActionSubmitter.class),
                haltRequestedConsumer,
                fatalErrorConsumer,
                (r, type, otherId) -> {},
                simpleScratchpad);

        handler.selfIssObserver(1234L, randomHash(), randomHash());

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
        final long selfId = 0;

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler = new IssHandler(
                stateConfig,
                new NodeId(selfId),
                mock(StatusActionSubmitter.class),
                haltRequestedConsumer,
                fatalErrorConsumer,
                (r, type, otherId) -> {},
                simpleScratchpad);

        handler.selfIssObserver(1234L, randomHash(), randomHash());

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
        final long selfId = 0;

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler = new IssHandler(
                stateConfig,
                new NodeId(selfId),
                mock(StatusActionSubmitter.class),
                haltRequestedConsumer,
                fatalErrorConsumer,
                (r, type, otherId) -> {},
                simpleScratchpad);

        handler.selfIssObserver(1234L, randomHash(), randomHash());

        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Once frozen, this should become a no-op
        handler.selfIssObserver(1234L, randomHash(), randomHash());

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
        final long selfId = 0;

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler = new IssHandler(
                stateConfig,
                new NodeId(selfId),
                mock(StatusActionSubmitter.class),
                haltRequestedConsumer,
                fatalErrorConsumer,
                (r, type, otherId) -> {},
                simpleScratchpad);

        handler.catastrophicIssObserver(1234L, mock(Hash.class));

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
        final long selfId = 0;

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler = new IssHandler(
                stateConfig,
                new NodeId(selfId),
                mock(StatusActionSubmitter.class),
                haltRequestedConsumer,
                fatalErrorConsumer,
                (r, type, otherId) -> {},
                simpleScratchpad);

        handler.catastrophicIssObserver(1234L, mock(Hash.class));

        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Once frozen, this should become a no-op
        handler.catastrophicIssObserver(1234L, mock(Hash.class));

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
        final long selfId = 0;

        final AtomicInteger freezeCount = new AtomicInteger();
        final AtomicInteger shutdownCount = new AtomicInteger();

        final HaltRequestedConsumer haltRequestedConsumer = (final String reason) -> freezeCount.getAndIncrement();

        final FatalErrorConsumer fatalErrorConsumer = (msg, t, code) -> shutdownCount.getAndIncrement();

        final Scratchpad<IssScratchpad> simpleScratchpad = new SimpleScratchpad<>();
        final IssHandler handler = new IssHandler(
                stateConfig,
                new NodeId(selfId),
                mock(StatusActionSubmitter.class),
                haltRequestedConsumer,
                fatalErrorConsumer,
                (r, type, otherId) -> {},
                simpleScratchpad);

        handler.catastrophicIssObserver(1234L, mock(Hash.class));

        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        // Once frozen, this should become a no-op
        handler.catastrophicIssObserver(1234L, mock(Hash.class));

        assertEquals(1, freezeCount.get(), "unexpected freeze count");
        assertEquals(0, shutdownCount.get(), "unexpected shutdown count");

        final SerializableLong issRound = simpleScratchpad.get(IssScratchpad.LAST_ISS_ROUND);
        assertNotNull(issRound);
        assertEquals(issRound.getValue(), 1234L);
    }

    @Test
    @DisplayName("Notifications Test")
    void issConsumerTest() {
        final AtomicInteger selfIssCount = new AtomicInteger();
        final AtomicInteger otherIssCount = new AtomicInteger();
        final AtomicInteger catastrophicIssCount = new AtomicInteger();
        final IssConsumer issConsumer = (round, type, otherId) -> {
            switch (type) {
                case OTHER_ISS -> otherIssCount.getAndIncrement();
                case SELF_ISS -> selfIssCount.getAndIncrement();
                case CATASTROPHIC_ISS -> catastrophicIssCount.getAndIncrement();
            }
        };

        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final IssHandler issHandler = new IssHandler(
                stateConfig,
                new NodeId(0L),
                mock(StatusActionSubmitter.class),
                (reason) -> {},
                (msg, t, code) -> {},
                issConsumer,
                new SimpleScratchpad<>());

        assertEquals(0, selfIssCount.get(), "incorrect self ISS count");
        assertEquals(0, otherIssCount.get(), "incorrect other ISS count");
        assertEquals(0, catastrophicIssCount.get(), "incorrect catastrophic ISS count");

        issHandler.selfIssObserver(1234L, randomHash(), randomHash());
        assertEquals(1, selfIssCount.get(), "incorrect self ISS count");
        assertEquals(0, otherIssCount.get(), "incorrect other ISS count");
        assertEquals(0, catastrophicIssCount.get(), "incorrect catastrophic ISS count");

        // This method should not trigger notification when called with the "self" node ID
        issHandler.stateHashValidityObserver(4321L, new NodeId(0L), randomHash(), randomHash());
        assertEquals(1, selfIssCount.get(), "incorrect self ISS count");
        assertEquals(0, otherIssCount.get(), "incorrect other ISS count");
        assertEquals(0, catastrophicIssCount.get(), "incorrect catastrophic ISS count");

        issHandler.stateHashValidityObserver(4321L, new NodeId(7L), randomHash(), randomHash());
        assertEquals(1, selfIssCount.get(), "incorrect self ISS count");
        assertEquals(1, otherIssCount.get(), "incorrect other ISS count");
        assertEquals(0, catastrophicIssCount.get(), "incorrect catastrophic ISS count");

        issHandler.catastrophicIssObserver(1111L, randomHash());
        assertEquals(1, selfIssCount.get(), "incorrect self ISS count");
        assertEquals(1, otherIssCount.get(), "incorrect other ISS count");
        assertEquals(1, catastrophicIssCount.get(), "incorrect catastrophic ISS count");
    }
}
