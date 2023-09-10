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

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfCall.BALANCE_OF;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_LONG_ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DecodingStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.decimals.DecimalsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isoperator.IsApprovedForAllCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.FungibleMintCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.MintCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.NonFungibleMintCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.name.NameCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ownerof.OwnerOfCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.symbol.SymbolCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenuri.TokenUriCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.totalsupply.TotalSupplyCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc721TransferFromCall;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.swirlds.common.utility.CommonUtils;
import java.math.BigInteger;
import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;

class HtsCallAttemptTest extends HtsCallTestBase {
    @Mock
    private VerificationStrategies verificationStrategies;

    @Mock
    private DecodingStrategies decodingStrategies;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private VerificationStrategy strategy;

    @Test
    void nonLongZeroAddressesArentTokens() {
        final var input = TestHelpers.bytesForRedirect(Erc20TransfersCall.ERC_20_TRANSFER.selector(), EIP_1014_ADDRESS);
        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);
        assertNull(subject.redirectToken());
        verifyNoInteractions(nativeOperations);
    }

    @Test
    void invalidSelectorLeadsToMissingCall() {
        given(nativeOperations.getToken(numberOfLongZero(NON_SYSTEM_LONG_ZERO_ADDRESS)))
                .willReturn(FUNGIBLE_TOKEN);
        final var input = TestHelpers.bytesForRedirect(new byte[4], NON_SYSTEM_LONG_ZERO_ADDRESS);
        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);
        assertNull(subject.asCallFrom(EIP_1014_ADDRESS, false));
    }

    @Test
    void constructsDecimals() {
        final var input = TestHelpers.bytesForRedirect(
                DecimalsCall.DECIMALS.encodeCallWithArgs().array(), NON_SYSTEM_LONG_ZERO_ADDRESS);
        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);
        assertInstanceOf(DecimalsCall.class, subject.asCallFrom(EIP_1014_ADDRESS, false));
    }

    @Test
    void constructsTokenUri() {
        final var input = TestHelpers.bytesForRedirect(
                TokenUriCall.TOKEN_URI.encodeCallWithArgs(BigInteger.ONE).array(), NON_SYSTEM_LONG_ZERO_ADDRESS);
        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);
        assertInstanceOf(TokenUriCall.class, subject.asCallFrom(EIP_1014_ADDRESS, false));
    }

    @Test
    void constructsOwnerOf() {
        final var input = TestHelpers.bytesForRedirect(
                OwnerOfCall.OWNER_OF.encodeCallWithArgs(BigInteger.ONE).array(), NON_SYSTEM_LONG_ZERO_ADDRESS);
        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);
        assertInstanceOf(OwnerOfCall.class, subject.asCallFrom(EIP_1014_ADDRESS, false));
    }

    @Test
    void constructsBalanceOf() {
        final var input = TestHelpers.bytesForRedirect(
                BALANCE_OF
                        .encodeCallWithArgs(asHeadlongAddress(EIP_1014_ADDRESS))
                        .array(),
                NON_SYSTEM_LONG_ZERO_ADDRESS);
        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);
        assertInstanceOf(BalanceOfCall.class, subject.asCallFrom(EIP_1014_ADDRESS, false));
    }

    @Test
    void constructsIsOperator() {
        final var address = asHeadlongAddress(EIP_1014_ADDRESS);
        final var input = TestHelpers.bytesForRedirect(
                IsApprovedForAllCall.IS_APPROVED_FOR_ALL
                        .encodeCallWithArgs(address, address)
                        .array(),
                NON_SYSTEM_LONG_ZERO_ADDRESS);
        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);
        assertInstanceOf(IsApprovedForAllCall.class, subject.asCallFrom(EIP_1014_ADDRESS, false));
    }

    @Test
    void constructsTotalSupply() {
        final var input = TestHelpers.bytesForRedirect(
                TotalSupplyCall.TOTAL_SUPPLY.encodeCallWithArgs().array(), NON_SYSTEM_LONG_ZERO_ADDRESS);
        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);
        assertInstanceOf(TotalSupplyCall.class, subject.asCallFrom(EIP_1014_ADDRESS, false));
    }

    @Test
    void constructsName() {
        final var input =
                TestHelpers.bytesForRedirect(NameCall.NAME.encodeCallWithArgs().array(), NON_SYSTEM_LONG_ZERO_ADDRESS);
        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);
        assertInstanceOf(NameCall.class, subject.asCallFrom(EIP_1014_ADDRESS, false));
    }

    @Test
    void constructsSymbol() {
        final var input = TestHelpers.bytesForRedirect(
                SymbolCall.SYMBOL.encodeCallWithArgs().array(), NON_SYSTEM_LONG_ZERO_ADDRESS);
        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);
        assertInstanceOf(SymbolCall.class, subject.asCallFrom(EIP_1014_ADDRESS, false));
    }

    @Test
    void constructsErc721TransferFromRedirectToNonfungible() {
        given(nativeOperations.getToken(numberOfLongZero(NON_SYSTEM_LONG_ZERO_ADDRESS)))
                .willReturn(NON_FUNGIBLE_TOKEN);
        final var input = TestHelpers.bytesForRedirect(
                Erc721TransferFromCall.ERC_721_TRANSFER_FROM
                        .encodeCallWithArgs(
                                asHeadlongAddress(EIP_1014_ADDRESS),
                                asHeadlongAddress(NON_SYSTEM_LONG_ZERO_ADDRESS),
                                BigInteger.ONE)
                        .array(),
                NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(verificationStrategies.activatingContractKeysFor(EIP_1014_ADDRESS, true, nativeOperations))
                .willReturn(strategy);
        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);
        assertInstanceOf(Erc721TransferFromCall.class, subject.asCallFrom(EIP_1014_ADDRESS, true));
    }

    @Test
    void constructsErc20TransferFromRedirectToFungible() {
        given(nativeOperations.getToken(numberOfLongZero(NON_SYSTEM_LONG_ZERO_ADDRESS)))
                .willReturn(FUNGIBLE_TOKEN);
        final var input = TestHelpers.bytesForRedirect(
                Erc20TransfersCall.ERC_20_TRANSFER_FROM
                        .encodeCallWithArgs(
                                asHeadlongAddress(EIP_1014_ADDRESS),
                                asHeadlongAddress(NON_SYSTEM_LONG_ZERO_ADDRESS),
                                BigInteger.TWO)
                        .array(),
                NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(verificationStrategies.activatingContractKeysFor(EIP_1014_ADDRESS, true, nativeOperations))
                .willReturn(strategy);
        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);
        assertInstanceOf(Erc20TransfersCall.class, subject.asCallFrom(EIP_1014_ADDRESS, true));
    }

    @Test
    void constructsErc20TransferRedirectToFungible() {
        given(nativeOperations.getToken(numberOfLongZero(NON_SYSTEM_LONG_ZERO_ADDRESS)))
                .willReturn(FUNGIBLE_TOKEN);
        final var input = TestHelpers.bytesForRedirect(
                Erc20TransfersCall.ERC_20_TRANSFER
                        .encodeCallWithArgs(asHeadlongAddress(NON_SYSTEM_LONG_ZERO_ADDRESS), BigInteger.TWO)
                        .array(),
                NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(verificationStrategies.activatingContractKeysFor(EIP_1014_ADDRESS, true, nativeOperations))
                .willReturn(strategy);
        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);
        assertInstanceOf(Erc20TransfersCall.class, subject.asCallFrom(EIP_1014_ADDRESS, true));
    }

    @ParameterizedTest
    @CsvSource({
        "false,false,0x189a554c",
        "false,false,0x0e71804f",
        "false,false,0xeca36917",
        "false,false,0x82bba493",
        "false,false,0x5cfc9011",
        "false,false,0x2c4ba191",
        "false,false,0x15dacbea",
        "false,false,0x9b23d3d9",
    })
    void constructsClassicTransfers(boolean useExplicitCall, boolean isRedirect, String hexedSelector) {
        final var selector = CommonUtils.unhex(hexedSelector.substring(2));
        final var selectorHex = hexedSelector.substring(2);
        // Even the approval-based transfers need a verification strategy since the receiver could have
        // receiverSigRequired on; in which case the sender will need to activate a contract id key
        given(verificationStrategies.activatingContractKeysFor(EIP_1014_ADDRESS, true, nativeOperations))
                .willReturn(strategy);
        if (ClassicTransfersCall.CRYPTO_TRANSFER.selectorHex().equals(selectorHex)) {
            given(decodingStrategies.decodeCryptoTransfer(any(), any())).willReturn(TransactionBody.DEFAULT);
        } else if (ClassicTransfersCall.CRYPTO_TRANSFER_V2.selectorHex().equals(selectorHex)) {
            given(decodingStrategies.decodeCryptoTransferV2(any(), any())).willReturn(TransactionBody.DEFAULT);
        } else if (ClassicTransfersCall.TRANSFER_TOKENS.selectorHex().equals(selectorHex)) {
            given(decodingStrategies.decodeTransferTokens(any(), any())).willReturn(TransactionBody.DEFAULT);
        } else if (ClassicTransfersCall.TRANSFER_TOKEN.selectorHex().equals(selectorHex)) {
            given(decodingStrategies.decodeTransferToken(any(), any())).willReturn(TransactionBody.DEFAULT);
        } else if (ClassicTransfersCall.TRANSFER_NFTS.selectorHex().equals(selectorHex)) {
            given(decodingStrategies.decodeTransferNfts(any(), any())).willReturn(TransactionBody.DEFAULT);
        } else if (ClassicTransfersCall.TRANSFER_NFT.selectorHex().equals(selectorHex)) {
            given(decodingStrategies.decodeTransferNft(any(), any())).willReturn(TransactionBody.DEFAULT);
        } else if (ClassicTransfersCall.HRC_TRANSFER_FROM.selectorHex().equals(selectorHex)) {
            given(decodingStrategies.decodeHrcTransferFrom(any(), any())).willReturn(TransactionBody.DEFAULT);
        } else if (ClassicTransfersCall.HRC_TRANSFER_NFT_FROM.selectorHex().equals(selectorHex)) {
            given(decodingStrategies.decodeHrcTransferNftFrom(any(), any())).willReturn(TransactionBody.DEFAULT);
        }
        final var input = encodeInput(useExplicitCall, isRedirect, selector);
        if (isRedirect) {
            given(nativeOperations.getToken(numberOfLongZero(NON_SYSTEM_LONG_ZERO_ADDRESS)))
                    .willReturn(FUNGIBLE_TOKEN);
        }

        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);

        assertInstanceOf(ClassicTransfersCall.class, subject.asCallFrom(EIP_1014_ADDRESS, true));
        assertArrayEquals(selector, subject.selector());
        assertEquals(isRedirect, subject.isTokenRedirect());
        if (isRedirect) {
            assertEquals(FUNGIBLE_TOKEN, subject.redirectToken());
            assertArrayEquals(selector, subject.input().slice(0, 4).toArrayUnsafe());
        } else {
            assertThrows(IllegalStateException.class, subject::redirectToken);
        }
    }

    enum LinkedTokenType {
        MISSING,
        NON_FUNGIBLE,
        FUNGIBLE
    }

    @ParameterizedTest
    @CsvSource({
        "0x278e0b88,MISSING",
        "0x278e0b88,FUNGIBLE",
        "0x278e0b88,NON_FUNGIBLE",
        "0xe0f4059a,MISSING",
        "0xe0f4059a,FUNGIBLE",
        "0xe0f4059a,NON_FUNGIBLE",
    })
    void constructsMints(String hexedSelector, LinkedTokenType linkedTokenType) {
        final var selector = CommonUtils.unhex(hexedSelector.substring(2));
        final var useV2 = Arrays.equals(MintCall.MINT_V2.selector(), selector);
        final Bytes input;
        if (linkedTokenType == LinkedTokenType.FUNGIBLE) {
            given(nativeOperations.getToken(numberOfLongZero(NON_SYSTEM_LONG_ZERO_ADDRESS)))
                    .willReturn(FUNGIBLE_TOKEN);
            input = useV2
                    ? Bytes.wrap(FungibleMintCall.MINT_V2
                            .encodeCallWithArgs(asHeadlongAddress(NON_SYSTEM_LONG_ZERO_ADDRESS), 1L, new byte[0][])
                            .array())
                    : Bytes.wrap(FungibleMintCall.MINT
                            .encodeCallWithArgs(
                                    asHeadlongAddress(NON_SYSTEM_LONG_ZERO_ADDRESS), BigInteger.ONE, new byte[0][])
                            .array());
        } else {
            if (linkedTokenType == LinkedTokenType.NON_FUNGIBLE) {
                given(nativeOperations.getToken(numberOfLongZero(NON_SYSTEM_LONG_ZERO_ADDRESS)))
                        .willReturn(NON_FUNGIBLE_TOKEN);
            }
            input = useV2
                    ? Bytes.wrap(FungibleMintCall.MINT_V2
                            .encodeCallWithArgs(
                                    asHeadlongAddress(NON_SYSTEM_LONG_ZERO_ADDRESS), 0L, new byte[][] {new byte[0]})
                            .array())
                    : Bytes.wrap(FungibleMintCall.MINT
                            .encodeCallWithArgs(
                                    asHeadlongAddress(NON_SYSTEM_LONG_ZERO_ADDRESS),
                                    BigInteger.ZERO,
                                    new byte[][] {new byte[0]})
                            .array());
        }

        final var subject = new HtsCallAttempt(
                input,
                mockEnhancement(),
                DEFAULT_CONFIG,
                decodingStrategies,
                addressIdConverter,
                verificationStrategies);

        if (linkedTokenType == LinkedTokenType.MISSING) {
            assertNull(subject.asCallFrom(EIP_1014_ADDRESS, false));
        } else if (linkedTokenType == LinkedTokenType.FUNGIBLE) {
            assertInstanceOf(FungibleMintCall.class, subject.asCallFrom(EIP_1014_ADDRESS, false));
        } else {
            assertInstanceOf(NonFungibleMintCall.class, subject.asCallFrom(EIP_1014_ADDRESS, false));
        }
        assertArrayEquals(selector, subject.selector());
        assertFalse(subject.isTokenRedirect());
    }

    private Bytes encodeInput(final boolean useExplicitCall, final boolean isRedirect, final byte[] selector) {
        if (isRedirect) {
            return useExplicitCall
                    ? Bytes.wrap(HtsCallAttempt.REDIRECT_FOR_TOKEN
                            .encodeCallWithArgs(
                                    asHeadlongAddress(NON_SYSTEM_LONG_ZERO_ADDRESS.toArrayUnsafe()),
                                    Bytes.wrap(selector).toArrayUnsafe())
                            .array())
                    : bytesForRedirect(selector);
        } else {
            return Bytes.wrap(selector);
        }
    }

    private Bytes bytesForRedirect(final byte[] subSelector) {
        return TestHelpers.bytesForRedirect(subSelector, NON_SYSTEM_LONG_ZERO_ADDRESS);
    }
}
