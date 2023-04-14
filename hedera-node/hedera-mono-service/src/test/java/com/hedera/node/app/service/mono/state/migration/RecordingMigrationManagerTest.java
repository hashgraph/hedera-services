/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.migration;

import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.context.StateChildren;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccountState;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.submerkle.RecordingSequenceNumber;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.replay.PbjLeafConverters;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import java.time.Instant;
import java.util.List;
import java.util.SplittableRandom;

import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.node.app.service.mono.state.logic.RecordingStatusChangeListener.FINAL_TOPICS_ASSET;
import static com.hedera.node.app.service.mono.state.migration.RecordingMigrationManager.INITIAL_ACCOUNTS_ASSET;
import static com.hedera.node.app.service.mono.utils.replay.PbjLeafConverters.accountFromMerkle;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecordingMigrationManagerTest {
    private static final int NUM_MOCK_ACCOUNTS = 10;

    @Mock
    private StateChildren stateChildren;

    @Mock
    private MigrationManager delegate;

    @Mock
    private ReplayAssetRecording assetRecording;

    private MerkleMap<EntityNum, MerkleAccount> accounts;

    private RecordingMigrationManager subject;

    @BeforeEach
    void setUp() {
        subject = new RecordingMigrationManager(delegate, stateChildren, assetRecording);
    }

    @Test
    void removesSystemConsumedEntityIdsAndDumpsInitialAccounts() {
        final var inOrder = Mockito.inOrder(stateChildren, delegate, assetRecording);
        final var now = Instant.ofEpochSecond(1_234_567L);

        registerForAccounts();
        givenSomeAccounts();

        subject.publishMigrationRecords(now);

        inOrder.verify(delegate).publishMigrationRecords(now);
        inOrder.verify(assetRecording).restartReplayAsset(RecordingSequenceNumber.REPLAY_SEQ_NOS_ASSET);
        for (int i = 0; i < NUM_MOCK_ACCOUNTS; i++) {
            final var account = accounts.get(EntityNum.fromLong(i));
            final var encodedAccount = PbjConverter.toB64Encoding(accountFromMerkle(account), Account.class);
            verify(assetRecording).appendPlaintextToAsset(INITIAL_ACCOUNTS_ASSET, encodedAccount);
        }
    }

    private static void registerForAccounts() {
        try {
            ConstructableRegistry.getInstance()
                    .registerConstructable(new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(new ClassConstructorPair(MerkleAccountState.class, MerkleAccountState::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(new ClassConstructorPair(FCQueue.class, FCQueue::new));
        } catch (final ConstructableRegistryException e) {
            throw new IllegalStateException(e);
        }
    }


    private void givenSomeAccounts() {
        accounts = new MerkleMap<>();
        final var r = new SplittableRandom(1_234_567L);
        final var source = new SeededPropertySource(r);
        for (int i = 0; i < NUM_MOCK_ACCOUNTS; i++) {
            final var accountState = source.nextAccountState();
            final var merkleAccount = new MerkleAccount(List.of(accountState, new FCQueue<>()));
            accounts.put(EntityNum.fromLong(i), merkleAccount);
        }
        given(stateChildren.accounts()).willReturn(AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts)));
    }
}
