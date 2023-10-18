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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.burn;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.BurnTranslator.BURN_TOKEN_V1;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.BurnTranslator.BURN_TOKEN_V2;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asBytesResult;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.FungibleBurnCall;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import com.hedera.node.app.service.token.records.TokenBurnRecordBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class NonFungibleBurnCallTest extends HtsCallTestBase {
    private static final long NEW_TOTAL_SUPPLY = 666L;

    private static final org.hyperledger.besu.datatypes.Address FRAME_SENDER_ADDRESS = EIP_1014_ADDRESS;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private TokenBurnRecordBuilder recordBuilder;

    private FungibleBurnCall subject;

    @Test
    void happyPathV1() {
        givensForSuccessfulNFTBurnV1AndV2();
        given(recordBuilder.status()).willReturn(ResponseCodeEnum.SUCCESS);
        given(recordBuilder.getNewTotalSupply()).willReturn(NEW_TOTAL_SUPPLY);

        subject = subjectForNFTBurn(1L);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(BURN_TOKEN_V1
                        .getOutputs()
                        .encodeElements((long) ResponseCodeEnum.SUCCESS.protoOrdinal(), NEW_TOTAL_SUPPLY)),
                result.getOutput());
    }

    @Test
    void happyPathV2() {
        givensForSuccessfulNFTBurnV1AndV2();
        given(recordBuilder.status()).willReturn(ResponseCodeEnum.SUCCESS);
        given(recordBuilder.getNewTotalSupply()).willReturn(NEW_TOTAL_SUPPLY);

        subject = subjectForNFTBurn(1L);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(BURN_TOKEN_V2
                        .getOutputs()
                        .encodeElements((long) ResponseCodeEnum.SUCCESS.protoOrdinal(), NEW_TOTAL_SUPPLY)),
                result.getOutput());
    }

    @Test
    void unhappyPathRevertsWithReason() {
        givensForSuccessfulNFTBurnV1AndV2();

        given(recordBuilder.status()).willReturn(INVALID_TOKEN_BURN_AMOUNT);

        subject = subjectForNFTBurn(1L);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(Bytes.wrap(INVALID_TOKEN_BURN_AMOUNT.protoName().getBytes()), result.getOutput());
    }

    private void givensForSuccessfulNFTBurnV1AndV2() {
        given(addressIdConverter.convert(asHeadlongAddress(FRAME_SENDER_ADDRESS)))
                .willReturn(A_NEW_ACCOUNT_ID);
        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(A_NEW_ACCOUNT_ID),
                        eq(TokenBurnRecordBuilder.class)))
                .willReturn(recordBuilder);
    }

    private FungibleBurnCall subjectForNFTBurn(final long serialNo) {
        return new FungibleBurnCall(
                serialNo,
                gasCalculator,
                mockEnhancement(),
                NON_FUNGIBLE_TOKEN_ID,
                verificationStrategy,
                SENDER_ID,
                FRAME_SENDER_ADDRESS,
                addressIdConverter);
    }
}
