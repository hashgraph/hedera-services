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

package com.hedera.node.app.tss.handlers;

import static com.hedera.node.app.tss.handlers.TssMessageHandlerTest.getTssBody;
import static com.hedera.node.app.tss.handlers.TssUtils.SIGNATURE_SCHEMA;
import static com.hedera.node.app.tss.handlers.TssUtils.voteForValidMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.cryptography.tss.api.TssMessage;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.app.tss.api.FakeGroupElement;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TssUtilsTest {
    private static final BlsPublicKey FAKE_ENCRYPTION_KEY =
            new BlsPublicKey(new FakeGroupElement(BigInteger.valueOf(123L)), SIGNATURE_SCHEMA);

    @Test
    public void testComputeParticipantDirectory() {
        RosterEntry rosterEntry1 = new RosterEntry(1L, 100L, null, null);
        RosterEntry rosterEntry2 = new RosterEntry(2L, 50L, null, null);
        int maxSharesPerNode = 10;

        TssParticipantDirectory directory = TssUtils.computeParticipantDirectory(
                new Roster(List.of(rosterEntry1, rosterEntry2)), maxSharesPerNode, nodeId -> FAKE_ENCRYPTION_KEY);

        assertNotNull(directory);
        assertEquals((15 + 2) / 2, directory.getThreshold());
        assertEquals(15, directory.getTotalShares());
        assertEquals(15, directory.getShareIds().size());
    }

    @Test
    public void testVoteForValidMessages() {
        final var body = getTssBody();
        final var tssLibrary = mock(TssLibrary.class);
        final var tssParticipantDirectory = mock(TssParticipantDirectory.class);
        given(tssLibrary.verifyTssMessage(any(), any())).willReturn(true);

        final var validMessages = voteForValidMessages(
                        List.of(body.tssMessageOrThrow()), tssParticipantDirectory, tssLibrary)
                .get()
                .validTssMessages();

        assertEquals(1, validMessages.size());
    }

    @Test
    public void testVoteForValidMessagesFails() {
        final var body = getTssBody();
        final var tssLibrary = mock(TssLibrary.class);
        final var tssParticipantDirectory = mock(TssParticipantDirectory.class);
        given(tssLibrary.verifyTssMessage(any(), any())).willReturn(false);
        given(tssParticipantDirectory.getShareIds()).willReturn(List.of(1, 2, 3, 4));

        final var validMessagesForVote =
                voteForValidMessages(List.of(body.tssMessageOrThrow()), tssParticipantDirectory, tssLibrary);

        assertTrue(validMessagesForVote.isEmpty());
    }

    @Test
    public void testGetTssMessages() {
        final var library = mock(TssLibrary.class);
        final var tssMessage = mock(TssMessage.class);
        RosterEntry rosterEntry1 = new RosterEntry(1L, 100L, null, null);
        RosterEntry rosterEntry2 = new RosterEntry(2L, 50L, null, null);
        int maxSharesPerNode = 10;

        given(library.getTssMessageFromBytes(any(), any())).willReturn(tssMessage);
        given(tssMessage.toBytes())
                .willReturn(Bytes.wrap("tssMessage".getBytes()).toByteArray());
        TssParticipantDirectory directory = TssUtils.computeParticipantDirectory(
                new Roster(List.of(rosterEntry1, rosterEntry2)), maxSharesPerNode, nodeId -> FAKE_ENCRYPTION_KEY);

        final var body = getTssBody();
        final var validTssOps = List.of(body.tssMessageOrThrow());
        final var tssMessages = TssUtils.getTssMessages(validTssOps, directory, library);

        assertEquals(1, tssMessages.size());
        assertThat(body.tssMessageOrThrow().tssMessage().toByteArray())
                .isEqualTo(tssMessages.get(0).toBytes());
    }

    @Test
    public void testComputeNodeShares() {
        RosterEntry entry1 = new RosterEntry(1L, 100L, null, null);
        RosterEntry entry2 = new RosterEntry(2L, 50L, null, null);

        List<RosterEntry> entries = List.of(entry1, entry2);
        long maxTssMessagesPerNode = 10L;

        final var shares = TssUtils.computeNodeShares(entries, maxTssMessagesPerNode);

        assertEquals(2, shares.size());
        assertEquals(10L, shares.get(1L));
        assertEquals(5L, shares.get(2L));
    }

    @Test
    public void testComputeNodeSharesEmptyRoster() {
        List<RosterEntry> entries = List.of();
        long maxTssMessagesPerNode = 10L;

        final var shares = TssUtils.computeNodeShares(entries, maxTssMessagesPerNode);

        assertTrue(shares.isEmpty());
    }
}
