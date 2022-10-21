/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_KEYS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.failResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungible;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.impl.TokenUpdateKeysPrecompile.decodeUpdateTokenKeys;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.HederaBlockValues;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateKeysWrapper;
import com.hedera.services.store.contracts.precompile.impl.TokenUpdateKeysPrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompileUtils;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
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
class TokenUpdateKeysPrecompileTest {
    @Mock private HederaTokenStore hederaTokenStore;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private TokenUpdateLogic updateLogic;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nfts;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
            tokenRels;

    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock private ExpiringCreations creator;
    @Mock private ImpliedTransfersMarshal impliedTransfersMarshal;
    @Mock private FeeCalculator feeCalculator;
    @Mock private StateView stateView;
    @Mock private ContractAliases aliases;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private AssetsLoader assetLoader;
    @Mock private HbarCentExchange exchange;
    @Mock private ExchangeRate exchangeRate;
    @Mock private AccessorFactory accessorFactory;
    private final TokenUpdateKeysWrapper updateWrapper = getUpdateWrapper();
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final Bytes UPDATE_FUNGIBLE_TOKEN_KEYS =
            Bytes.fromHexString(
                    "0x6fc3cbaf00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000500000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000001e0000000000000000000000000000000000000000000000000000000000000034000000000000000000000000000000000000000000000000000000000000004600000000000000000000000000000000000000000000000000000000000000580000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000201aeca66efce3b1c581d865197a41880b6c05c3115cfeac97f2832c2198f49f570000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000021031929ec5ff0aeef191aff1a4f0775470da849d92fc5eaed6e22b4c829ca5e99b400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000106400000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000106400000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000106400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    private HTSPrecompiledContract subject;
    private MockedStatic<TokenUpdateKeysPrecompile> tokenUpdateKeysPrecompile;

    @BeforeEach
    void setUp() {
        PrecompilePricingUtils precompilePricingUtils =
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
                        syntheticTxnFactory,
                        creator,
                        impliedTransfersMarshal,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory);
        tokenUpdateKeysPrecompile = Mockito.mockStatic(TokenUpdateKeysPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        tokenUpdateKeysPrecompile.close();
    }

    @Test
    void computeCallsSuccessfullyUpdateKeysForFungibleToken() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_UPDATE_TOKEN_KEYS));
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
    void failsWithWrongValidityForUpdateFungibleToken() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_UPDATE_TOKEN_KEYS));
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
    void buildingKeyValueWrapperFailsWithNullKey() {
        assertThrows(
                InvalidTransactionException.class,
                () -> PrecompileUtils.buildKeyValueWrapper(null));
    }

    @Test
    void decodeUpdateTokenKeysForFungible() {
        tokenUpdateKeysPrecompile
                .when(() -> decodeUpdateTokenKeys(UPDATE_FUNGIBLE_TOKEN_KEYS, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeUpdateTokenKeys(UPDATE_FUNGIBLE_TOKEN_KEYS, identity());
        assertTrue(decodedInput.tokenID().getTokenNum() > 0);
        assertFalse(decodedInput.tokenKeys().isEmpty());
    }

    private void givenFrameContext() {
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getContractAddress()).willReturn(contractAddr);
        given(frame.getRecipientAddress()).willReturn(fungibleTokenAddr);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
    }

    private void givenMinimalContextForSuccessfulCall() {
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
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
        tokenUpdateKeysPrecompile
                .when(() -> decodeUpdateTokenKeys(any(), any()))
                .thenReturn(updateWrapper);
        given(syntheticTxnFactory.createTokenUpdateKeys(updateWrapper))
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

    private TokenUpdateKeysWrapper getUpdateWrapper() {
        final var adminKey =
                new KeyValueWrapper(
                        false,
                        null,
                        new byte[] {},
                        new byte[] {},
                        EntityIdUtils.contractIdFromEvmAddress(contractAddress));
        final var multiKey =
                new KeyValueWrapper(
                        false,
                        EntityIdUtils.contractIdFromEvmAddress(contractAddress),
                        new byte[] {},
                        new byte[] {},
                        null);
        return new TokenUpdateKeysWrapper(
                fungible,
                List.of(new TokenKeyWrapper(112, multiKey), new TokenKeyWrapper(1, adminKey)));
    }
}
