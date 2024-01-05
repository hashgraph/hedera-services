/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual;

import static org.apache.commons.lang3.RandomUtils.nextInt;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.provider.Arguments;

class VirtualMapFactoryTest {

    @TempDir
    private Path tempDir;

    private VirtualMapFactory subject;

    @BeforeEach
    void setup() {
        subject = new VirtualMapFactory(tempDir);
    }

    private static Stream<Arguments> falseTrueNull() {
        return Stream.of(Arguments.of(false), Arguments.of(true), Arguments.of((Boolean) null));
    }

    @Test
    void virtualizedUniqueTokenStorage_whenEmpty_canProperlyInsertAndFetchValues() {
        final VirtualMap<UniqueTokenKey, UniqueTokenValue> map = subject.newVirtualizedUniqueTokenStorage();
        assertThat(map.isEmpty()).isTrue();

        map.put(
                new UniqueTokenKey(123L, 456L),
                new UniqueTokenValue(789L, 123L, "hello world".getBytes(), RichInstant.MISSING_INSTANT));

        assertThat(map.get(new UniqueTokenKey(123L, 111L))).isNull();
        final var value = map.get(new UniqueTokenKey(123L, 456L));
        assertThat(value).isNotNull();
        assertThat(value.getOwnerAccountNum()).isEqualTo(789L);
        assertThat(value.getMetadata()).isEqualTo("hello world".getBytes());
    }

    @Test
    void newOnDiskTokensRels_whenEmpty_canProperlyInsertAndFetchValues() {
        final VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> map = subject.newOnDiskTokenRels();

        assertThat(map.isEmpty()).isTrue();

        final EntityNumVirtualKey key = new EntityNumVirtualKey(nextLong());
        final OnDiskTokenRel value = new OnDiskTokenRel();
        value.setBalance(nextLong());
        value.setNumbers(nextLong());
        map.put(key, value);

        final OnDiskTokenRel valueFromMap = map.get(key);
        assertThat(valueFromMap).isEqualTo(value);
    }

    @Test
    void newVirtualizedIterableStorage_whenEmpty_canProperlyInsertAndFetchValues() {
        final VirtualMap<ContractKey, IterableContractValue> map = subject.newVirtualizedIterableStorage();

        assertThat(map.isEmpty()).isTrue();

        final ContractKey key = new ContractKey(nextLong(), nextLong());
        final IterableContractValue value = new IterableContractValue(nextLong());
        map.put(key, value);

        final IterableContractValue valueFromMap = map.get(key);
        assertThat(valueFromMap).isEqualTo(value);
    }

    @Test
    void newContractStorage_whenEmpty_canProperlyInsertAndFetchValues() {
        final VirtualMap<ContractKey, IterableContractValue> map;
        map = subject.newVirtualizedIterableStorage();

        assertThat(map.isEmpty()).isTrue();

        final ContractKey key = new ContractKey(nextLong(), nextLong());
        final IterableContractValue value = new IterableContractValue(nextLong());
        map.put(key, value);

        final IterableContractValue valueFromMap = map.get(key);
        assertThat(valueFromMap).isEqualTo(value);
    }

    @Test
    void newScheduleListStorage_whenEmpty_canProperlyInsertAndFetchValues() {
        final VirtualMap<EntityNumVirtualKey, ScheduleVirtualValue> map;
        map = subject.newScheduleListStorage();

        assertThat(map.isEmpty()).isTrue();

        final EntityNumVirtualKey key = new EntityNumVirtualKey(nextLong());
        final ScheduleVirtualValue value = new ScheduleVirtualValue();
        map.put(key, value);

        final ScheduleVirtualValue valueFromMap = map.get(key);
        assertThat(valueFromMap).isEqualTo(value);
    }

    @Test
    void newScheduleTemporalStorage_whenEmpty_canProperlyInsertAndFetchValues() {
        final VirtualMap<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> map;
        map = subject.newScheduleTemporalStorage();

        assertThat(map.isEmpty()).isTrue();

        final SecondSinceEpocVirtualKey key = new SecondSinceEpocVirtualKey(nextLong());
        final ScheduleSecondVirtualValue value = new ScheduleSecondVirtualValue();
        map.put(key, value);

        final ScheduleSecondVirtualValue valueFromMap = map.get(key);
        assertThat(valueFromMap).isEqualTo(value);
    }

    @Test
    void newScheduleEqualityStorage_whenEmpty_canProperlyInsertAndFetchValues() {
        final VirtualMap<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> map;
        map = subject.newScheduleEqualityStorage();

        assertThat(map.isEmpty()).isTrue();

        final ScheduleEqualityVirtualKey key = new ScheduleEqualityVirtualKey(nextLong());
        final ScheduleEqualityVirtualValue value = new ScheduleEqualityVirtualValue();
        map.put(key, value);

        final ScheduleEqualityVirtualValue valueFromMap = map.get(key);
        assertThat(valueFromMap).isEqualTo(value);
    }

    @Test
    void newOnDiskAccountStorage_whenEmpty_canProperlyInsertAndFetchValues() {
        final VirtualMap<EntityNumVirtualKey, OnDiskAccount> map = subject.newOnDiskAccountStorage();

        assertThat(map.isEmpty()).isTrue();

        final EntityNumVirtualKey key = new EntityNumVirtualKey(nextLong());
        final OnDiskAccount value = new OnDiskAccount();
        map.put(key, value);

        final OnDiskAccount valueFromMap = map.get(key);
        assertThat(valueFromMap).isEqualTo(value);
    }

    @Test
    void newVirtualizedBlobs_whenEmpty_canProperlyInsertAndFetchValues() {
        final VirtualMap<VirtualBlobKey, VirtualBlobValue> map = subject.newVirtualizedBlobs();

        assertThat(map.isEmpty()).isTrue();

        final VirtualBlobKey key = new VirtualBlobKey(VirtualBlobKey.Type.CONTRACT_BYTECODE, nextInt());
        final VirtualBlobValue value = new VirtualBlobValue();
        map.put(key, value);

        final VirtualBlobValue valueFromMap = map.get(key);
        assertThat(valueFromMap).isEqualTo(value);
    }
}
