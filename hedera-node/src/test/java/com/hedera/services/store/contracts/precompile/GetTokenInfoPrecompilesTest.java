package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createTokenInfoWrapperForNonFungibleToken;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createTokenInfoWrapperForToken;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.parentContractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.parentContractAddressConvertedToContractId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.payerId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.payerIdConvertedToAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderIdConvertedToAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenMerkleAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenMerkleId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hedera.services.config.NetworkInfo;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JDelegatableContractIDKey;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.Expiry;
import com.hedera.services.store.contracts.precompile.codec.FungibleTokenInfo;
import com.hedera.services.store.contracts.precompile.codec.HederaToken;
import com.hedera.services.store.contracts.precompile.codec.KeyValue;
import com.hedera.services.store.contracts.precompile.codec.NonFungibleTokenInfo;
import com.hedera.services.store.contracts.precompile.codec.TokenInfo;
import com.hedera.services.store.contracts.precompile.codec.TokenKey;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
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
public class GetTokenInfoPrecompilesTest {

  @Mock
  private GlobalDynamicProperties dynamicProperties;
  @Mock private GasCalculator gasCalculator;
  @Mock private MessageFrame frame;
  @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
  @Mock private RecordsHistorian recordsHistorian;
  @Mock private DecodingFacade decoder;
  @Mock private EncodingFacade encoder;
  @Mock private SideEffectsTracker sideEffects;
  @Mock private TransactionBody.Builder mockSynthBodyBuilder;
  @Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
  @Mock private SyntheticTxnFactory syntheticTxnFactory;
  @Mock private HederaStackedWorldStateUpdater worldUpdater;
  @Mock private WorldLedgers wrappedLedgers;
  @Mock private WorldLedgers trackingLedgers;
  @Mock private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nfts;
  @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
  @Mock private ExpiringCreations creator;
  @Mock private ImpliedTransfersMarshal impliedTransfersMarshal;
  @Mock private FeeCalculator feeCalculator;
  @Mock private StateView stateView;
  @Mock private UsagePricesProvider resourceCosts;
  @Mock private InfrastructureFactory infrastructureFactory;
  @Mock private AssetsLoader assetLoader;
  @Mock private HbarCentExchange exchange;
  @Mock private NetworkInfo networkInfo;
  @Mock private MerkleToken merkleToken;
  @Mock private MerkleUniqueToken uniqueToken;
  @Mock private FeeObject mockFeeObject;
  @Mock private JKey adminKey;
  @Mock private JContractIDKey contractKey;
  @Mock private JDelegatableContractIDKey delegateContractKey;

  private HTSPrecompiledContract subject;
  private MockedStatic<EntityIdUtils> entityIdUtils;

  private final String name = "Name";
  private final String symbol = "N";
  private final EntityId treasury = senderId;
  private final Address treasuryAddress = senderIdConvertedToAddress;
  private final String memo = "Memo";
  private final long maxSupply = 10000;
  private final boolean freezeDefault = false;
  private final KeyValue keyValue = new KeyValue(false, parentContractAddress, new byte[]{}, new byte[]{}, null);
  private final TokenKey tokenKey = new TokenKey(1, keyValue);
  private final List<TokenKey> tokenKeys = new ArrayList<>();
  private final long expiryPeriod = 10200L;
  private final EntityId autoRenewAccount = senderId;
  private final Address autoRenewAccountAddress = senderIdConvertedToAddress;
  private final long autoRenewPeriod = 500L;
  private final long totalSupply = 20500;
  private final boolean deleted = false;
  private final boolean defaultKycStatus = false;
  private final boolean pauseStatus = false;
  private final String ledgerId = "0x03";
  private final int decimals = 10;
  private final long serialNumber = 1;
  private final Address ownerId = payerIdConvertedToAddress;
  private final long creationTime = 152435353252L;
  private final byte[] metadata = "Metadata".getBytes();
  private final Address spenderId = senderIdConvertedToAddress;

  private TokenInfo tokenInfo;
  private FungibleTokenInfo fungibleTokenInfo;
  private NonFungibleTokenInfo nonFungibleTokenInfo;

  @BeforeEach
  void setUp() {
    final PrecompilePricingUtils precompilePricingUtils =
        new PrecompilePricingUtils(
            assetLoader, exchange, () -> feeCalculator, resourceCosts, stateView);

    entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
    entityIdUtils
        .when(() -> EntityIdUtils.asTypedEvmAddress(tokenMerkleId))
        .thenReturn(tokenMerkleAddress);
    entityIdUtils
        .when(() -> EntityIdUtils.asTypedEvmAddress(treasury))
        .thenReturn(treasuryAddress);
    entityIdUtils
        .when(() -> EntityIdUtils.asTypedEvmAddress(payerId))
        .thenReturn(payerIdConvertedToAddress);
    entityIdUtils
        .when(() -> EntityIdUtils.asTypedEvmAddress(senderId))
        .thenReturn(senderIdConvertedToAddress);
    tokenKeys.add(tokenKey);

    final Expiry expiry = new Expiry(expiryPeriod, autoRenewAccountAddress, autoRenewPeriod);
    final HederaToken hederaToken = new HederaToken(name, symbol,
        EntityIdUtils.asTypedEvmAddress(treasury), memo,
        true, maxSupply, freezeDefault, tokenKeys, expiry);
    tokenInfo = new TokenInfo(hederaToken, totalSupply, deleted, defaultKycStatus, pauseStatus, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), ledgerId);
    fungibleTokenInfo = new FungibleTokenInfo(tokenInfo, decimals);
    nonFungibleTokenInfo = new NonFungibleTokenInfo(tokenInfo, serialNumber, ownerId, creationTime, metadata, spenderId);

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
            infrastructureFactory,
            networkInfo);
    given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
    given(worldUpdater.permissivelyUnaliased(any()))
        .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
  }

  @AfterEach
  void closeMocks() {
    entityIdUtils.close();
  }

  @Test
  void getTokenInfoWorks() {
    givenMinimalFrameContext();

    final var tokenInfoWrapper =
       createTokenInfoWrapperForToken(tokenMerkleId);
    final Bytes pretendArguments = Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)),
        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
    given(decoder.decodeGetTokenInfo(pretendArguments))
        .willReturn(tokenInfoWrapper);

    givenMinimalTokenContext();
    givenMinimalKeyContext();

    given(encoder.encodeGetTokenInfo(tokenInfo)).willReturn(successResult);

    givenMinimalContext(pretendArguments);
    givenReadOnlyFeeSchedule();

    // when:
    subject.prepareFields(frame);
    subject.prepareComputation(pretendArguments, a -> a);
    subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
    final var result = subject.computeInternal(frame);

    // then:
    assertEquals(successResult, result);
    // and:
    verify(worldUpdater)
        .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
  }

  @Test
  void getFungibleTokenInfoWorks() {
    givenMinimalFrameContext();

    final var tokenInfoWrapper =
        createTokenInfoWrapperForToken(tokenMerkleId);
    final Bytes pretendArguments = Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_GET_FUNGIBLE_TOKEN_INFO)),
        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
    given(decoder.decodeGetFungibleTokenInfo(pretendArguments))
        .willReturn(tokenInfoWrapper);

    givenMinimalTokenContext();
    given(merkleToken.decimals()).willReturn(decimals);
    givenMinimalKeyContext();

    given(encoder.encodeGetFungibleTokenInfo(fungibleTokenInfo)).willReturn(successResult);

    givenMinimalContext(pretendArguments);
    givenReadOnlyFeeSchedule();

    // when:
    subject.prepareFields(frame);
    subject.prepareComputation(pretendArguments, a -> a);
    subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
    final var result = subject.computeInternal(frame);

    // then:
    assertEquals(successResult, result);
    // and:
    verify(worldUpdater)
        .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
  }

  @Test
  void getNonFungibleTokenInfoWorks() {
    givenMinimalFrameContext();

    final var tokenInfoWrapper =
        createTokenInfoWrapperForNonFungibleToken(tokenMerkleId, serialNumber);
    final Bytes pretendArguments = Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO)),
        EntityIdUtils.asTypedEvmAddress(tokenMerkleId), Bytes.wrap(new byte[]{Long.valueOf(serialNumber).byteValue()}));
    given(decoder.decodeGetNonFungibleTokenInfo(pretendArguments))
        .willReturn(tokenInfoWrapper);

    givenMinimalTokenContext();
    givenMinimalUniqueTokenContext();
    givenMinimalKeyContext();

    given(encoder.encodeGetNonFungibleTokenInfo(nonFungibleTokenInfo)).willReturn(successResult);

    givenMinimalContext(pretendArguments);
    givenReadOnlyFeeSchedule();

    // when:
    subject.prepareFields(frame);
    subject.prepareComputation(pretendArguments, a -> a);
    subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
    final var result = subject.computeInternal(frame);

    // then:
    assertEquals(successResult, result);
    // and:
    verify(worldUpdater)
        .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
  }

  private void givenReadOnlyFeeSchedule() {
    given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
        .willReturn(mockFeeObject);
    given(
        feeCalculator.estimatedGasPriceInTinybars(
            HederaFunctionality.ContractCall, timestamp))
        .willReturn(1L);
    given(mockFeeObject.getNodeFee()).willReturn(1L);
    given(mockFeeObject.getNetworkFee()).willReturn(1L);
    given(mockFeeObject.getServiceFee()).willReturn(1L);
  }

  private void givenMinimalKeyContext() {
    given(adminKey.getEd25519()).willReturn(new byte[]{});
    given(adminKey.getECDSASecp256k1Key()).willReturn(new byte[]{});
    given(adminKey.getContractIDKey()).willReturn(contractKey);
    entityIdUtils
        .when(() -> EntityIdUtils.asTypedEvmAddress(parentContractAddressConvertedToContractId))
        .thenReturn(parentContractAddress);
    given(contractKey.getContractID()).willReturn(parentContractAddressConvertedToContractId);
    given(adminKey.getDelegatableContractIdKey()).willReturn(delegateContractKey);
    given(delegateContractKey.getContractID()).willReturn(null);
  }

  private void givenMinimalTokenContext() {
    given(trackingLedgers.tokens()).willReturn(tokens);
    given(tokens.getImmutableRef(tokenMerkleId)).willReturn(merkleToken);
    given(merkleToken.name()).willReturn(name);
    given(merkleToken.symbol()).willReturn(symbol);
    given(merkleToken.treasury()).willReturn(treasury);
    given(merkleToken.memo()).willReturn(memo);
    given(merkleToken.supplyType()).willReturn(TokenSupplyType.FINITE);
    given(merkleToken.maxSupply()).willReturn(maxSupply);
    given(merkleToken.hasFreezeKey()).willReturn(freezeDefault);
    given(merkleToken.expiry()).willReturn(expiryPeriod);
    entityIdUtils
        .when(() -> EntityIdUtils.asTypedEvmAddress(autoRenewAccount))
        .thenReturn(autoRenewAccountAddress);
    given(merkleToken.autoRenewAccount()).willReturn(autoRenewAccount);
    given(merkleToken.autoRenewPeriod()).willReturn(autoRenewPeriod);
    given(merkleToken.totalSupply()).willReturn(totalSupply);
    given(merkleToken.isDeleted()).willReturn(deleted);
    given(merkleToken.accountsKycGrantedByDefault()).willReturn(defaultKycStatus);
    given(merkleToken.isPaused()).willReturn(pauseStatus);
    given(merkleToken.getAdminKey()).willReturn(adminKey);
    given(merkleToken.getFreezeKey()).willReturn(null);
    given(merkleToken.getPauseKey()).willReturn(null);
    given(merkleToken.getFeeScheduleKey()).willReturn(null);
    given(merkleToken.getSupplyKey()).willReturn(null);
    given(merkleToken.getWipeKey()).willReturn(null);
    given(merkleToken.getKycKey()).willReturn(null);

    given(networkInfo.ledgerId()).willReturn(ByteString.copyFrom(ledgerId.getBytes()));
  }

  private void givenMinimalUniqueTokenContext() {
    given(trackingLedgers.nfts()).willReturn(nfts);
    given(nfts.getImmutableRef(NftId.fromGrpc(tokenMerkleId, serialNumber))).willReturn(uniqueToken);
    given(uniqueToken.getOwner()).willReturn(payerId);
    given(uniqueToken.getPackedCreationTime()).willReturn(creationTime);
    given(uniqueToken.getMetadata()).willReturn(metadata);
    given(uniqueToken.getSpender()).willReturn(senderId);
  }

  private void givenMinimalFrameContext() {
    given(frame.getWorldUpdater()).willReturn(worldUpdater);
    given(frame.getRemainingGas()).willReturn(100_000L);
    given(frame.getValue()).willReturn(Wei.ZERO);
    given(frame.getSenderAddress()).willReturn(senderAddress);
    Optional<WorldUpdater> parent = Optional.of(worldUpdater);
    given(worldUpdater.parentUpdater()).willReturn(parent);
    given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    given(worldUpdater.trackingLedgers()).willReturn(trackingLedgers);
  }

  private void givenMinimalContext(final Bytes pretendArguments) {
    given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
        .willReturn(mockSynthBodyBuilder);
    given(
        creator.createSuccessfulSyntheticRecord(
            Collections.emptyList(), sideEffects, EMPTY_MEMO))
        .willReturn(mockRecordBuilder);
  }
}
