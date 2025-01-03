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

package com.hedera.node.app.tss.schemas;

import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_MESSAGE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_VOTE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0580TssBaseSchema.TSS_ENCRYPTION_KEYS_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.tss.TssEncryptionKeys;
import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.node.internal.network.Network;
import com.hedera.node.internal.network.NodeMetadata;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TssBaseTransplantSchemaTest {
    private static final long ROUND_NO = 666L;
    private static final Bytes FAKE_LEDGER_ID = Bytes.fromHex("abcd");
    private static final Bytes NODE1_ENCRYPTION_KEY = Bytes.wrap("NODE1_ENCRYPTION_KEY");
    private static final Bytes NODE2_ENCRYPTION_KEY = Bytes.wrap("NODE2_ENCRYPTION_KEY");
    private static final Network NETWORK_WITH_KEYS = Network.newBuilder()
            .nodeMetadata(
                    NodeMetadata.newBuilder()
                            .rosterEntry(RosterEntry.newBuilder().nodeId(1L).build())
                            .tssEncryptionKey(NODE1_ENCRYPTION_KEY)
                            .build(),
                    NodeMetadata.newBuilder()
                            .rosterEntry(RosterEntry.newBuilder().nodeId(2L).build())
                            .tssEncryptionKey(NODE2_ENCRYPTION_KEY)
                            .build())
            .build();
    private static final Network NETWORK_WITHOUT_KEYS = NETWORK_WITH_KEYS
            .copyBuilder()
            .nodeMetadata(NETWORK_WITH_KEYS.nodeMetadata().stream()
                    .map(nm -> nm.copyBuilder().tssEncryptionKey(Bytes.EMPTY).build())
                    .toList())
            .build();
    private static Network NETWORK_WITH_TSS_KEY_MATERIAL = NETWORK_WITH_KEYS
            .copyBuilder()
            .ledgerId(FAKE_LEDGER_ID)
            .tssMessages(List.of(
                    new TssMessageTransactionBody(Bytes.EMPTY, Bytes.EMPTY, 1L, Bytes.EMPTY),
                    new TssMessageTransactionBody(Bytes.EMPTY, Bytes.EMPTY, 2L, Bytes.EMPTY)))
            .build();

    @Mock
    private MigrationContext ctx;

    @Mock
    private StartupNetworks startupNetworks;

    @Mock
    private WritableStates writableStates;

    @Mock
    private WritableKVState<EntityNumber, TssEncryptionKeys> writableEncryptionKeys;

    @Mock
    private WritableKVState<TssVoteMapKey, TssVoteTransactionBody> writableVotes;

    @Mock
    private WritableKVState<TssMessageMapKey, TssMessageTransactionBody> writableMessages;

    @Mock
    private ReadableRosterStore readableRosterStore;

    private final TssBaseTransplantSchema subject =
            new V0580TssBaseSchema(WritableTssStore::new, () -> readableRosterStore);

    @Test
    void noOpWithoutTssEnabled() {
        givenConfig(false);
        subject.restart(ctx);
        verifyNoMoreInteractions(ctx);
    }

    @Test
    void notGenesisAndNoOverridePresentIsNoop() {
        givenConfig(true);
        given(ctx.roundNumber()).willReturn(ROUND_NO);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<EntityNumber, TssEncryptionKeys>get(TSS_ENCRYPTION_KEYS_KEY))
                .willReturn(writableEncryptionKeys);
        given(writableStates.<TssVoteMapKey, TssVoteTransactionBody>get(TSS_VOTE_MAP_KEY))
                .willReturn(writableVotes);
        given(writableStates.<TssMessageMapKey, TssMessageTransactionBody>get(TSS_MESSAGE_MAP_KEY))
                .willReturn(writableMessages);
        given(writableEncryptionKeys.keys()).willReturn(mock(Iterator.class));

        subject.restart(ctx);

        verify(startupNetworks).overrideNetworkFor(ROUND_NO);
    }

    @Test
    void withOverrideSetsEncryptionKeysFromNetwork() {
        final EntityNumber entityNumber1 = new EntityNumber(1);
        final EntityNumber entityNumber2 = new EntityNumber(2);

        givenConfig(true);
        given(ctx.roundNumber()).willReturn(ROUND_NO);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(startupNetworks.overrideNetworkFor(ROUND_NO)).willReturn(Optional.of(NETWORK_WITH_KEYS));
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<EntityNumber, TssEncryptionKeys>get(TSS_ENCRYPTION_KEYS_KEY))
                .willReturn(writableEncryptionKeys);
        given(writableEncryptionKeys.keys())
                .willReturn(List.of(entityNumber1, entityNumber2).iterator());

        subject.restart(ctx);

        verify(writableEncryptionKeys).put(entityNumber1, new TssEncryptionKeys(NODE1_ENCRYPTION_KEY, Bytes.EMPTY));
        verify(writableEncryptionKeys).put(entityNumber2, new TssEncryptionKeys(NODE2_ENCRYPTION_KEY, Bytes.EMPTY));
    }

    @Test
    void ignoresEmptyEncryptionKeys() {
        givenConfig(true);
        given(ctx.roundNumber()).willReturn(ROUND_NO);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(startupNetworks.overrideNetworkFor(ROUND_NO)).willReturn(Optional.of(NETWORK_WITHOUT_KEYS));
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<EntityNumber, TssEncryptionKeys>get(TSS_ENCRYPTION_KEYS_KEY))
                .willReturn(writableEncryptionKeys);
        given(writableEncryptionKeys.keys()).willReturn(mock(Iterator.class));

        subject.restart(ctx);

        verify(writableEncryptionKeys, never()).put(any(), any());
    }

    @Test
    void setsTssMaterialFromNetworkMessagesIfPresent() {
        final var roster = RosterUtils.rosterFrom(NETWORK_WITH_TSS_KEY_MATERIAL);
        final var rosterHash = RosterUtils.hash(roster).getBytes();

        subject.setTssMessageOpsAndVotes(NETWORK_WITH_TSS_KEY_MATERIAL, writableMessages, writableVotes);

        verify(writableMessages)
                .put(
                        new TssMessageMapKey(rosterHash, 0L),
                        NETWORK_WITH_TSS_KEY_MATERIAL.tssMessages().getFirst());
        verify(writableMessages)
                .put(
                        new TssMessageMapKey(rosterHash, 1L),
                        NETWORK_WITH_TSS_KEY_MATERIAL.tssMessages().getLast());
        verify(writableVotes).put(eq(new TssVoteMapKey(rosterHash, 1L)), any());
        verify(writableVotes).put(eq(new TssVoteMapKey(rosterHash, 2L)), any());
    }

    private void givenConfig(final boolean tssEnabled) {
        final var configBuilder = HederaTestConfigBuilder.create().withValue("tss.keyCandidateRoster", tssEnabled);
        given(ctx.appConfig()).willReturn(configBuilder.getOrCreateConfig());
    }
}
