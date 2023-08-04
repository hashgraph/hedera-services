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

package com.hedera.node.app.service.contract.impl.test.infra;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.infra.IterableStorageManager;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.contract.impl.state.StorageSizeChange;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IterableStorageManagerTest {
    @Mock
    private HederaOperations hederaOperations;

    @Mock
    private ContractStateStore store;

    private final IterableStorageManager subject = new IterableStorageManager();

    @Test
    void rewriteUpdatesKvCountStorageMetadataOnly() {
        final var sizeChanges = List.of(
                new StorageSizeChange(1L, 2, 3), new StorageSizeChange(2L, 3, 2), new StorageSizeChange(3L, 4, 4));

        subject.rewrite(hederaOperations, List.of(), sizeChanges, store);

        verify(hederaOperations).updateStorageMetadata(1L, Bytes.EMPTY, 1);
        verify(hederaOperations).updateStorageMetadata(2L, Bytes.EMPTY, -1);
        verify(hederaOperations, never()).updateStorageMetadata(2L, Bytes.EMPTY, 0);
    }
}
