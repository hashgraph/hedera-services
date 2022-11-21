package com.hedera.node.app.spi.numbers;

import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.SigWaivers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class PreHandleContextTest {
    @Mock
    private SigWaivers sigWaivers;

    @Test
    void checksNullParams(){
        assertThrows(NullPointerException.class, () -> new PreHandleContext(null));
    }

    @Test
    void passesWIthNonNullParams(){
        assertDoesNotThrow(() -> new PreHandleContext(sigWaivers));
    }
}
