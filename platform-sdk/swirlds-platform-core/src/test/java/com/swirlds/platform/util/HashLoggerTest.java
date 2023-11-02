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

package com.swirlds.platform.util;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.legacy.LogMarker.STATE_HASH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.merkle.util.MerkleTestUtils;
import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.MessageSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class HashLoggerTest {
    private Logger mockLogger;
    private HashLogger hashLogger;
    private List<String> logged;

    /**
     * Get a regex that will match a log message containing the given round number
     *
     * @param round the round number
     * @return the regex
     */
    private String getRoundEqualsRegex(final long round) {
        return String.format("State Info, round = %s[\\S\\s]*", round);
    }

    @BeforeEach
    public void setUp() {
        mockLogger = mock(Logger.class);
        final StateConfig stateConfig =
                new TestConfigBuilder().getOrCreateConfig().getConfigData(StateConfig.class);
        hashLogger = new HashLogger(getStaticThreadManager(), stateConfig, mockLogger);
        logged = new ArrayList<>();

        doAnswer(invocation -> {
                    final MessageSupplier supplier = invocation.getArgument(1, MessageSupplier.class);
                    final String message = supplier.get().getFormattedMessage();
                    logged.add(message);
                    return message;
                })
                .when(mockLogger)
                .info(eq(STATE_HASH.getMarker()), any(MessageSupplier.class));
    }

    @Test
    public void loggingInOrder() {
        hashLogger.logHashes(createSignedState(1));
        hashLogger.logHashes(createSignedState(2));
        hashLogger.logHashes(createSignedState(3));
        flush();
        assertThat(logged).hasSize(3);
        assertThat(logged.get(0)).matches(getRoundEqualsRegex(1));
        assertThat(logged.get(1)).matches(getRoundEqualsRegex(2));
        assertThat(logged.get(2)).matches(getRoundEqualsRegex(3));
    }

    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    public void loggingEarlierEventsDropped() {
        hashLogger.logHashes(createSignedState(1));
        hashLogger.logHashes(createSignedState(2));
        hashLogger.logHashes(createSignedState(3));
        hashLogger.logHashes(createSignedState(1));
        hashLogger.logHashes(createSignedState(1));
        hashLogger.logHashes(createSignedState(2));
        flush();
        assertThat(logged).hasSize(3);
        assertThat(logged.get(0)).matches(getRoundEqualsRegex(1));
        assertThat(logged.get(1)).matches(getRoundEqualsRegex(2));
        assertThat(logged.get(2)).matches(getRoundEqualsRegex(3));
    }

    @Test
    public void loggingWithGapsAddsExtraWarning() {
        hashLogger.logHashes(createSignedState(1));
        hashLogger.logHashes(createSignedState(2));
        hashLogger.logHashes(createSignedState(5));
        hashLogger.logHashes(createSignedState(4));
        flush();
        assertThat(logged).hasSize(4);
        assertThat(logged.get(0)).matches(getRoundEqualsRegex(1));
        assertThat(logged.get(1)).matches(getRoundEqualsRegex(2));
        assertThat(logged.get(2)).contains("Several rounds skipped. Round received 5. Previously received 2.");
        assertThat(logged.get(3)).matches(getRoundEqualsRegex(5));
    }

    @Test
    public void noLoggingWhenDisabled() {
        final StateConfig stateConfig = new TestConfigBuilder()
                .withValue("state.enableHashStreamLogging", "false")
                .getOrCreateConfig()
                .getConfigData(StateConfig.class);

        hashLogger = new HashLogger(getStaticThreadManager(), stateConfig, mockLogger);
        hashLogger.logHashes(createSignedState(1));
        assertThat(logged).isEmpty();
        assertThat(hashLogger.queue()).isNullOrEmpty();
    }

    @Test
    public void loggerWithDefaultConstructorWorks() {
        final StateConfig stateConfig =
                new TestConfigBuilder().getOrCreateConfig().getConfigData(StateConfig.class);

        assertDoesNotThrow(() -> {
            hashLogger = new HashLogger(getStaticThreadManager(), stateConfig);
            hashLogger.logHashes(createSignedState(1));
            flush();
        });
    }

    private SignedState createSignedState(final long round) {
        final MerkleNode merkleNode = MerkleTestUtils.buildLessSimpleTree();
        MerkleCryptoFactory.getInstance().digestTreeSync(merkleNode);
        final SignedState signedState = mock(SignedState.class);
        final State state = mock(State.class);

        final AddressBook addressBook = new AddressBook();
        addressBook.setHash(merkleNode.getHash());

        final PlatformData platformData = new PlatformData();
        platformData.setRound(round);
        platformData.setHash(merkleNode.getHash());

        final PlatformState platformState = new PlatformState();
        platformState.setChild(0, platformData);
        platformState.setChild(1, addressBook);

        when(state.getPlatformState()).thenReturn(platformState);
        when(state.getRoute()).thenReturn(merkleNode.getRoute());
        when(state.getHash()).thenReturn(merkleNode.getHash());

        when(signedState.getState()).thenReturn(state);
        when(signedState.getRound()).thenReturn(round);

        return signedState;
    }

    private void flush() {
        final CyclicBarrier barrier = new CyclicBarrier(2);
        hashLogger.queue().offer(() -> {
            try {
                barrier.await();
            } catch (final Exception e) {
            }
        });
        try {
            barrier.await();
        } catch (final Exception e) {
        }
    }
}
