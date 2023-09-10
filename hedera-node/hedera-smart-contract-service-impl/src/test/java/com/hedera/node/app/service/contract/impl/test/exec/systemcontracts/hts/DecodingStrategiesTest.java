/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.RECEIVER_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.RECEIVER_ID;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DecodingStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersCall;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DecodingStrategiesTest {
    @Mock
    private AddressIdConverter addressIdConverter;

    private final DecodingStrategies subject = new DecodingStrategies();

    @Test
    void hrcTransferNftFromWorks() {
        final var encoded = ClassicTransfersCall.HRC_TRANSFER_NFT_FROM
                .encodeCallWithArgs(
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        OWNER_HEADLONG_ADDRESS,
                        RECEIVER_HEADLONG_ADDRESS,
                        BigInteger.TWO)
                .array();
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);
        givenConvertibleCredit(RECEIVER_HEADLONG_ADDRESS, RECEIVER_ID);

        final var body = subject.decodeHrcTransferNftFrom(encoded, addressIdConverter);

        assertTokenTransfersCount(body, 1);
        assertOwnershipChange(body, NON_FUNGIBLE_TOKEN_ID, OWNER_ID, RECEIVER_ID, 2, true);
    }

    private void assertTokenTransfersCount(@NonNull final TransactionBody body, final int n) {
        assertEquals(
                n,
                body.cryptoTransferOrThrow().tokenTransfersOrElse(emptyList()).size());
    }

    private void assertOwnershipChange(
            @NonNull final TransactionBody body,
            @NonNull final TokenID tokenId,
            @NonNull final AccountID from,
            @NonNull final AccountID to,
            final long serialNo,
            final boolean approval) {
        for (final var tokenTransfers : body.cryptoTransferOrThrow().tokenTransfersOrThrow()) {
            if (tokenTransfers.tokenOrThrow().equals(tokenId)) {
                assertTrue(tokenTransfers.transfersOrElse(emptyList()).isEmpty());
                for (final var ownershipChange : tokenTransfers.nftTransfersOrThrow()) {
                    if (ownershipChange.senderAccountIDOrThrow().equals(from)
                            && ownershipChange.receiverAccountIDOrThrow().equals(to)
                            && ownershipChange.serialNumber() == serialNo
                            && ownershipChange.isApproval() == approval) {
                        return;
                    }
                }
            }
        }
        Assertions.fail("No matching ownership change found");
    }

    private void givenConvertible(@NonNull final Address address, @NonNull final AccountID id) {
        given(addressIdConverter.convert(address)).willReturn(id);
    }

    private void givenConvertibleCredit(@NonNull final Address address, @NonNull final AccountID id) {
        given(addressIdConverter.convertCredit(address)).willReturn(id);
    }
}
