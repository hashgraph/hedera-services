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
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_DELETE_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenDeleteWrapper;
import static com.hedera.services.store.contracts.precompile.impl.DeleteTokenPrecompile.decodeDelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
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
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.DeleteTokenPrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.NftId;
import com.hedera.services.txns.token.DeleteLogic;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
class DeleteTokenPrecompileTest {
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private TypedTokenStore tokenStore;
    @Mock private AccountStore accountStore;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private ExpiringCreations creator;
    @Mock private ImpliedTransfersMarshal impliedTransfers;
    @Mock private FeeCalculator feeCalculator;
    @Mock private ExchangeRate exchangeRate;
    @Mock private StateView stateView;
    @Mock private ContractAliases aliases;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private DeleteLogic deleteLogic;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private HbarCentExchange exchange;
    @Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nfts;
    @Mock private AccessorFactory accessorFactory;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
            tokenRels;

    @Mock private AssetsLoader assetLoader;

    private HTSPrecompiledContract subject;
    private MockedStatic<DeleteTokenPrecompile> deleteTokenPrecompile;
    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;
    public static final Bytes DELETE_INPUT =
            Bytes.fromHexString(
                    "0xf069f712000000000000000000000000000000000000000000000000000000000000046d");

    @BeforeEach
    void setUp() throws IOException {
        Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(
                HederaFunctionality.TokenDelete, Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
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
                        impliedTransfers,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory);
        deleteTokenPrecompile = Mockito.mockStatic(DeleteTokenPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        deleteTokenPrecompile.close();
    }

    @Test
    void computeCallsSuccessfullyForDeleteFungibleToken() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_DELETE_TOKEN));
        givenFrameContext();
        givenLedgers();
        givenMinimalContextForSuccessfulCall();
        givenMinimalRecordStructureForSuccessfulCall();
        givenTokenDeleteContext();

        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);

        // then
        assertEquals(successResult, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForDeleteToken() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_DELETE_TOKEN));
        givenMinimalFrameContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        givenPricingUtilsContext();
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        deleteTokenPrecompile.when(() -> decodeDelete(any())).thenReturn(tokenDeleteWrapper);
        given(syntheticTxnFactory.createDelete(tokenDeleteWrapper))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenDeletion(TokenDeleteTransactionBody.newBuilder()));
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeTokenDeleteWithValidInput() {
        deleteTokenPrecompile.when(() -> decodeDelete(DELETE_INPUT)).thenCallRealMethod();
        final var decodedInput = decodeDelete(DELETE_INPUT);

        assertEquals(TokenID.newBuilder().setTokenNum(1133).build(), decodedInput.tokenID());
    }

    private void givenMinimalFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
    }

    private void givenFrameContext() {
        givenMinimalFrameContext();
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

    private void givenTokenDeleteContext() {
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, null, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newDeleteLogic(accountStore, tokenStore))
                .willReturn(deleteLogic);
        given(deleteLogic.validate(any())).willReturn(OK);
        deleteTokenPrecompile.when(() -> decodeDelete(any())).thenReturn(tokenDeleteWrapper);
        given(syntheticTxnFactory.createDelete(tokenDeleteWrapper))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenDeletion(TokenDeleteTransactionBody.newBuilder()));
        given(
                        sigsVerifier.hasActiveAdminKey(
                                true, fungibleTokenAddr, fungibleTokenAddr, wrappedLedgers))
                .willReturn(true);
    }

    private void givenMinimalRecordStructureForSuccessfulCall() {
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), null, EMPTY_MEMO))
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
