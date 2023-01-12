/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts.precompile;

import static com.hedera.node.app.service.mono.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V3;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.failResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenUpdatePrecompile.decodeUpdateTokenInfo;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenUpdatePrecompile.decodeUpdateTokenInfoV2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenUpdatePrecompile.decodeUpdateTokenInfoV3;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.TokenUpdateWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenUpdatePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenUpdatePrecompileTest {
    @Mock private HederaTokenStore hederaTokenStore;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private EvmEncodingFacade evmEncoder;
    @Mock private TokenUpdateLogic updateLogic;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nfts;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
            tokenRels;

    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock private ExpiringCreations creator;
    @Mock private FeeCalculator feeCalculator;
    @Mock private StateView stateView;
    @Mock private ContractAliases aliases;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private AssetsLoader assetLoader;
    @Mock private HbarCentExchange exchange;
    @Mock private ExchangeRate exchangeRate;
    @Mock private AccessorFactory accessorFactory;
    @Mock private EvmHTSPrecompiledContract evmHTSPrecompiledContract;
    private final TokenUpdateWrapper updateWrapper =
            HTSTestsUtil.createFungibleTokenUpdateWrapperWithKeys(null);

    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final Bytes UPDATE_FUNGIBLE_TOKEN_INPUT =
            Bytes.fromHexString(
                    "0x2cccc36f0000000000000000000000000000000000000000000000000000000000000b650000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000001a00000000000000000000000000000000000000000000000000000000000000b6100000000000000000000000000000000000000000000000000000000000001e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000022000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000b6100000000000000000000000000000000000000000000000000000000007a1200000000000000000000000000000000000000000000000000000000000000000a637573746f6d4e616d65000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002cea900000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000054f6d656761000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000500000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000001e0000000000000000000000000000000000000000000000000000000000000034000000000000000000000000000000000000000000000000000000000000004600000000000000000000000000000000000000000000000000000000000000580000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000205d2a3c5dd3e65bde502cacc8bc88a12599712d3d7f6d96aa0db12e140740a65e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000210324e3e6c5a305f98e36ee89783d1aedcf07140780b5bb16d5d2aa7911ccdf8bdf000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000b6400000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000b6400000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000b6400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes UPDATE_FUNGIBLE_TOKEN_INPUT_WITH_NO_TREASURY_SET =
            Bytes.fromHexString(
                    "0x2cccc36f00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000001a0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001e00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000220000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003ea00000000000000000000000000000000000000000000000000000000007a1200000000000000000000000000000000000000000000000000000000000000000a637573746f6d4e616d65000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002cea900000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000054f6d656761000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000500000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000001e0000000000000000000000000000000000000000000000000000000000000034000000000000000000000000000000000000000000000000000000000000004600000000000000000000000000000000000000000000000000000000000000580000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020fe23738800e207f0224ad1f4f1df64b5333bf5232cf439ecf96eb222fc4c1e400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002103a30f81e84b98f183256c015f52686d0eac88301ed253747172d06fd77be47a9b0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003ed00000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003ed00000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000003ed00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes UPDATE_FUNGIBLE_TOKEN_INPUT_V2 =
            Bytes.fromHexString(
                    "0x18370d3400000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000001a00000000000000000000000000000000000000000000000000000000000000b6100000000000000000000000000000000000000000000000000000000000001e000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000007fffffffffffffff0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000022000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000b6100000000000000000000000000000000000000000000000000000000007a1200000000000000000000000000000000000000000000000000000000000000000a637573746f6d4e616d65000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002cea900000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000054f6d656761000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000005000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes UPDATE_FUNGIBLE_TOKEN_INPUT_V3 =
            Bytes.fromHexString(
                    "0x7d305cfa00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000001a00000000000000000000000000000000000000000000000000000000000000b6100000000000000000000000000000000000000000000000000000000000001e000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000007fffffffffffffff0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000022000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000b6100000000000000000000000000000000000000000000000000000000007a1200000000000000000000000000000000000000000000000000000000000000000a637573746f6d4e616d65000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002cea900000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000054f6d656761000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000005000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    private HTSPrecompiledContract subject;
    private MockedStatic<TokenUpdatePrecompile> tokenUpdatePrecompile;

    @BeforeEach
    void setUp() {
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(
                        assetLoader,
                        exchange,
                        () -> feeCalculator,
                        resourceCosts,
                        stateView,
                        accessorFactory);
        subject =
                new HTSPrecompiledContract(
                        dynamicProperties,
                        gasCalculator,
                        recordsHistorian,
                        sigsVerifier,
                        encoder,
                        evmEncoder,
                        syntheticTxnFactory,
                        creator,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory,
                    evmHTSPrecompiledContract);

        tokenUpdatePrecompile = Mockito.mockStatic(TokenUpdatePrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        if (!tokenUpdatePrecompile.isClosed()) {
            tokenUpdatePrecompile.close();
        }
    }

    @Test
    void computeCallsSuccessfullyForUpdateFungibleToken() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_UPDATE_TOKEN_INFO));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        givenFrameContext();
        given(frame.getBlockValues())
                .willReturn(new HederaBlockValues(10L, 123L, Instant.ofEpochSecond(123L)));
        givenLedgers();
        givenMinimalContextForSuccessfulCall();
        givenMinimalRecordStructureForSuccessfulCall();
        givenUpdateTokenContext();
        givenPricingUtilsContext();
        given(updateLogic.validate(any())).willReturn(OK);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        subject.getPrecompile().getMinimumFeeInTinybars(Timestamp.getDefaultInstance());
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void computeCallsSuccessfullyForUpdateFungibleTokenV2() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_UPDATE_TOKEN_INFO_V2));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        givenFrameContext();
        given(frame.getBlockValues())
                .willReturn(new HederaBlockValues(10L, 123L, Instant.ofEpochSecond(123L)));
        givenLedgers();
        givenMinimalContextForSuccessfulCall();
        givenMinimalRecordStructureForSuccessfulCall();
        givenUpdateTokenContextV2();
        givenPricingUtilsContext();
        given(updateLogic.validate(any())).willReturn(OK);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        subject.getPrecompile().getMinimumFeeInTinybars(Timestamp.getDefaultInstance());
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void computeCallsSuccessfullyForUpdateFungibleTokenV3() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_UPDATE_TOKEN_INFO_V3));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        givenFrameContext();
        given(frame.getBlockValues())
                .willReturn(new HederaBlockValues(10L, 123L, Instant.ofEpochSecond(123L)));
        givenLedgers();
        givenMinimalContextForSuccessfulCall();
        givenMinimalRecordStructureForSuccessfulCall();
        givenUpdateTokenContextV3();
        givenPricingUtilsContext();
        given(updateLogic.validate(any())).willReturn(OK);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        subject.getPrecompile().getMinimumFeeInTinybars(Timestamp.getDefaultInstance());
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void failsWithWrongValidityForUpdateFungibleToken() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_UPDATE_TOKEN_INFO));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        givenFrameContext();
        givenLedgers();
        givenMinimalContextForSuccessfulCall();
        givenUpdateTokenContext();
        given(worldUpdater.aliases()).willReturn(aliases);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(updateLogic.validate(any())).willReturn(FAIL_INVALID);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(failResult, result);
    }

    @Test
    void failsWithWrongValidityForUpdateFungibleTokenV2() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_UPDATE_TOKEN_INFO));
        givenFrameContext();
        givenLedgers();
        givenMinimalContextForSuccessfulCall();
        givenUpdateTokenContextV2();
        given(worldUpdater.aliases()).willReturn(aliases);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(updateLogic.validate(any())).willReturn(FAIL_INVALID);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(failResult, result);
    }

    @Test
    void decodeFungibleUpdateInput() {
        tokenUpdatePrecompile.close();
        final var decodedInput = decodeUpdateTokenInfo(UPDATE_FUNGIBLE_TOKEN_INPUT, identity());

        assertExpectedFungibleTokenUpdateStruct(decodedInput);
    }

    @Test
    void decodeFungibleUpdateInputWithNoTreasurySet() {
        tokenUpdatePrecompile.close();
        final var decodedInput =
                decodeUpdateTokenInfo(UPDATE_FUNGIBLE_TOKEN_INPUT_WITH_NO_TREASURY_SET, identity());

        assertEquals("customName", decodedInput.name());
        assertEquals("Ω", decodedInput.symbol());
        assertNull(decodedInput.treasury());
        assertEquals("Omega", decodedInput.memo());
        assertEquals(8000000, decodedInput.expiry().autoRenewPeriod());
        assertEquals(
                AccountID.newBuilder().setAccountNum(1002L).build(),
                decodedInput.expiry().autoRenewAccount());
        assertEquals(0L, decodedInput.expiry().second());
    }

    @Test
    void decodeFungibleUpdateInputV2() {
        tokenUpdatePrecompile.close();
        final var decodedInput =
                decodeUpdateTokenInfoV2(UPDATE_FUNGIBLE_TOKEN_INPUT_V2, identity());

        assertExpectedFungibleTokenUpdateStruct(decodedInput);
    }

    @Test
    void decodeFungibleUpdateInputV3() {
        tokenUpdatePrecompile.close();
        final var decodedInput =
                decodeUpdateTokenInfoV3(UPDATE_FUNGIBLE_TOKEN_INPUT_V3, identity());

        assertExpectedFungibleTokenUpdateStruct(decodedInput);
    }

    private void assertExpectedFungibleTokenUpdateStruct(final TokenUpdateWrapper decodedInput) {
        assertEquals("customName", decodedInput.name());
        assertEquals("Ω", decodedInput.symbol());
        assertEquals(AccountID.newBuilder().setAccountNum(2913L).build(), decodedInput.treasury());
        assertEquals("Omega", decodedInput.memo());
        assertEquals(8000000, decodedInput.expiry().autoRenewPeriod());
        assertEquals(
                AccountID.newBuilder().setAccountNum(2913L).build(),
                decodedInput.expiry().autoRenewAccount());
        assertEquals(0L, decodedInput.expiry().second());
    }

    private void givenFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getContractAddress()).willReturn(contractAddr);
        given(frame.getRecipientAddress()).willReturn(fungibleTokenAddr);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
    }

    private void givenMinimalContextForSuccessfulCall() {
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    private void givenUpdateTokenContext() {
        given(
                        sigsVerifier.hasActiveAdminKey(
                                true, fungibleTokenAddr, fungibleTokenAddr, wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(
                        infrastructureFactory.newTokenUpdateLogic(
                                hederaTokenStore, wrappedLedgers, sideEffects))
                .willReturn(updateLogic);
        tokenUpdatePrecompile
                .when(() -> decodeUpdateTokenInfo(any(), any()))
                .thenReturn(updateWrapper);
        given(syntheticTxnFactory.createTokenUpdate(updateWrapper))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenUpdate(TokenUpdateTransactionBody.newBuilder()));
    }

    private void givenUpdateTokenContextV2() {
        given(
                        sigsVerifier.hasActiveAdminKey(
                                true, fungibleTokenAddr, fungibleTokenAddr, wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(
                        infrastructureFactory.newTokenUpdateLogic(
                                hederaTokenStore, wrappedLedgers, sideEffects))
                .willReturn(updateLogic);
        tokenUpdatePrecompile
                .when(() -> decodeUpdateTokenInfoV2(any(), any()))
                .thenReturn(updateWrapper);
        given(syntheticTxnFactory.createTokenUpdate(updateWrapper))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenUpdate(TokenUpdateTransactionBody.newBuilder()));
    }

    private void givenUpdateTokenContextV3() {
        given(
                        sigsVerifier.hasActiveAdminKey(
                                true, fungibleTokenAddr, fungibleTokenAddr, wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(
                        infrastructureFactory.newTokenUpdateLogic(
                                hederaTokenStore, wrappedLedgers, sideEffects))
                .willReturn(updateLogic);
        tokenUpdatePrecompile
                .when(() -> decodeUpdateTokenInfoV3(any(), any()))
                .thenReturn(updateWrapper);
        given(syntheticTxnFactory.createTokenUpdate(updateWrapper))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenUpdate(TokenUpdateTransactionBody.newBuilder()));
    }

    private void givenMinimalRecordStructureForSuccessfulCall() {
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
    }

    private void givenLedgers() {
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }
}
