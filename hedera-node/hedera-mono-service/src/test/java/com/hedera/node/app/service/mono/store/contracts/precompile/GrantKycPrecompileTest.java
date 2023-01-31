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
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GRANT_TOKEN_KYC;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.grantRevokeKycWrapper;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.GrantKycPrecompile.decodeGrantTokenKyc;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
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
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GrantKycPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.txns.token.GrantKycLogic;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
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
class GrantKycPrecompileTest {
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private TypedTokenStore tokenStore;
    @Mock private AccountStore accountStore;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private EvmEncodingFacade evmEncoder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private ExpiringCreations creator;
    @Mock private FeeCalculator feeCalculator;
    @Mock private ExchangeRate exchangeRate;
    @Mock private StateView stateView;
    @Mock private ContractAliases aliases;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private GrantKycLogic grantKycLogic;
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
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
            tokenRels;

    @Mock private AssetsLoader assetLoader;
    @Mock private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    private HTSPrecompiledContract subject;
    private MockedStatic<GrantKycPrecompile> grantKycPrecompile;
    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;
    public static final Bytes GRANT_TOKEN_KYC_INPUT =
            Bytes.fromHexString(
                    "0x8f8d7f9900000000000000000000000000000000000000000000000000000000000004b200000000000000000000000000000000000000000000000000000000000004b0");

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(
                HederaFunctionality.TokenGrantKycToAccount,
                Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
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
        grantKycPrecompile = Mockito.mockStatic(GrantKycPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        grantKycPrecompile.close();
    }

    @Test
    void GrantKyc() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_GRANT_TOKEN_KYC));
        givenFrameContext();
        givenLedgers();
        givenMinimalContextForSuccessfulCall();
        givenMinimalRecordStructureForSuccessfulCall();
        givenGrantContext();

        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);

        // then
        assertEquals(successResult, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForGrantKyc() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_GRANT_TOKEN_KYC));
        givenMinimalFrameContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        givenPricingUtilsContext();
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        grantKycPrecompile
                .when(() -> decodeGrantTokenKyc(any(), any()))
                .thenReturn(grantRevokeKycWrapper);
        given(syntheticTxnFactory.createGrantKyc(grantRevokeKycWrapper))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenGrantKyc(TokenGrantKycTransactionBody.newBuilder()));
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeGrantTokenKycInput() {
        grantKycPrecompile
                .when(() -> decodeGrantTokenKyc(GRANT_TOKEN_KYC_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeGrantTokenKyc(GRANT_TOKEN_KYC_INPUT, identity());

        assertTrue(decodedInput.token().getTokenNum() > 0);
        assertTrue(decodedInput.account().getAccountNum() > 0);
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
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    private void givenGrantContext() {
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newTokenStore(accountStore, null, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newGrantKycLogic(accountStore, tokenStore))
                .willReturn(grantKycLogic);
        given(grantKycLogic.validate(any())).willReturn(OK);
        grantKycPrecompile
                .when(() -> decodeGrantTokenKyc(any(), any()))
                .thenReturn(grantRevokeKycWrapper);
        given(syntheticTxnFactory.createGrantKyc(grantRevokeKycWrapper))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenGrantKyc(TokenGrantKycTransactionBody.newBuilder()));
        given(
                        sigsVerifier.hasActiveKycKey(
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
