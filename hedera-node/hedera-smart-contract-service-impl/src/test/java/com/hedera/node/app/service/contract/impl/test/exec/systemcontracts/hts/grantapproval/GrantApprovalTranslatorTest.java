/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.BurnTranslator.BURN_TOKEN_V2;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator.ERC_GRANT_APPROVAL;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator.ERC_GRANT_APPROVAL_NFT;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator.GRANT_APPROVAL;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator.GRANT_APPROVAL_NFT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.UNAUTHORIZED_SPENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHtsAttemptWithSelector;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHtsAttemptWithSelectorForRedirect;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.ClassicGrantApprovalCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.ERCGrantApprovalCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator;
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
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private VerificationStrategies verificationStrategies;

    @Mock
    private HederaNativeOperations nativeOperations;

    private final GrantApprovalDecoder decoder = new GrantApprovalDecoder();
    private GrantApprovalTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new GrantApprovalTranslator(decoder);
    }

    @Test
    void grantApprovalMatches() {
        attempt = prepareHtsAttemptWithSelector(
                GRANT_APPROVAL, subject, enhancement, addressIdConverter, verificationStrategies, gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void ERCGrantApprovalMatches() {
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        given(nativeOperations.getToken(anyLong())).willReturn(FUNGIBLE_TOKEN);
        attempt = prepareHtsAttemptWithSelectorForRedirect(
                ERC_GRANT_APPROVAL, subject, enhancement, addressIdConverter, verificationStrategies, gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void ERCGrantApprovalNFTMatches() {
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        given(nativeOperations.getToken(anyLong())).willReturn(FUNGIBLE_TOKEN);
        attempt = prepareHtsAttemptWithSelectorForRedirect(
                ERC_GRANT_APPROVAL_NFT,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void grantApprovalNFTMatches() {
        attempt = prepareHtsAttemptWithSelector(
                GRANT_APPROVAL_NFT, subject, enhancement, addressIdConverter, verificationStrategies, gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void falseOnInvalidSelector() {
        attempt = prepareHtsAttemptWithSelector(
                BURN_TOKEN_V2, subject, enhancement, addressIdConverter, verificationStrategies, gasCalculator);
        assertFalse(subject.matches(attempt));
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
        given(attempt.isSelector(GRANT_APPROVAL, GRANT_APPROVAL_NFT)).willReturn(true);
        given(attempt.isSelector(GRANT_APPROVAL)).willReturn(true);
        given(attempt.isSelector(ERC_GRANT_APPROVAL)).willReturn(false);
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

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
        given(attempt.isSelector(GRANT_APPROVAL, GRANT_APPROVAL_NFT)).willReturn(true);
        given(attempt.isSelector(GRANT_APPROVAL)).willReturn(false);
        given(attempt.isSelector(ERC_GRANT_APPROVAL)).willReturn(false);
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        final var call = subject.callFrom(attempt);
        assertInstanceOf(ClassicGrantApprovalCall.class, call);
    }

    @Test
    void callFromERCFungible() {
        final Tuple tuple = new Tuple(UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, BigInteger.valueOf(123L));
        final byte[] inputBytes =
                Bytes.wrapByteBuffer(ERC_GRANT_APPROVAL.encodeCall(tuple)).toArray();
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convert(any())).willReturn(UNAUTHORIZED_SPENDER_ID);
        given(attempt.redirectTokenId()).willReturn(FUNGIBLE_TOKEN_ID);
        given(attempt.redirectTokenType()).willReturn(TokenType.FUNGIBLE_COMMON);
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.isSelector(ERC_GRANT_APPROVAL)).willReturn(true);
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

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
        given(attempt.isSelector(ERC_GRANT_APPROVAL)).willReturn(true);
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        final var call = subject.callFrom(attempt);
        assertInstanceOf(ERCGrantApprovalCall.class, call);
    }
}
