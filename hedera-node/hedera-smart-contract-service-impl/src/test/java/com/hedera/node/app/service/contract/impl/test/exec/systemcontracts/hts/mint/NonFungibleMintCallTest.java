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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.mint;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.MintTranslator.MINT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asBytesResult;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.NonFungibleMintCall;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import com.hedera.node.app.service.token.records.TokenMintRecordBuilder;
import java.math.BigInteger;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class NonFungibleMintCallTest extends HtsCallTestBase {
    private static final org.hyperledger.besu.datatypes.Address FRAME_SENDER_ADDRESS = EIP_1014_ADDRESS;
    private static final List<com.hedera.pbj.runtime.io.buffer.Bytes> METADATA =
            List.of(com.hedera.pbj.runtime.io.buffer.Bytes.wrap("metadata"));

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private TokenMintRecordBuilder recordBuilder;

    private NonFungibleMintCall subject;

    @Mock
    private TransactionBody syntheticTransfer;

    @Test
    void revertsOnMissingToken() {
        subject = new NonFungibleMintCall(
                gasCalculator,
                mockEnhancement(),
                METADATA,
                null,
                verificationStrategy,
                FRAME_SENDER_ADDRESS,
                addressIdConverter,
                syntheticTransfer);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(Bytes.wrap(INVALID_TOKEN_ID.protoName().getBytes()), result.getOutput());
    }

    @Test
    void mintHappyPathSucceedsWithTrue() {
        given(addressIdConverter.convert(asHeadlongAddress(FRAME_SENDER_ADDRESS)))
                .willReturn(A_NEW_ACCOUNT_ID);
        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(A_NEW_ACCOUNT_ID),
                        eq(TokenMintRecordBuilder.class)))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(ResponseCodeEnum.SUCCESS);
        given(recordBuilder.getSerialNumbers()).willReturn(List.of(1L));
        subject = subjectForMint();

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(MINT.getOutputs()
                        .encodeElements((long) SUCCESS.protoOrdinal(), BigInteger.valueOf(0), new long[] {1})),
                result.getOutput());
    }

    @Test
    void unhappyPathRevertsWithReason() {
        given(addressIdConverter.convert(asHeadlongAddress(FRAME_SENDER_ADDRESS)))
                .willReturn(A_NEW_ACCOUNT_ID);
        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(A_NEW_ACCOUNT_ID),
                        eq(TokenMintRecordBuilder.class)))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(INSUFFICIENT_ACCOUNT_BALANCE);

        subject = subjectForMint();

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(MINT.getOutputs()
                        .encodeElements(
                                (long) INSUFFICIENT_ACCOUNT_BALANCE.protoOrdinal(),
                                BigInteger.valueOf(0),
                                new long[] {})),
                result.getOutput());
    }

    private NonFungibleMintCall subjectForMint() {
        return new NonFungibleMintCall(
                gasCalculator,
                mockEnhancement(),
                METADATA,
                FUNGIBLE_TOKEN_ID,
                verificationStrategy,
                FRAME_SENDER_ADDRESS,
                addressIdConverter,
                syntheticTransfer);
    }
}
