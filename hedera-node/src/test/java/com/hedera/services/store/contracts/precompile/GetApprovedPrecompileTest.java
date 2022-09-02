package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.impl.GetApprovedPrecompile.decodeGetApproved;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.store.contracts.precompile.impl.GetApprovedPrecompile;
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
class GetApprovedPrecompileTest {
    private static final Bytes GET_APPROVED_INPUT_ERC =
            Bytes.fromHexString(
                    "0x081812fc0000000000000000000000000000000000000000000000000000000000000001");
    private static final Bytes GET_APPROVED_INPUT_HAPI =
            Bytes.fromHexString(
                    "0x098f236600000000000000000000000000000000000000000000000000000000000012340000000000000000000000000000000000000000000000000000000000000001");

    private static final long TOKEN_NUM_HAPI_TOKEN = 0x1234;

    private static final TokenID TOKEN_ID =
            TokenID.newBuilder().setTokenNum(TOKEN_NUM_HAPI_TOKEN).build();
    private MockedStatic<GetApprovedPrecompile> getApprovedPrecompile;

    @BeforeEach
    void setUp() {
        getApprovedPrecompile = Mockito.mockStatic(GetApprovedPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        getApprovedPrecompile.close();
    }

    @Test
    void decodeGetApprovedInputERC() {
        getApprovedPrecompile
                .when(() -> decodeGetApproved(GET_APPROVED_INPUT_ERC, TOKEN_ID))
                .thenCallRealMethod();
        final var decodedInput = decodeGetApproved(GET_APPROVED_INPUT_ERC, TOKEN_ID);

        assertEquals(TOKEN_ID.getTokenNum(), decodedInput.tokenId().getTokenNum());
        assertEquals(1, decodedInput.serialNo());
    }

    @Test
    void decodeGetApprovedInput() {
        getApprovedPrecompile
                .when(() -> decodeGetApproved(GET_APPROVED_INPUT_ERC, TOKEN_ID))
                .thenCallRealMethod();
        final var decodedInput = decodeGetApproved(GET_APPROVED_INPUT_HAPI, null);

        assertEquals(TOKEN_NUM_HAPI_TOKEN, decodedInput.tokenId().getTokenNum());
        assertEquals(1, decodedInput.serialNo());
    }
}
