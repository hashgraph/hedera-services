package com.hedera.services.store.contracts.precompile;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.HederaBlockValues;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeObject;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;

import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_FREEZE;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createFungibleTokenUpdateWrapperWithKeys;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenFreezeUnFreezeWrapper;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenUpdatePrecompileTest {
	@Mock
	private HederaTokenStore hederaTokenStore;
	@Mock private GlobalDynamicProperties dynamicProperties;
	@Mock private GasCalculator gasCalculator;
	@Mock private MessageFrame frame;
	@Mock private MessageFrame parentFrame;
	@Mock private Deque<MessageFrame> frameDeque;
	@Mock private Iterator<MessageFrame> dequeIterator;
	@Mock private TxnAwareEvmSigsVerifier sigsVerifier;
	@Mock private RecordsHistorian recordsHistorian;
	@Mock private DecodingFacade decoder;
	@Mock private EncodingFacade encoder;
	@Mock private AccountStore accountStore;
	@Mock private TokenUpdateLogic updateLogic;
	@Mock private SideEffectsTracker sideEffects;
	@Mock private TransactionBody.Builder mockSynthBodyBuilder;
	@Mock private CryptoTransferTransactionBody cryptoTransferTransactionBody;
	@Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
	@Mock private SyntheticTxnFactory syntheticTxnFactory;
	@Mock private HederaStackedWorldStateUpdater worldUpdater;
	@Mock private WorldLedgers wrappedLedgers;
	@Mock private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nfts;

	@Mock
	private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
			tokenRels;

	@Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts;
	@Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
	@Mock private ExpiringCreations creator;
	@Mock private ImpliedTransfersMarshal impliedTransfersMarshal;
	@Mock private ImpliedTransfers impliedTransfers;
	@Mock private ImpliedTransfersMeta impliedTransfersMeta;
	@Mock private FeeCalculator feeCalculator;
	@Mock private FeeObject mockFeeObject;
	@Mock private StateView stateView;
	@Mock private ContractAliases aliases;
	@Mock private UsagePricesProvider resourceCosts;
	@Mock private InfrastructureFactory infrastructureFactory;
	@Mock private AssetsLoader assetLoader;
	@Mock private HbarCentExchange exchange;
	@Mock private ExchangeRate exchangeRate;
	private TokenUpdateWrapper updateWrapper = createFungibleTokenUpdateWrapperWithKeys(null);

	private static final long TEST_SERVICE_FEE = 5_000_000;
	private static final long TEST_NETWORK_FEE = 400_000;
	private static final long TEST_NODE_FEE = 300_000;
	private static final int CENTS_RATE = 12;
	private static final int HBAR_RATE = 1;
	private static final long EXPECTED_GAS_PRICE =
			(TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;

	private HTSPrecompiledContract subject;

	@BeforeEach
	void setUp() {
		PrecompilePricingUtils precompilePricingUtils =
				new PrecompilePricingUtils(
						assetLoader, exchange, () -> feeCalculator, resourceCosts, stateView);
		subject =
				new HTSPrecompiledContract(
						dynamicProperties,
						gasCalculator,
						recordsHistorian,
						sigsVerifier,
						decoder,
						encoder,
						syntheticTxnFactory,
						creator,
						impliedTransfersMarshal,
						() -> feeCalculator,
						stateView,
						precompilePricingUtils,
						infrastructureFactory);
		given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
		given(worldUpdater.permissivelyUnaliased(any()))
				.willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
	}

	@Test
	void computeCallsSuccessfullyForUpdateFungibleToken() {
		// given
		final var input = Bytes.of(Integers.toBytes(ABI_ID_UPDATE_TOKEN_INFO));
		givenFrameContext();
		givenLedgers();
		givenMinimalContextForSuccessfulCall();
		givenMinimalRecordStructureForSuccessfulCall();
		givenUpdateTokenContext();
		givenPricingUtilsContext();

		// when
		subject.prepareFields(frame);
		subject.prepareComputation(input, a -> a);
		subject.getPrecompile().getMinimumFeeInTinybars(Timestamp.getDefaultInstance());
		final var result = subject.computeInternal(frame);

		// then
		assertEquals(successResult, result);
	}

	private void givenMinimalFrameContext() {
		given(frame.getSenderAddress()).willReturn(contractAddress);
		given(frame.getWorldUpdater()).willReturn(worldUpdater);
	}

	private void givenFrameContext() {
		givenMinimalFrameContext();
		given(frame.getBlockValues()).willReturn(new HederaBlockValues(10L, 123L, Instant.ofEpochSecond(123L)));
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
		given(sigsVerifier.hasActiveAdminKey(true,fungibleTokenAddr,fungibleTokenAddr,wrappedLedgers)).willReturn(true);
		given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
				.willReturn(hederaTokenStore);
		given(infrastructureFactory.newTokenUpdateLogic(hederaTokenStore,wrappedLedgers,sideEffects))
				.willReturn(updateLogic);
		given(updateLogic.validate(any())).willReturn(OK);
		given(decoder.decodeUpdateTokenInfo(any(), any())).willReturn(updateWrapper);
		given(syntheticTxnFactory.createTokenUpdate(updateWrapper))
				.willReturn(
						TransactionBody.newBuilder()
								.setTokenUpdate(
										TokenUpdateTransactionBody.newBuilder()));
	}

	private void givenMinimalRecordStructureForSuccessfulCall() {
		given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
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