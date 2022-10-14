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
class OnDiskAccountSupplierTest {
    private final OnDiskAccountSupplier subject = new OnDiskAccountSupplier();

    @Mock private SerializableDataInputStream in;
    @Mock private SerializableDataOutputStream out;

    @Test
    void ioIsNoop() throws IOException {
        subject.serialize(out);
        subject.deserialize(in, 1);

        verifyNoInteractions(in);
    }

    @Test
    void hasExpectedProfile() {
        assertEquals(0xe5d01987257f5efcL, subject.getClassId());
        assertEquals(1, subject.getVersion());
    }

    @Test
    void createsOnDiskAccounts() {
        assertInstanceOf(OnDiskAccount.class, subject.get());
    }
}