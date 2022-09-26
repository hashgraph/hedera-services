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
package com.hedera.services.store.contracts.precompile.proxy;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_ALLOWANCE;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_DECIMALS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_GET_APPROVED;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_OWNER_OF_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_SYMBOL;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOTAL_SUPPLY_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.services.store.contracts.precompile.proxy.RedirectViewExecutor.MINIMUM_TINYBARS_COST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.BalanceOfWrapper;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.GetApprovedWrapper;
import com.hedera.services.store.contracts.precompile.codec.IsApproveForAllWrapper;
import com.hedera.services.store.contracts.precompile.codec.OwnerOfAndTokenURIWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenAllowanceWrapper;
import com.hedera.services.store.contracts.precompile.impl.AllowancePrecompile;
import com.hedera.services.store.contracts.precompile.impl.BalanceOfPrecompile;
import com.hedera.services.store.contracts.precompile.impl.GetApprovedPrecompile;
import com.hedera.services.store.contracts.precompile.impl.IsApprovedForAllPrecompile;
import com.hedera.services.store.contracts.precompile.impl.OwnerOfPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenURIPrecompile;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
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
    @Mock private EncodingFacade encodingFacade;
    @Mock private ViewGasCalculator viewGasCalculator;
    @Mock private HederaStackedWorldStateUpdater stackedWorldStateUpdater;
    @Mock private WorldLedgers worldLedgers;
    @Mock private BlockValues blockValues;
    @Mock private BalanceOfWrapper balanceOfWrapper;
    @Mock private OwnerOfAndTokenURIWrapper ownerOfAndTokenURIWrapper;

    public static final AccountID account = IdUtils.asAccount("0.0.777");
    public static final AccountID spender = IdUtils.asAccount("0.0.888");
    public static final TokenID fungible = IdUtils.asToken("0.0.888");
    public static final TokenID nonfungibletoken = IdUtils.asToken("0.0.999");
    public static final NftId nonfungible = new NftId(0, 0, 999, 1);
    public static final Id fungibleId = Id.fromGrpcToken(fungible);
    public static final Id nonfungibleId = Id.fromGrpcToken(nonfungibletoken);
    public static final Address fungibleTokenAddress = fungibleId.asEvmAddress();
    public static final Address nonfungibleTokenAddress = nonfungibleId.asEvmAddress();

    private static final long timestamp = 10L;
    private static final Timestamp resultingTimestamp =
            Timestamp.newBuilder().setSeconds(timestamp).build();
    private static final long gas = 100L;
    private static final Bytes answer = Bytes.of(1);

    RedirectViewExecutor subject;
    private MockedStatic<AllowancePrecompile> allowancePrecompile;
    private MockedStatic<GetApprovedPrecompile> getApprovedPrecompile;
    private MockedStatic<IsApprovedForAllPrecompile> isApprovedForAllPrecompile;
    private MockedStatic<BalanceOfPrecompile> balanceOfPrecompile;
    private MockedStatic<OwnerOfPrecompile> ownerOfPrecompile;
    private MockedStatic<TokenURIPrecompile> tokenURIPrecompile;

    @BeforeEach
    void setUp() {
        allowancePrecompile = Mockito.mockStatic(AllowancePrecompile.class);
        getApprovedPrecompile = Mockito.mockStatic(GetApprovedPrecompile.class);
        isApprovedForAllPrecompile = Mockito.mockStatic(IsApprovedForAllPrecompile.class);
        balanceOfPrecompile = Mockito.mockStatic(BalanceOfPrecompile.class);
        ownerOfPrecompile = Mockito.mockStatic(OwnerOfPrecompile.class);
        tokenURIPrecompile = Mockito.mockStatic(TokenURIPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        allowancePrecompile.close();
        getApprovedPrecompile.close();
        isApprovedForAllPrecompile.close();
        balanceOfPrecompile.close();
        ownerOfPrecompile.close();
        tokenURIPrecompile.close();
    }

    @Test
    void computeCostedNAME() {
        prerequisites(ABI_ID_ERC_NAME, fungibleTokenAddress);

        final var result = "name";

        given(worldLedgers.nameOf(fungible)).willReturn(result);
        given(encodingFacade.encodeName(result)).willReturn(answer);

        assertEquals(Pair.of(gas, answer), subject.computeCosted());
    }

    @Test
    void computeCostedSYMBOL() {
        prerequisites(ABI_ID_ERC_SYMBOL, fungibleTokenAddress);

        final var result = "symbol";

        given(worldLedgers.symbolOf(fungible)).willReturn(result);
        given(encodingFacade.encodeSymbol(result)).willReturn(answer);

        assertEquals(Pair.of(gas, answer), subject.computeCosted());
    }

    @Test
    void computeAllowanceOf() {
        final var nestedInput = prerequisites(ABI_ID_ERC_ALLOWANCE, fungibleTokenAddress);

        final var allowanceWrapper = new TokenAllowanceWrapper(fungible, account, spender);
        allowancePrecompile
                .when(
                        () ->
                                AllowancePrecompile.decodeTokenAllowance(
                                        eq(nestedInput), eq(fungible), any()))
                .thenReturn(allowanceWrapper);
        given(worldLedgers.staticAllowanceOf(account, spender, fungible)).willReturn(123L);
        given(encodingFacade.encodeAllowance(123L)).willReturn(answer);

        assertEquals(Pair.of(gas, answer), subject.computeCosted());
    }

    @Test
    void computeApprovedSpenderOf() {
        final var nestedInput = prerequisites(ABI_ID_ERC_GET_APPROVED, nonfungibleTokenAddress);

        final var getApprovedWrapper = new GetApprovedWrapper(nonfungibletoken, 123L);
        getApprovedPrecompile
                .when(() -> GetApprovedPrecompile.decodeGetApproved(nestedInput, nonfungibletoken))
                .thenReturn(getApprovedWrapper);
        given(worldLedgers.staticApprovedSpenderOf(NftId.fromGrpc(nonfungibletoken, 123L)))
                .willReturn(Address.ALTBN128_ADD);
        given(worldLedgers.canonicalAddress(Address.ALTBN128_ADD)).willReturn(Address.ALTBN128_ADD);
        given(encodingFacade.encodeGetApproved(Address.ALTBN128_ADD)).willReturn(answer);

        assertEquals(Pair.of(gas, answer), subject.computeCosted());
    }

    @Test
    void computeOperatorCheck() {
        final var nestedInput =
                prerequisites(ABI_ID_ERC_IS_APPROVED_FOR_ALL, nonfungibleTokenAddress);

        final var isApproveForAll = new IsApproveForAllWrapper(nonfungibletoken, account, spender);
        isApprovedForAllPrecompile
                .when(
                        () ->
                                IsApprovedForAllPrecompile.decodeIsApprovedForAll(
                                        eq(nestedInput), eq(nonfungibletoken), any()))
                .thenReturn(isApproveForAll);
        given(worldLedgers.staticIsOperator(account, spender, nonfungibletoken)).willReturn(true);
        given(encodingFacade.encodeIsApprovedForAll(true)).willReturn(answer);

        assertEquals(Pair.of(gas, answer), subject.computeCosted());
    }

    @Test
    void revertsFrameAndReturnsNullOnRevertingException() {
        final var nestedInput = prerequisites(ABI_ID_ERC_ALLOWANCE, fungibleTokenAddress);

        final var allowanceWrapper = new TokenAllowanceWrapper(fungible, account, spender);
        allowancePrecompile
                .when(
                        () ->
                                AllowancePrecompile.decodeTokenAllowance(
                                        eq(nestedInput), eq(fungible), any()))
                .thenReturn(allowanceWrapper);
        given(worldLedgers.staticAllowanceOf(account, spender, fungible))
                .willThrow(new InvalidTransactionException(INVALID_ALLOWANCE_OWNER_ID, true));

        assertEquals(Pair.of(gas, null), subject.computeCosted());
        verify(frame).setState(MessageFrame.State.REVERT);
    }

    @Test
    void computeCostedDECIMALS() {
        prerequisites(ABI_ID_ERC_DECIMALS, fungibleTokenAddress);

        final var result = 1;

        given(worldLedgers.typeOf(fungible)).willReturn(TokenType.FUNGIBLE_COMMON);
        given(worldLedgers.decimalsOf(fungible)).willReturn(result);
        given(encodingFacade.encodeDecimals(result)).willReturn(answer);

        assertEquals(Pair.of(gas, answer), subject.computeCosted());
    }

    @Test
    void computeCostedTOTAL_SUPPY_TOKEN() {
        prerequisites(ABI_ID_ERC_TOTAL_SUPPLY_TOKEN, fungibleTokenAddress);

        final var result = 1L;

        given(worldLedgers.totalSupplyOf(fungible)).willReturn(result);
        given(encodingFacade.encodeTotalSupply(result)).willReturn(answer);

        assertEquals(Pair.of(gas, answer), subject.computeCosted());
    }

    @Test
    void computeCostedBALANCE_OF_TOKEN() {
        Bytes nestedInput = prerequisites(ABI_ID_ERC_BALANCE_OF_TOKEN, fungibleTokenAddress);

        final var result = 1L;

        balanceOfPrecompile
                .when(() -> BalanceOfPrecompile.decodeBalanceOf(eq(nestedInput), any()))
                .thenReturn(balanceOfWrapper);
        given(balanceOfWrapper.accountId()).willReturn(account);
        given(worldLedgers.balanceOf(account, fungible)).willReturn(result);
        given(encodingFacade.encodeBalance(result)).willReturn(answer);

        assertEquals(Pair.of(gas, answer), subject.computeCosted());
    }

    @Test
    void computeCostedOWNER_OF_NFT() {
        Bytes nestedInput = prerequisites(ABI_ID_ERC_OWNER_OF_NFT, nonfungibleTokenAddress);

        final var result = Address.fromHexString("0x000000000000013");
        final var serialNum = 1L;

        ownerOfPrecompile
                .when(() -> OwnerOfPrecompile.decodeOwnerOf(nestedInput))
                .thenReturn(ownerOfAndTokenURIWrapper);
        given(ownerOfAndTokenURIWrapper.serialNo()).willReturn(serialNum);
        given(worldLedgers.ownerOf(nonfungible)).willReturn(result);
        given(worldLedgers.canonicalAddress(result)).willReturn(result);
        given(encodingFacade.encodeOwner(result)).willReturn(answer);

        assertEquals(Pair.of(gas, answer), subject.computeCosted());
    }

    @Test
    void computeCostedTOKEN_URI_NFT() {
        Bytes nestedInput = prerequisites(ABI_ID_ERC_TOKEN_URI_NFT, nonfungibleTokenAddress);

        final var result = "some metadata";
        final var serialNum = 1L;

        tokenURIPrecompile
                .when(() -> TokenURIPrecompile.decodeTokenUriNFT(nestedInput))
                .thenReturn(ownerOfAndTokenURIWrapper);
        given(ownerOfAndTokenURIWrapper.serialNo()).willReturn(serialNum);
        given(worldLedgers.metadataOf(nonfungible)).willReturn(result);
        given(encodingFacade.encodeTokenUri(result)).willReturn(answer);

        assertEquals(Pair.of(gas, answer), subject.computeCosted());
    }

    @Test
    void computeCostedNOT_SUPPORTED() {
        prerequisites(0xffffffff, fungibleTokenAddress);
        assertNull(subject.computeCosted().getRight());
    }

    Bytes prerequisites(final int descriptor, final Bytes tokenAddress) {
        given(frame.getWorldUpdater()).willReturn(stackedWorldStateUpdater);
        given(stackedWorldStateUpdater.trackingLedgers()).willReturn(worldLedgers);
        Bytes nestedInput = Bytes.of(Integers.toBytes(descriptor));
        Bytes input =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                        tokenAddress,
                        nestedInput);
        given(frame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(timestamp);
        given(viewGasCalculator.compute(resultingTimestamp, MINIMUM_TINYBARS_COST)).willReturn(gas);
        given(frame.getWorldUpdater()).willReturn(stackedWorldStateUpdater);
        given(stackedWorldStateUpdater.trackingLedgers()).willReturn(worldLedgers);
        this.subject = new RedirectViewExecutor(input, frame, encodingFacade, viewGasCalculator);
        return nestedInput;
    }
}
