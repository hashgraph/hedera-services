package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.impl.IsTokenPrecompile.decodeIsToken;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.store.contracts.precompile.impl.IsTokenPrecompile;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IsTokenPrecompileTest {
    private static final Bytes IS_TOKEN_INPUT =
            Bytes.fromHexString(
                    "0x19f373610000000000000000000000000000000000000000000000000000000000000b03");
    private MockedStatic<IsTokenPrecompile> isTokenPrecompile;

    @BeforeEach
    void setUp() {
        isTokenPrecompile = Mockito.mockStatic(IsTokenPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        isTokenPrecompile.close();
    }

    @Test
    void decodeIsTokenAsExpected() {
        isTokenPrecompile.when(() -> decodeIsToken(IS_TOKEN_INPUT)).thenCallRealMethod();
        final var decodedInput = decodeIsToken(IS_TOKEN_INPUT);
        assertEquals(TokenID.newBuilder().setTokenNum(2819).build(), decodedInput.tokenID());
        assertEquals(-1, decodedInput.serialNumber());
    }
}
