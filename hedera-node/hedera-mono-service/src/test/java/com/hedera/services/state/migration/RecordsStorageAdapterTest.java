package com.hedera.services.state.migration;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerklePayerRecords;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.annotation.Nullable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RecordsStorageAdapterTest {
    private static final EntityNum SOME_NUM =EntityNum.fromInt(1234);

    @Mock
    private @Nullable MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock
    private @Nullable MerkleMap<EntityNum, MerklePayerRecords> payerRecords;

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