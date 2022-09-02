package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.impl.SetApprovalForAllPrecompile.decodeSetApprovalForAll;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.store.contracts.precompile.impl.SetApprovalForAllPrecompile;
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
class SetApprovalForAllPrecompileTest {
    public static final Bytes SET_APPROVAL_FOR_ALL_INPUT_ERC =
            Bytes.fromHexString(
                    "0xa22cb46500000000000000000000000000000000000000000000000000000000000006400000000000000000000000000000000000000000000000000000000000000001");

    public static final Bytes SET_APPROVAL_FOR_ALL_INPUT_HAPI =
            Bytes.fromHexString(
                    "0x367605ca000000000000000000000000000000000000000000000000000000000000123400000000000000000000000000000000000000000000000000000000000006400000000000000000000000000000000000000000000000000000000000000001");
    private static final long TOKEN_NUM_HAPI_TOKEN = 0x1234;
    private static final long ACCOUNT_NUM_APPROVE_ALL_TO = 0x640;
    private static final TokenID TOKEN_ID =
            TokenID.newBuilder().setTokenNum(TOKEN_NUM_HAPI_TOKEN).build();
    private MockedStatic<SetApprovalForAllPrecompile> setApprovalForAllPrecompile;

    @BeforeEach
    void setUp() {
        setApprovalForAllPrecompile = Mockito.mockStatic(SetApprovalForAllPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        setApprovalForAllPrecompile.close();
    }

    @Test
    void decodeSetApprovalForAllERC() {
        setApprovalForAllPrecompile
                .when(
                        () ->
                                decodeSetApprovalForAll(
                                        SET_APPROVAL_FOR_ALL_INPUT_ERC, TOKEN_ID, identity()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeSetApprovalForAll(SET_APPROVAL_FOR_ALL_INPUT_ERC, TOKEN_ID, identity());

        assertEquals(TOKEN_ID.getTokenNum(), decodedInput.tokenId().getTokenNum());
        assertEquals(ACCOUNT_NUM_APPROVE_ALL_TO, decodedInput.to().getAccountNum());
        assertTrue(decodedInput.approved());
    }

    @Test
    void decodeSetApprovalForAllHAPI() {
        setApprovalForAllPrecompile
                .when(
                        () ->
                                decodeSetApprovalForAll(
                                        SET_APPROVAL_FOR_ALL_INPUT_ERC, null, identity()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeSetApprovalForAll(SET_APPROVAL_FOR_ALL_INPUT_HAPI, null, identity());

        assertEquals(TOKEN_NUM_HAPI_TOKEN, decodedInput.tokenId().getTokenNum());
        assertEquals(ACCOUNT_NUM_APPROVE_ALL_TO, decodedInput.to().getAccountNum());
        assertTrue(decodedInput.approved());
    }
}
