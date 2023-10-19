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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.create;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asBytesResult;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.ClassicCreatesCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class ClassicCreatesCallTest extends HtsCallTestBase {
    private static final org.hyperledger.besu.datatypes.Address FRAME_SENDER_ADDRESS = EIP_1014_ADDRESS;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private CryptoCreateRecordBuilder recordBuilder;

    private static final TransactionBody PRETEND_CREATE_TOKEN = TransactionBody.newBuilder()
            .tokenCreation(TokenCreateTransactionBody.DEFAULT)
            .build();

    private ClassicCreatesCall subject;

    @Test
    void createFungibleTokenHappyPathV1() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()))),
                result.getOutput());
    }

    @Test
    void createFungibleTokenHappyPathV2() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V2
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()))),
                result.getOutput());
    }

    @Test
    void createFungibleTokenHappyPathV3() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V3
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()))),
                result.getOutput());
    }

    @Test
    void createFungibleTokenWithCustomFeesHappyPathV1() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()))),
                result.getOutput());
    }

    @Test
    void createFungibleTokenWithCustomFeesHappyPathV2() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()))),
                result.getOutput());
    }

    @Test
    void createFungibleTokenWithCustomFeesHappyPathV3() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()))),
                result.getOutput());
    }

    @Test
    void createNonFungibleTokenHappyPathV1() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()))),
                result.getOutput());
    }

    @Test
    void createNonFungibleTokenHappyPathV2() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V2
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()))),
                result.getOutput());
    }

    @Test
    void createNonFungibleTokenHappyPathV3() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V3
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()))),
                result.getOutput());
    }

    @Test
    void createNonFungibleTokenWithCustomFeesHappyPathV1() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()))),
                result.getOutput());
    }

    @Test
    void createNonFungibleTokenWithCustomFeesHappyPathV2() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()))),
                result.getOutput());
    }

    @Test
    void createNonFungibleTokenWithCustomFeesHappyPathV3() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()))),
                result.getOutput());
    }

    @Test
    void createFungibleTokenUnhappyPathRevertsWithReason() {
        commonGivens();
        given(recordBuilder.status()).willReturn(TOKEN_HAS_NO_SUPPLY_KEY);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(Bytes.wrap(TOKEN_HAS_NO_SUPPLY_KEY.protoName().getBytes()), result.getOutput());
    }

    private void commonGivens() {
        subject = new ClassicCreatesCall(
                mockEnhancement(),
                PRETEND_CREATE_TOKEN,
                verificationStrategy,
                FRAME_SENDER_ADDRESS,
                addressIdConverter);

        given(addressIdConverter.convert(asHeadlongAddress(FRAME_SENDER_ADDRESS)))
                .willReturn(A_NEW_ACCOUNT_ID);
        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(A_NEW_ACCOUNT_ID),
                        eq(CryptoCreateRecordBuilder.class)))
                .willReturn(recordBuilder);
    }
}
