package com.hedera.services.state.virtual.entities;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OnDiskTokenRelSupplierTest {
    private final OnDiskTokenRelSupplier subject = new OnDiskTokenRelSupplier();

    @Mock
    private SerializableDataInputStream in;
    @Mock private SerializableDataOutputStream out;

    @Test
    void ioIsNoop() throws IOException {
        subject.serialize(out);
        subject.deserialize(in, 1);

        verifyNoInteractions(in);
    }

    @Test
    void hasExpectedProfile() {
        assertEquals(0x0e52cff909625f55L, subject.getClassId());
        assertEquals(1, subject.getVersion());
    }

    @Test
    void createsOnDiskAccounts() {
        assertInstanceOf(OnDiskTokenRel.class, subject.get());
    }
}