/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.migration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerklePayerRecords;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordsStorageAdapterTest {
    private static final EntityNum SOME_NUM = EntityNum.fromInt(1234);

    @Mock private @Nullable MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private @Nullable MerkleMap<EntityNum, MerklePayerRecords> payerRecords;

    private RecordsStorageAdapter subject;

    @Test
    void removingLegacyPayerIsNoop() {
        withLegacySubject();
        subject.forgetPayer(SOME_NUM);
        verifyNoInteractions(accounts);
    }

    @Test
    void removingDedicatedPayerRequiresWork() {
        withDedicatedSubject();
        subject.forgetPayer(SOME_NUM);
        verify(payerRecords).remove(SOME_NUM);
    }

    @Test
    void creatingLegacyPayerIsNoop() {
        withLegacySubject();
        subject.prepForPayer(SOME_NUM);
        verifyNoInteractions(accounts);
    }

    @Test
    void creatingDedicatedPayerRequiresWork() {
        withDedicatedSubject();
        subject.prepForPayer(SOME_NUM);
        verify(payerRecords).put(eq(SOME_NUM), any());
    }

    private void withLegacySubject() {
        subject = RecordsStorageAdapter.fromLegacy(accounts);
    }

    private void withDedicatedSubject() {
        subject = RecordsStorageAdapter.fromDedicated(payerRecords);
    }
}
