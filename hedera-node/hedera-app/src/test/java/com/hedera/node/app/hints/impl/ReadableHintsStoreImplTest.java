package com.hedera.node.app.hints.impl;

import com.swirlds.state.spi.ReadableStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class ReadableHintsStoreImplTest {
    @Mock
    private ReadableStates states;

    private ReadableHintsStoreImpl subject;

    @BeforeEach
    void setUp() {
        subject = new ReadableHintsStoreImpl(states);
    }

    @Test
    void constructorWorks() {
        assertNotNull(subject);
    }
}