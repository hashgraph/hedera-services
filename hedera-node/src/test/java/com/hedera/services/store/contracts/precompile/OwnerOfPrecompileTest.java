package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.impl.OwnerOfPrecompile.decodeOwnerOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.store.contracts.precompile.impl.OwnerOfPrecompile;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerOfPrecompileTest {

    private static final Bytes OWNER_OF_INPUT =
            Bytes.fromHexString(
                    "0x6352211e0000000000000000000000000000000000000000000000000000000000000001");
    private MockedStatic<OwnerOfPrecompile> ownerOfPrecompile;

    @BeforeEach
    void setUp() {
        ownerOfPrecompile = Mockito.mockStatic(OwnerOfPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        ownerOfPrecompile.close();
    }

    @Test
    void decodeOwnerOfInput() {
        ownerOfPrecompile
                .when(() -> OwnerOfPrecompile.decodeOwnerOf(OWNER_OF_INPUT))
                .thenCallRealMethod();
        final var decodedInput = decodeOwnerOf(OWNER_OF_INPUT);

        assertEquals(1, decodedInput.serialNo());
    }
}
