package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.impl.TokenURIPrecompile.decodeTokenUriNFT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.store.contracts.precompile.impl.TokenURIPrecompile;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenURIPrecompileTest {

    private static final Bytes TOKEN_URI_INPUT =
            Bytes.fromHexString(
                    "0xc87b56dd0000000000000000000000000000000000000000000000000000000000000001");
    private MockedStatic<TokenURIPrecompile> tokenURIPrecompile;

    @BeforeEach
    void setUp() {
        tokenURIPrecompile = Mockito.mockStatic(TokenURIPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        tokenURIPrecompile.close();
    }

    @Test
    void decodeTokenUriInput() {
        tokenURIPrecompile.when(() -> decodeTokenUriNFT(TOKEN_URI_INPUT)).thenCallRealMethod();
        final var decodedInput = decodeTokenUriNFT(TOKEN_URI_INPUT);

        assertEquals(1, decodedInput.serialNo());
    }


}
