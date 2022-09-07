/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.impl.ERCTransferPrecompile.decodeERCTransfer;
import static com.hedera.services.store.contracts.precompile.impl.ERCTransferPrecompile.decodeERCTransferFrom;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ERCTransferPrecompileTest {
    private static final Bytes TRANSFER_INPUT =
            Bytes.fromHexString(
                    "0xa9059cbb00000000000000000000000000000000000000000000000000000000000005a50000000000000000000000000000000000000000000000000000000000000002");

    private static final Bytes TRANSFER_FROM_FUNGIBLE_INPUT =
            Bytes.fromHexString(
                    "0x23b872dd00000000000000000000000000000000000000000000000000000000000005aa00000000000000000000000000000000000000000000000000000000000005ab0000000000000000000000000000000000000000000000000000000000000005");

    private static final Bytes TRANSFER_FROM_NON_FUNGIBLE_INPUT =
            Bytes.fromHexString(
                    "0x23b872dd00000000000000000000000000000000000000000000000000000000000003e900000000000000000000000000000000000000000000000000000000000003ea0000000000000000000000000000000000000000000000000000000000000001");

    private static final long TOKEN_NUM_HAPI_TOKEN = 0x1234;
    private static final TokenID TOKEN_ID =
            TokenID.newBuilder().setTokenNum(TOKEN_NUM_HAPI_TOKEN).build();
    @Mock private WorldLedgers ledgers;

    @Test
    void decodeTransferInput() {
        final var decodedInput =
                decodeERCTransfer(
                        TRANSFER_INPUT, TOKEN_ID, AccountID.getDefaultInstance(), identity());
        final var fungibleTransfer = decodedInput.get(0).fungibleTransfers().get(0);

        assertTrue(fungibleTransfer.receiver().getAccountNum() > 0);
        assertEquals(2, fungibleTransfer.amount());
    }

    @Test
    void decodeTransferFromFungibleInputUsingApprovalIfNotOwner() {
        final var notOwner = new EntityId(0, 0, 1002);

        final var decodedInput =
                decodeERCTransferFrom(
                        TRANSFER_FROM_FUNGIBLE_INPUT,
                        TOKEN_ID,
                        true,
                        identity(),
                        ledgers,
                        notOwner);
        final var fungibleTransfer = decodedInput.get(0).fungibleTransfers();

        assertTrue(fungibleTransfer.get(0).receiver().getAccountNum() > 0);
        assertTrue(fungibleTransfer.get(1).sender().getAccountNum() > 0);
        assertTrue(fungibleTransfer.get(1).isApproval());
        assertEquals(5, fungibleTransfer.get(0).amount());
    }

    @Test
    void decodeTransferFromFungibleInputDoesntUseApprovalIfFromIsOperator() {
        final var fromOp = new EntityId(0, 0, 1450);

        final var decodedInput =
                decodeERCTransferFrom(
                        TRANSFER_FROM_FUNGIBLE_INPUT, TOKEN_ID, true, identity(), ledgers, fromOp);
        final var fungibleTransfer = decodedInput.get(0).fungibleTransfers();

        assertTrue(fungibleTransfer.get(0).receiver().getAccountNum() > 0);
        assertTrue(fungibleTransfer.get(1).sender().getAccountNum() > 0);
        assertFalse(fungibleTransfer.get(1).isApproval());
        assertEquals(5, fungibleTransfer.get(0).amount());
    }

    @Test
    void decodeTransferFromNonFungibleInputUsingApprovalIfNotOwner() {
        final var notOwner = new EntityId(0, 0, 1002);

        final var decodedInput =
                decodeERCTransferFrom(
                        TRANSFER_FROM_NON_FUNGIBLE_INPUT,
                        TOKEN_ID,
                        false,
                        identity(),
                        ledgers,
                        notOwner);
        final var nftTransfer = decodedInput.get(0).nftExchanges().get(0).asGrpc();

        assertTrue(nftTransfer.getSenderAccountID().getAccountNum() > 0);
        assertTrue(nftTransfer.getReceiverAccountID().getAccountNum() > 0);
        assertEquals(1, nftTransfer.getSerialNumber());
        assertTrue(nftTransfer.getIsApproval());
    }

    @Test
    void decodeTransferFromNonFungibleInputIfOwner() {
        final var callerId = new EntityId(0, 0, 1001);
        given(ledgers.ownerIfPresent(any())).willReturn(callerId);

        final var decodedInput =
                decodeERCTransferFrom(
                        TRANSFER_FROM_NON_FUNGIBLE_INPUT,
                        TOKEN_ID,
                        false,
                        identity(),
                        ledgers,
                        callerId);
        final var nftTransfer = decodedInput.get(0).nftExchanges().get(0).asGrpc();

        assertTrue(nftTransfer.getSenderAccountID().getAccountNum() > 0);
        assertTrue(nftTransfer.getReceiverAccountID().getAccountNum() > 0);
        assertEquals(1, nftTransfer.getSerialNumber());
        assertFalse(nftTransfer.getIsApproval());
    }
}
