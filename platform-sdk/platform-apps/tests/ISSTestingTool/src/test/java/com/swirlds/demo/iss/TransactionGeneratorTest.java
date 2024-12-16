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

package com.swirlds.demo.iss;

import static com.swirlds.common.utility.ByteUtils.intToByteArray;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.Platform;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TransactionGeneratorTest {

    @Test
    void startTransactionGenerator() {
        // Given
        final var random = new Random();
        final var platform = mock(Platform.class);
        final var roster = new Roster(List.of(new RosterEntry(1, 5, Bytes.EMPTY, new ArrayList<>())));
        final var networkWideTransactionsPerSecond = 100;
        final var issTestingToolState = mock(ISSTestingToolState.class);

        // When
        when(platform.getRoster()).thenReturn(roster);
        when(issTestingToolState.encodeSystemTransaction(any()))
                .thenReturn(Bytes.wrap(intToByteArray(random.nextInt())));

        final var transactionGenerator =
                new TransactionGenerator(random, platform, networkWideTransactionsPerSecond, issTestingToolState);
        transactionGenerator.start();

        // Then
        verify(platform, atLeastOnce()).getRoster();
        verify(issTestingToolState, atLeastOnce()).encodeSystemTransaction(any());
        verify(platform, atLeastOnce()).createTransaction(any());
    }
}
