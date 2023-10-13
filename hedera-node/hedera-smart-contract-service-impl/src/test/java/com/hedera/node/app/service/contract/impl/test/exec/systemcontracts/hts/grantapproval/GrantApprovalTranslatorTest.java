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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.grantapproval;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.UNAUTHORIZED_SPENDER_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.ClassicGrantApprovalCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.ERCGrantApprovalCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersTranslator;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrantApprovalTranslatorTest {

    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    @Mock
    private VerificationStrategy verificationStrategy;

    private final GrantApprovalDecoder decoder = new GrantApprovalDecoder();
    private GrantApprovalTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new GrantApprovalTranslator(decoder);
    }

    @Test
    void grantApprovalMatches() {
        given(attempt.selector()).willReturn(GrantApprovalTranslator.GRANT_APPROVAL.selector());
        final var matches = subject.matches(attempt);
        assertTrue(matches);
    }

    @Test
    void ERCGrantApprovalMatches() {
        given(attempt.selector()).willReturn(GrantApprovalTranslator.ERC_GRANT_APPROVAL.selector());
        final var matches = subject.matches(attempt);
        assertTrue(matches);
    }

    @Test
    void ERCGrantApprovalNFTMatches() {
        given(attempt.selector()).willReturn(GrantApprovalTranslator.ERC_GRANT_APPROVAL_NFT.selector());
        final var matches = subject.matches(attempt);
        assertTrue(matches);
    }

    @Test
    void grantApprovalNFTMatches() {
        given(attempt.selector()).willReturn(GrantApprovalTranslator.GRANT_APPROVAL_NFT.selector());
        final var matches = subject.matches(attempt);
        assertTrue(matches);
    }

    @Test
    void falseOnInvalidSelector() {
        given(attempt.selector()).willReturn(ClassicTransfersTranslator.CRYPTO_TRANSFER.selector());
        final var matches = subject.matches(attempt);
        assertFalse(matches);
    }

    @Test
    void callFromHapiFungible() {
        final Tuple tuple = new Tuple(
                FUNGIBLE_TOKEN_HEADLONG_ADDRESS, UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, BigInteger.valueOf(123L));
        final byte[] inputBytes = Bytes.wrapByteBuffer(GrantApprovalTranslator.GRANT_APPROVAL.encodeCall(tuple))
                .toArray();
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convert(any())).willReturn(UNAUTHORIZED_SPENDER_ID);
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.selector()).willReturn(GrantApprovalTranslator.GRANT_APPROVAL.selector());
        given(attempt.inputBytes()).willReturn(inputBytes);

        final var call = subject.callFrom(attempt);
        assertInstanceOf(ClassicGrantApprovalCall.class, call);
    }

    @Test
    void callFromHapiNonFungible() {
        final Tuple tuple = new Tuple(
                NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, BigInteger.valueOf(123L));
        final byte[] inputBytes = Bytes.wrapByteBuffer(GrantApprovalTranslator.GRANT_APPROVAL_NFT.encodeCall(tuple))
                .toArray();
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convert(any())).willReturn(UNAUTHORIZED_SPENDER_ID);
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.selector()).willReturn(GrantApprovalTranslator.GRANT_APPROVAL_NFT.selector());
        given(attempt.inputBytes()).willReturn(inputBytes);

        final var call = subject.callFrom(attempt);
        assertInstanceOf(ClassicGrantApprovalCall.class, call);
    }

    @Test
    void callFromERCFungible() {
        final Tuple tuple = new Tuple(UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, BigInteger.valueOf(123L));
        final byte[] inputBytes = Bytes.wrapByteBuffer(GrantApprovalTranslator.ERC_GRANT_APPROVAL.encodeCall(tuple))
                .toArray();
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convert(any())).willReturn(UNAUTHORIZED_SPENDER_ID);
        given(attempt.redirectTokenId()).willReturn(FUNGIBLE_TOKEN_ID);
        given(attempt.redirectTokenType()).willReturn(TokenType.FUNGIBLE_COMMON);
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.selector()).willReturn(GrantApprovalTranslator.ERC_GRANT_APPROVAL.selector());
        given(attempt.inputBytes()).willReturn(inputBytes);

        final var call = subject.callFrom(attempt);
        assertInstanceOf(ERCGrantApprovalCall.class, call);
    }

    @Test
    void callFromERCNonFungible() {
        final Tuple tuple = new Tuple(UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, BigInteger.valueOf(123L));
        final byte[] inputBytes = Bytes.wrapByteBuffer(GrantApprovalTranslator.ERC_GRANT_APPROVAL_NFT.encodeCall(tuple))
                .toArray();
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convert(any())).willReturn(UNAUTHORIZED_SPENDER_ID);
        given(attempt.redirectTokenId()).willReturn(NON_FUNGIBLE_TOKEN_ID);
        given(attempt.redirectTokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.selector()).willReturn(GrantApprovalTranslator.ERC_GRANT_APPROVAL_NFT.selector());
        given(attempt.inputBytes()).willReturn(inputBytes);

        final var call = subject.callFrom(attempt);
        assertInstanceOf(ERCGrantApprovalCall.class, call);
    }
}
