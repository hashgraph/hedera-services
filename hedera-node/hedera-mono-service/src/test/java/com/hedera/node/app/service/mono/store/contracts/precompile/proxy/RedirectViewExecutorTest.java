/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts.precompile.proxy;

import static com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectViewExecutor.MINIMUM_TINYBARS_COST;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_ALLOWANCE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_DECIMALS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_GET_APPROVED;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_OWNER_OF_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_SYMBOL;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOTAL_SUPPLY_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.BalanceOfWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetApprovedWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.IsApproveForAllWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.OwnerOfAndTokenURIWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenAllowanceWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmAllowancePrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmBalanceOfPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetApprovedPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmIsApprovedForAllPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmOwnerOfPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmTokenURIPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.AllowancePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.BalanceOfPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.IsApprovedForAllPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.OwnerOfPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenURIPrecompile;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedirectViewExecutorTest {
    @Mock private MessageFrame frame;
    @Mock private EvmEncodingFacade evmEncodingFacade;
    @Mock private ViewGasCalculator viewGasCalculator;
    @Mock private HederaStackedWorldStateUpdater stackedWorldStateUpdater;
    @Mock private WorldLedgers worldLedgers;
    @Mock private BlockValues blockValues;
    @Mock private BalanceOfWrapper<byte[]> balanceOfWrapper;
    @Mock private OwnerOfAndTokenURIWrapper ownerOfAndTokenURIWrapper;
    @Mock private TokenAccessor tokenAccessor;

    public static final AccountID account = IdUtils.asAccount("0.0.777");
    public static final AccountID spender = IdUtils.asAccount("0.0.888");
    public static final TokenID fungible = IdUtils.asToken("0.0.888");
    public static final TokenID nonfungibletoken = IdUtils.asToken("0.0.999");
    public static final NftId nonfungible = new NftId(0, 0, 999, 1);
    public static final Id fungibleId = Id.fromGrpcToken(fungible);
    public static final Id nonfungibleId = Id.fromGrpcToken(nonfungibletoken);
    public static final Id accountId = Id.fromGrpcAccount(account);
    public static final Id spenderId = Id.fromGrpcAccount(spender);
    public static final Address fungibleTokenAddress = fungibleId.asEvmAddress();
    public static final Address nonfungibleTokenAddress = nonfungibleId.asEvmAddress();
    public static final Address accountAddress = accountId.asEvmAddress();
    public static final Address spenderAddress = spenderId.asEvmAddress();

    private static final long timestamp = 10L;
    private static final Timestamp resultingTimestamp =
            Timestamp.newBuilder().setSeconds(timestamp).build();
    private static final long gas = 100L;
    private static final Bytes answer = Bytes.of(1);

    RedirectViewExecutor subject;
    private MockedStatic<AllowancePrecompile> allowancePrecompile;
    private MockedStatic<IsApprovedForAllPrecompile> isApprovedForAllPrecompile;
    private MockedStatic<BalanceOfPrecompile> balanceOfPrecompile;
    private MockedStatic<OwnerOfPrecompile> ownerOfPrecompile;
    private MockedStatic<TokenURIPrecompile> tokenURIPrecompile;

    @BeforeEach
    void setUp() {
        allowancePrecompile = Mockito.mockStatic(AllowancePrecompile.class);
        isApprovedForAllPrecompile = Mockito.mockStatic(IsApprovedForAllPrecompile.class);
        balanceOfPrecompile = Mockito.mockStatic(BalanceOfPrecompile.class);
        ownerOfPrecompile = Mockito.mockStatic(OwnerOfPrecompile.class);
        tokenURIPrecompile = Mockito.mockStatic(TokenURIPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        allowancePrecompile.close();
        isApprovedForAllPrecompile.close();
        balanceOfPrecompile.close();
        ownerOfPrecompile.close();
        tokenURIPrecompile.close();
    }

    @Test
    void computeCostedNAME() {
        prerequisites(ABI_ID_ERC_NAME, fungibleTokenAddress);

        final var result = "name";

        given(tokenAccessor.nameOf(fungibleTokenAddress)).willReturn(result);
        given(evmEncodingFacade.encodeName(result)).willReturn(answer);

        assertEquals(Pair.of(gas, answer), subject.computeCosted());
    }

    @Test
    void computeCostedSYMBOL() {
        prerequisites(ABI_ID_ERC_SYMBOL, fungibleTokenAddress);

        final var result = "symbol";

        given(tokenAccessor.symbolOf(fungibleTokenAddress)).willReturn(result);
        given(evmEncodingFacade.encodeSymbol(result)).willReturn(answer);

        assertEquals(Pair.of(gas, answer), subject.computeCosted());
    }

    @Test
    void computeAllowanceOf() {
        prerequisites(ABI_ID_ERC_ALLOWANCE, fungibleTokenAddress);

        try (MockedStatic<EvmAllowancePrecompile> utilities =
                Mockito.mockStatic(EvmAllowancePrecompile.class)) {
            final var allowanceWrapper =
                    new TokenAllowanceWrapper<>(
                            fungibleTokenAddress.toArrayUnsafe(),
                            accountAddress.toArrayUnsafe(),
                            spenderAddress.toArrayUnsafe());
            utilities
                    .when(() -> EvmAllowancePrecompile.decodeTokenAllowance(any()))
                    .thenReturn(allowanceWrapper);
            given(tokenAccessor.staticAllowanceOf(any(), any(), any())).willReturn(123L);
            given(evmEncodingFacade.encodeAllowance(123L)).willReturn(answer);

            assertEquals(Pair.of(gas, answer), subject.computeCosted());
        }
    }

    @Test
    void computeApprovedSpenderOf() {
        prerequisites(ABI_ID_ERC_GET_APPROVED, nonfungibleTokenAddress);

        try (MockedStatic<EvmGetApprovedPrecompile> utilities =
                Mockito.mockStatic(EvmGetApprovedPrecompile.class)) {
            final var getApprovedWrapper =
                    new GetApprovedWrapper<>(nonfungibleTokenAddress.toArrayUnsafe(), 123L);
            utilities
                    .when(() -> EvmGetApprovedPrecompile.decodeGetApproved(any()))
                    .thenReturn(getApprovedWrapper);
            given(evmEncodingFacade.encodeGetApproved(any())).willReturn(answer);
            given(
                            tokenAccessor.staticApprovedSpenderOf(
                                    nonfungibleTokenAddress, getApprovedWrapper.serialNo()))
                    .willReturn(Address.ALTBN128_ADD);

            assertEquals(Pair.of(gas, answer), subject.computeCosted());
        }
    }

    @Test
    void computeOperatorCheck() {
        prerequisites(ABI_ID_ERC_IS_APPROVED_FOR_ALL, nonfungibleTokenAddress);

        try (MockedStatic<EvmIsApprovedForAllPrecompile> utilities =
                Mockito.mockStatic(EvmIsApprovedForAllPrecompile.class)) {
            final var isApproveForAll =
                    new IsApproveForAllWrapper<>(
                            nonfungibleTokenAddress.toArrayUnsafe(),
                            accountAddress.toArrayUnsafe(),
                            spenderAddress.toArrayUnsafe());
            utilities
                    .when(() -> EvmIsApprovedForAllPrecompile.decodeIsApprovedForAll(any()))
                    .thenReturn(isApproveForAll);

            given(tokenAccessor.staticIsOperator(any(), any(), any())).willReturn(true);
            given(evmEncodingFacade.encodeIsApprovedForAll(true)).willReturn(answer);

            assertEquals(Pair.of(gas, answer), subject.computeCosted());
        }
    }

    @Test
    void revertsFrameAndReturnsNullOnRevertingException() {
        prerequisites(ABI_ID_ERC_ALLOWANCE, fungibleTokenAddress);

        try (MockedStatic<EvmAllowancePrecompile> utilities =
                Mockito.mockStatic(EvmAllowancePrecompile.class)) {
            final var allowanceWrapper =
                    new TokenAllowanceWrapper<>(
                            fungibleTokenAddress.toArrayUnsafe(),
                            accountAddress.toArrayUnsafe(),
                            spenderAddress.toArrayUnsafe());
            utilities
                    .when(() -> EvmAllowancePrecompile.decodeTokenAllowance(any()))
                    .thenReturn(allowanceWrapper);
            given(tokenAccessor.staticAllowanceOf(any(), any(), any()))
                    .willThrow(new InvalidTransactionException(INVALID_ALLOWANCE_OWNER_ID, true));

            assertEquals(Pair.of(gas, null), subject.computeCosted());
            verify(frame).setState(MessageFrame.State.REVERT);
        }
    }

    @Test
    void computeCostedDECIMALS() {
        prerequisites(ABI_ID_ERC_DECIMALS, fungibleTokenAddress);

        final var result = 1;

        given(tokenAccessor.typeOf(fungibleTokenAddress)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(tokenAccessor.decimalsOf(fungibleTokenAddress)).willReturn(result);
        given(evmEncodingFacade.encodeDecimals(result)).willReturn(answer);

        assertEquals(Pair.of(gas, answer), subject.computeCosted());
    }

    @Test
    void computeCostedTOTAL_SUPPY_TOKEN() {
        prerequisites(ABI_ID_ERC_TOTAL_SUPPLY_TOKEN, fungibleTokenAddress);

        final var result = 1L;

        given(tokenAccessor.totalSupplyOf(fungibleTokenAddress)).willReturn(result);
        given(evmEncodingFacade.encodeTotalSupply(result)).willReturn(answer);

        assertEquals(Pair.of(gas, answer), subject.computeCosted());
    }

    @Test
    void computeCostedBALANCE_OF_TOKEN() {
        Bytes nestedInput = prerequisites(ABI_ID_ERC_BALANCE_OF_TOKEN, fungibleTokenAddress);

        final var result = 1L;

        try (MockedStatic<EvmBalanceOfPrecompile> utilities =
                Mockito.mockStatic(EvmBalanceOfPrecompile.class)) {
            utilities
                    .when(() -> EvmBalanceOfPrecompile.decodeBalanceOf(nestedInput))
                    .thenReturn(balanceOfWrapper);
            given(balanceOfWrapper.account()).willReturn(accountAddress.toArray());
            given(tokenAccessor.balanceOf(any(), any())).willReturn(result);
            given(evmEncodingFacade.encodeBalance(result)).willReturn(answer);

            assertEquals(Pair.of(gas, answer), subject.computeCosted());
        }
    }

    @Test
    void computeCostedOWNER_OF_NFT() {
        Bytes nestedInput = prerequisites(ABI_ID_ERC_OWNER_OF_NFT, nonfungibleTokenAddress);

        final var result = Address.fromHexString("0x000000000000013");
        final var serialNum = 1L;

        try (MockedStatic<EvmOwnerOfPrecompile> utilities =
                Mockito.mockStatic(EvmOwnerOfPrecompile.class)) {
            utilities
                    .when(() -> EvmOwnerOfPrecompile.decodeOwnerOf(nestedInput))
                    .thenReturn(ownerOfAndTokenURIWrapper);
            given(ownerOfAndTokenURIWrapper.serialNo()).willReturn(serialNum);
            given(tokenAccessor.ownerOf(nonfungibleTokenAddress, serialNum)).willReturn(result);
            given(tokenAccessor.canonicalAddress(result)).willReturn(result);
            given(evmEncodingFacade.encodeOwner(result)).willReturn(answer);

            assertEquals(Pair.of(gas, answer), subject.computeCosted());
        }
    }

    @Test
    void computeCostedTOKEN_URI_NFT() {
        Bytes nestedInput = prerequisites(ABI_ID_ERC_TOKEN_URI_NFT, nonfungibleTokenAddress);
        final var result = "some metadata";
        final var serialNum = 1L;

        try (MockedStatic<EvmTokenURIPrecompile> utilities =
                Mockito.mockStatic(EvmTokenURIPrecompile.class)) {
            utilities
                    .when(() -> EvmTokenURIPrecompile.decodeTokenUriNFT(nestedInput))
                    .thenReturn(ownerOfAndTokenURIWrapper);
            given(ownerOfAndTokenURIWrapper.serialNo()).willReturn(serialNum);
            given(evmEncodingFacade.encodeTokenUri(result)).willReturn(answer);
            given(
                            tokenAccessor.metadataOf(
                                    nonfungibleTokenAddress, ownerOfAndTokenURIWrapper.serialNo()))
                    .willReturn(result);

            assertEquals(Pair.of(gas, answer), subject.computeCosted());
        }
    }

    @Test
    void computeCostedNOT_SUPPORTED() {
        prerequisites(0xffffffff, fungibleTokenAddress);
        assertNull(subject.computeCosted().getRight());
    }

    Bytes prerequisites(final int descriptor, final Bytes tokenAddress) {
        Bytes nestedInput = Bytes.of(Integers.toBytes(descriptor));
        Bytes input =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                        tokenAddress,
                        nestedInput);
        given(frame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(timestamp);
        given(viewGasCalculator.compute(resultingTimestamp, MINIMUM_TINYBARS_COST)).willReturn(gas);
        this.subject =
                new RedirectViewExecutor(
                        input, frame, evmEncodingFacade, viewGasCalculator, tokenAccessor);
        return nestedInput;
    }
}
