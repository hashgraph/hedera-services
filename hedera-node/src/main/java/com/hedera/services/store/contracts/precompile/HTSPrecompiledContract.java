package com.hedera.services.store.contracts.precompile;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.proxy.RedirectGasCalculator;
import com.hedera.services.store.contracts.precompile.proxy.RedirectViewExecutor;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.crypto.ApproveAllowanceLogic;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.txns.crypto.DeleteAllowanceLogic;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.services.txns.token.AssociateLogic;
import com.hedera.services.txns.token.BurnLogic;
import com.hedera.services.txns.token.CreateLogic;
import com.hedera.services.txns.token.DissociateLogic;
import com.hedera.services.txns.token.MintLogic;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.token.validators.CreateChecks;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.grpc.marshalling.ImpliedTransfers.NO_ALIASES;
import static com.hedera.services.ledger.ids.ExceptionalEntityIdSource.NOOP_ID_SOURCE;
import static com.hedera.services.ledger.properties.AccountProperty.APPROVE_FOR_ALL_NFTS_ALLOWANCES;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.properties.AccountProperty.FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hedera.services.ledger.properties.NftProperty.SPENDER;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.store.contracts.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;
import static com.hedera.services.store.contracts.precompile.DescriptorUtils.isTokenProxyRedirect;
import static com.hedera.services.store.contracts.precompile.PrecompilePricingUtils.GasCostType.APPROVE;
import static com.hedera.services.store.contracts.precompile.PrecompilePricingUtils.GasCostType.ASSOCIATE;
import static com.hedera.services.store.contracts.precompile.PrecompilePricingUtils.GasCostType.DELETE_NFT_APPROVE;
import static com.hedera.services.store.contracts.precompile.PrecompilePricingUtils.GasCostType.DISSOCIATE;
import static com.hedera.services.store.contracts.precompile.PrecompilePricingUtils.GasCostType.MINT_FUNGIBLE;
import static com.hedera.services.store.contracts.precompile.PrecompilePricingUtils.GasCostType.MINT_NFT;
import static com.hedera.services.store.contracts.precompile.TokenCreateWrapper.FixedFeeWrapper.FixedFeePayment.INVALID_PAYMENT;
import static com.hedera.services.store.contracts.precompile.TokenCreateWrapper.KeyValueWrapper.KeyValueType.INVALID_KEY;
import static com.hedera.services.txns.span.SpanMapManager.reCalculateXferMeta;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

@Singleton
public class HTSPrecompiledContract extends AbstractPrecompiledContract {
	private static final Logger log = LogManager.getLogger(HTSPrecompiledContract.class);

	public static final String HTS_PRECOMPILED_CONTRACT_ADDRESS = "0x167";
	public static final Address TYPED_HTS_PRECOMPILED_CONTRACT_ADDRESS =
			Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS);
	public static final ContractID HTS_PRECOMPILE_MIRROR_ID = contractIdFromEvmAddress(
			TYPED_HTS_PRECOMPILED_CONTRACT_ADDRESS.toArrayUnsafe());
	public static final EntityId HTS_PRECOMPILE_MIRROR_ENTITY_ID =
			EntityId.fromGrpcContractId(HTS_PRECOMPILE_MIRROR_ID);

	private static final Query SYNTHETIC_REDIRECT_QUERY = Query.newBuilder()
			.setTransactionGetRecord(TransactionGetRecordQuery.newBuilder().build())
			.build();
	private static final Bytes STATIC_CALL_REVERT_REASON = Bytes.of("HTS precompiles are not static".getBytes());
	private static final String NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON = "Invalid operation for ERC-20 token!";
	private static final String NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON = "Invalid operation for ERC-721 token!";
	private static final Bytes ERROR_DECODING_INPUT_REVERT_REASON = Bytes.of(
			"Error decoding precompile input".getBytes());
	private static final List<Long> NO_SERIAL_NOS = Collections.emptyList();
	private static final List<ByteString> NO_METADATA = Collections.emptyList();
	private static final EntityIdSource ids = NOOP_ID_SOURCE;
	public static final String URI_QUERY_NON_EXISTING_TOKEN_ERROR = "ERC721Metadata: URI query for nonexistent token";

	private CreateLogicFactory createLogicFactory = CreateLogic::new;
	private MintLogicFactory mintLogicFactory = MintLogic::new;
	private BurnLogicFactory burnLogicFactory = BurnLogic::new;
	private AssociateLogicFactory associateLogicFactory = AssociateLogic::new;
	private DissociateLogicFactory dissociateLogicFactory = DissociateLogic::new;
	private ApproveAllowanceLogicFactory approveAllowanceLogicFactory = ApproveAllowanceLogic::new;
	private DeleteAllowanceLogicFactory deleteAllowanceLogicFactory = DeleteAllowanceLogic::new;
	private TransferLogicFactory transferLogicFactory = TransferLogic::new;
	private TokenStoreFactory tokenStoreFactory = TypedTokenStore::new;
	private HederaTokenStoreFactory hederaTokenStoreFactory = HederaTokenStore::new;
	private AccountStoreFactory accountStoreFactory = AccountStore::new;
	private final RedirectExecutorFactory redirectExecutorFactory = RedirectViewExecutor::new;
	private Supplier<SideEffectsTracker> sideEffectsFactory = SideEffectsTracker::new;

	private final EntityCreator creator;
	private final DecodingFacade decoder;
	private final EncodingFacade encoder;
	private final GlobalDynamicProperties dynamicProperties;
	private final OptionValidator validator;
	private final EvmSigsVerifier sigsVerifier;
	private final SigImpactHistorian sigImpactHistorian;
	private final RecordsHistorian recordsHistorian;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final DissociationFactory dissociationFactory;
	private final UsagePricesProvider resourceCosts;
	private final CreateChecks tokenCreateChecks;
	private final EntityIdSource entityIdSource;

	private final ImpliedTransfersMarshal impliedTransfersMarshal;

	//cryptoTransfer(TokenTransferList[] memory tokenTransfers)
	protected static final int ABI_ID_CRYPTO_TRANSFER = 0x189a554c;
	//transferTokens(address token, address[] memory accountId, int64[] memory amount)
	protected static final int ABI_ID_TRANSFER_TOKENS = 0x82bba493;
	//transferToken(address token, address sender, address recipient, int64 amount)
	protected static final int ABI_ID_TRANSFER_TOKEN = 0xeca36917;
	//transferNFTs(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber)
	protected static final int ABI_ID_TRANSFER_NFTS = 0x2c4ba191;
	//transferNFT(address token,  address sender, address recipient, int64 serialNum)
	protected static final int ABI_ID_TRANSFER_NFT = 0x5cfc9011;
	//mintToken(address token, uint64 amount, bytes[] memory metadata)
	protected static final int ABI_ID_MINT_TOKEN = 0x278e0b88;
	//burnToken(address token, uint64 amount, int64[] memory serialNumbers)
	protected static final int ABI_ID_BURN_TOKEN = 0xacb9cff9;
	//associateTokens(address account, address[] memory tokens)
	protected static final int ABI_ID_ASSOCIATE_TOKENS = 0x2e63879b;
	//associateToken(address account, address token)
	protected static final int ABI_ID_ASSOCIATE_TOKEN = 0x49146bde;
	//dissociateTokens(address account, address[] memory tokens)
	protected static final int ABI_ID_DISSOCIATE_TOKENS = 0x78b63918;
	//dissociateToken(address account, address token)
	protected static final int ABI_ID_DISSOCIATE_TOKEN = 0x099794e8;
	//redirectForToken(address token, bytes memory data)
	protected static final int ABI_ID_REDIRECT_FOR_TOKEN = 0x618dc65e;

	//name()
	public static final int ABI_ID_NAME = 0x06fdde03;
	//symbol()
	public static final int ABI_ID_SYMBOL = 0x95d89b41;
	//decimals()
	public static final int ABI_ID_DECIMALS = 0x313ce567;
	//totalSupply()
	public static final int ABI_ID_TOTAL_SUPPLY_TOKEN = 0x18160ddd;
	//balanceOf(address account)
	public static final int ABI_ID_BALANCE_OF_TOKEN = 0x70a08231;
	//transfer(address recipient, uint256 amount)
	public static final int ABI_ID_ERC_TRANSFER = 0xa9059cbb;
	//transferFrom(address sender, address recipient, uint256 amount)
	//transferFrom(address from, address to, uint256 tokenId)
	public static final int ABI_ID_ERC_TRANSFER_FROM = 0x23b872dd;
	//allowance(address token, address owner, address spender)
	protected static final int ABI_ID_ALLOWANCE = 0xdd62ed3e;
	//approve(address token, address spender, uint256 amount)
	//approve(address token, address to, uint256 tokenId)
	protected static final int ABI_ID_APPROVE = 0x95ea7b3;
	//setApprovalForAll(address token, address operator, bool approved)
	protected static final int ABI_ID_SET_APPROVAL_FOR_ALL = 0xa22cb465;
	//getApproved(address token, uint256 tokenId)
	protected static final int ABI_ID_GET_APPROVED = 0x081812fc;
	//isApprovedForAll(address token, address owner, address operator)
	protected static final int ABI_ID_IS_APPROVED_FOR_ALL = 0xe985e9c5;
	//ownerOf(uint256 tokenId)
	public static final int ABI_ID_OWNER_OF_NFT = 0x6352211e;
	//tokenURI(uint256 tokenId)
	public static final int ABI_ID_TOKEN_URI_NFT = 0xc87b56dd;

	//createFungibleToken(HederaToken memory token, uint initialTotalSupply, uint decimals)
	protected static final int ABI_ID_CREATE_FUNGIBLE_TOKEN = 0x7812a04b;
	//createFungibleTokenWithCustomFees(HederaToken memory token, uint initialTotalSupply, uint decimals, FixedFee[]
	// memory fixedFees, FractionalFee[] memory fractionalFees)
	protected static final int ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES = 0x4c381ae7;
	//createNonFungibleToken(HederaToken memory token)
	protected static final int ABI_ID_CREATE_NON_FUNGIBLE_TOKEN = 0x9dc711e0;
	//createNonFungibleTokenWithCustomFees(HederaToken memory token, FixedFee[] memory fixedFees, RoyaltyFee[] memory
	// royaltyFees)
	protected static final int ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES = 0x5bc7c0e6;

	//Transfer(address indexed from, address indexed to, uint256 indexed tokenId)
	//Transfer(address indexed from, address indexed to, uint256 value)
	private static final Bytes TRANSFER_EVENT = Bytes.fromHexString(
			"ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
	//Approval(address indexed owner, address indexed spender, uint256 value)
	//Approval(address indexed owner, address indexed approved, uint256 indexed tokenId)
	private static final Bytes APPROVAL_EVENT = Bytes.fromHexString(
			"8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925");
	//ApprovalForAll(address indexed owner, address indexed operator, bool approved)
	private static final Bytes APPROVAL_FOR_ALL_EVENT = Bytes.fromHexString(
			"17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31");

	private int functionId;
	private Precompile precompile;
	private TransactionBody.Builder transactionBody;
	private final Provider<FeeCalculator> feeCalculator;
	private Gas gasRequirement = Gas.ZERO;
	private final StateView currentView;
	private SideEffectsTracker sideEffectsTracker;
	private final PrecompilePricingUtils precompilePricingUtils;
	private WorldLedgers ledgers;
	private Address senderAddress;
	private HederaStackedWorldStateUpdater updater;
	private boolean isTokenReadOnlyTransaction = false;
	private final DeleteAllowanceChecks deleteAllowanceChecks;
	private final ApproveAllowanceChecks approveAllowanceChecks;

	@Inject
	public HTSPrecompiledContract(
			final OptionValidator validator,
			final GlobalDynamicProperties dynamicProperties,
			final GasCalculator gasCalculator,
			final SigImpactHistorian sigImpactHistorian,
			final RecordsHistorian recordsHistorian,
			final TxnAwareEvmSigsVerifier sigsVerifier,
			final DecodingFacade decoder,
			final EncodingFacade encoder,
			final SyntheticTxnFactory syntheticTxnFactory,
			final ExpiringCreations creator,
			final DissociationFactory dissociationFactory,
			final ImpliedTransfersMarshal impliedTransfersMarshal,
			final Provider<FeeCalculator> feeCalculator,
			final StateView currentView,
			final PrecompilePricingUtils precompilePricingUtils,
			final UsagePricesProvider resourceCosts,
			final CreateChecks tokenCreateChecks,
			final EntityIdSource entityIdSource,
			final ApproveAllowanceChecks approveAllowanceChecks,
			final DeleteAllowanceChecks deleteAllowanceChecks
	) {
		super("HTS", gasCalculator);
		this.sigImpactHistorian = sigImpactHistorian;
		this.decoder = decoder;
		this.encoder = encoder;
		this.sigsVerifier = sigsVerifier;
		this.recordsHistorian = recordsHistorian;
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.creator = creator;
		this.validator = validator;
		this.dynamicProperties = dynamicProperties;
		this.dissociationFactory = dissociationFactory;
		this.impliedTransfersMarshal = impliedTransfersMarshal;
		this.feeCalculator = feeCalculator;
		this.currentView = currentView;
		this.precompilePricingUtils = precompilePricingUtils;
		this.resourceCosts = resourceCosts;
		this.tokenCreateChecks = tokenCreateChecks;
		this.entityIdSource = entityIdSource;
		this.approveAllowanceChecks = approveAllowanceChecks;
		this.deleteAllowanceChecks = deleteAllowanceChecks;
	}

	public Pair<Gas, Bytes> computeCosted(final Bytes input, final MessageFrame frame) {
		if (frame.isStatic()) {
			if (!isTokenProxyRedirect(input)) {
				frame.setRevertReason(STATIC_CALL_REVERT_REASON);
				return Pair.of(defaultGas(), null);
			} else {
				final var proxyUpdater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
				if (!proxyUpdater.hasMutableLedgers()) {
					final var executor = redirectExecutorFactory.newRedirectExecutor(
							input, frame, encoder, decoder, this::computeViewFunctionGas);
					return executor.computeCosted();
				}
			}
		}
		final var result = compute(input, frame);
		return Pair.of(gasRequirement, result);
	}

	@Override
	public Gas gasRequirement(final Bytes bytes) {
		return gasRequirement;
	}

	@Override
	public Bytes compute(final Bytes input, final MessageFrame frame) {
		prepareFields(frame);
		prepareComputation(input, updater::unaliased);

		gasRequirement = defaultGas();
		if (this.precompile == null || this.transactionBody == null) {
			frame.setRevertReason(ERROR_DECODING_INPUT_REVERT_REASON);
			return null;
		}

		final var now = frame.getBlockValues().getTimestamp();
		if (isTokenReadOnlyTransaction) {
			computeViewFunctionGasRequirement(now);
		} else if (precompile instanceof TokenCreatePrecompile) {
			gasRequirement = Gas.of(precompile.getMinimumFeeInTinybars(Timestamp.newBuilder().setSeconds(now).build()));
		} else {
			computeGasRequirement(now);
		}
		return computeInternal(frame);
	}

	void prepareFields(final MessageFrame frame) {
		this.updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
		this.sideEffectsTracker = sideEffectsFactory.get();
		this.ledgers = updater.wrappedTrackingLedgers(sideEffectsTracker);
		final var unaliasedSenderAddress = updater.permissivelyUnaliased(frame.getSenderAddress().toArray());
		this.senderAddress = Address.wrap(Bytes.of(unaliasedSenderAddress));
	}

	void computeGasRequirement(final long blockTimestamp) {
		final Timestamp timestamp = Timestamp.newBuilder().setSeconds(
				blockTimestamp).build();
		final long gasPriceInTinybars = feeCalculator.get().estimatedGasPriceInTinybars(ContractCall, timestamp);

		final long calculatedFeeInTinybars = gasFeeInTinybars(
				transactionBody.setTransactionID(TransactionID.newBuilder().setTransactionValidStart(
						timestamp).build()), Instant.ofEpochSecond(blockTimestamp));

		final long minimumFeeInTinybars = precompile.getMinimumFeeInTinybars(timestamp);
		final long actualFeeInTinybars = Math.max(minimumFeeInTinybars, calculatedFeeInTinybars);

		// convert to gas cost
		final Gas baseGasCost = Gas.of((actualFeeInTinybars + gasPriceInTinybars - 1) / gasPriceInTinybars);

		// charge premium
		gasRequirement = baseGasCost.plus((baseGasCost.dividedBy(5)));
	}

	void computeViewFunctionGasRequirement(final long blockTimestamp) {
		final var now = Timestamp.newBuilder().setSeconds(blockTimestamp).build();
		gasRequirement = computeViewFunctionGas(now, precompile.getMinimumFeeInTinybars(now));
	}

	Gas computeViewFunctionGas(final Timestamp now, final long minimumTinybarCost) {
		final var calculator = feeCalculator.get();
		final var usagePrices = resourceCosts.defaultPricesGiven(TokenGetInfo, now);
		final var fees = calculator.estimatePayment(
				SYNTHETIC_REDIRECT_QUERY, usagePrices, currentView, now, ANSWER_ONLY);

		final long gasPriceInTinybars = calculator.estimatedGasPriceInTinybars(ContractCall, now);
		final long calculatedFeeInTinybars = fees.getNetworkFee() + fees.getNodeFee() + fees.getServiceFee();
		final long actualFeeInTinybars = Math.max(minimumTinybarCost, calculatedFeeInTinybars);

		// convert to gas cost
		final Gas baseGasCost = Gas.of((actualFeeInTinybars + gasPriceInTinybars - 1) / gasPriceInTinybars);

		// charge premium
		return baseGasCost.plus((baseGasCost.dividedBy(5)));
	}

	void prepareComputation(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		this.precompile = null;
		this.transactionBody = null;

		this.functionId = input.getInt(0);
		this.gasRequirement = null;
		this.isTokenReadOnlyTransaction = false;

		this.precompile =
				switch (functionId) {
					case ABI_ID_CRYPTO_TRANSFER,
							ABI_ID_TRANSFER_TOKENS,
							ABI_ID_TRANSFER_TOKEN,
							ABI_ID_TRANSFER_NFTS,
							ABI_ID_TRANSFER_NFT -> new TransferPrecompile();
					case ABI_ID_MINT_TOKEN -> new MintPrecompile();
					case ABI_ID_BURN_TOKEN -> new BurnPrecompile();
					case ABI_ID_ASSOCIATE_TOKENS -> new MultiAssociatePrecompile();
					case ABI_ID_ASSOCIATE_TOKEN -> new AssociatePrecompile();
					case ABI_ID_DISSOCIATE_TOKENS -> new MultiDissociatePrecompile();
					case ABI_ID_DISSOCIATE_TOKEN -> new DissociatePrecompile();
					case ABI_ID_REDIRECT_FOR_TOKEN -> {
						final var target = DescriptorUtils.getRedirectTarget(input);
						final var tokenId = target.tokenId();
						final var isFungibleToken = TokenType.FUNGIBLE_COMMON.equals(ledgers.typeOf(tokenId));
						Precompile nestedPrecompile;
						this.isTokenReadOnlyTransaction = true;
						final var nestedFunctionSelector = target.descriptor();
						if (ABI_ID_NAME == nestedFunctionSelector) {
							nestedPrecompile = new NamePrecompile(tokenId);
						} else if (ABI_ID_SYMBOL == nestedFunctionSelector) {
							nestedPrecompile = new SymbolPrecompile(tokenId);
						} else if (ABI_ID_DECIMALS == nestedFunctionSelector) {
							if (!isFungibleToken) {
								throw new InvalidTransactionException(
										NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON, INVALID_TOKEN_ID);
							}
							nestedPrecompile = new DecimalsPrecompile(tokenId);
						} else if (ABI_ID_TOTAL_SUPPLY_TOKEN == nestedFunctionSelector) {
							nestedPrecompile = new TotalSupplyPrecompile(tokenId);
						} else if (ABI_ID_BALANCE_OF_TOKEN == nestedFunctionSelector) {
							nestedPrecompile = new BalanceOfPrecompile(tokenId);
						} else if (ABI_ID_OWNER_OF_NFT == nestedFunctionSelector) {
							if (isFungibleToken) {
								throw new InvalidTransactionException(
										NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON, INVALID_TOKEN_ID);
							}
							nestedPrecompile = new OwnerOfPrecompile(tokenId);
						} else if (ABI_ID_TOKEN_URI_NFT == nestedFunctionSelector) {
							if (isFungibleToken) {
								throw new InvalidTransactionException(
										NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON, INVALID_TOKEN_ID);
							}
							nestedPrecompile = new TokenURIPrecompile(tokenId);
						} else if (ABI_ID_ERC_TRANSFER == nestedFunctionSelector) {
							this.isTokenReadOnlyTransaction = false;
							if (!isFungibleToken) {
								throw new InvalidTransactionException(
										NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON, INVALID_TOKEN_ID);
							}
							nestedPrecompile = new ERCTransferPrecompile(tokenId, this.senderAddress, isFungibleToken);
						} else if (ABI_ID_ERC_TRANSFER_FROM == nestedFunctionSelector) {
							if (!dynamicProperties.areAllowancesEnabled()) {
								throw new InvalidTransactionException(NOT_SUPPORTED);
							}
							this.isTokenReadOnlyTransaction = false;
							nestedPrecompile = new ERCTransferPrecompile(tokenId, this.senderAddress, isFungibleToken);
						} else if (ABI_ID_ALLOWANCE == nestedFunctionSelector) {
							if (!dynamicProperties.areAllowancesEnabled()) {
								throw new InvalidTransactionException(NOT_SUPPORTED);
							}
							nestedPrecompile = new AllowancePrecompile(tokenId);
						} else if (ABI_ID_APPROVE == nestedFunctionSelector) {
							if (!dynamicProperties.areAllowancesEnabled()) {
								throw new InvalidTransactionException(NOT_SUPPORTED);
							}
							this.isTokenReadOnlyTransaction = false;
							nestedPrecompile = new ApprovePrecompile(tokenId, isFungibleToken);
						} else if (ABI_ID_SET_APPROVAL_FOR_ALL == nestedFunctionSelector) {
							if (!dynamicProperties.areAllowancesEnabled()) {
								throw new InvalidTransactionException(NOT_SUPPORTED);
							}
							nestedPrecompile = new SetApprovalForAllPrecompile(tokenId);
						} else if (ABI_ID_GET_APPROVED == nestedFunctionSelector) {
							if (!dynamicProperties.areAllowancesEnabled()) {
								throw new InvalidTransactionException(NOT_SUPPORTED);
							}
							nestedPrecompile = new GetApprovedPrecompile(tokenId);
						} else if (ABI_ID_IS_APPROVED_FOR_ALL == nestedFunctionSelector) {
							if (!dynamicProperties.areAllowancesEnabled()) {
								throw new InvalidTransactionException(NOT_SUPPORTED);
							}
							nestedPrecompile = new IsApprovedForAllPrecompile(tokenId);
						} else {
							this.isTokenReadOnlyTransaction = false;
							nestedPrecompile = null;
						}
						yield nestedPrecompile;
					}
					case ABI_ID_CREATE_FUNGIBLE_TOKEN,
							ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES,
							ABI_ID_CREATE_NON_FUNGIBLE_TOKEN,
							ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES ->
							dynamicProperties.isHTSPrecompileCreateEnabled() ? new TokenCreatePrecompile() : null;
					default -> null;
				};
		if (precompile != null) {
			decodeInput(input, aliasResolver);
		}
	}

	/* --- Helpers --- */
	private AccountStore createAccountStore() {
		return accountStoreFactory.newAccountStore(validator, ledgers.accounts());
	}

	private TypedTokenStore createTokenStore(
			final AccountStore accountStore,
			final SideEffectsTracker sideEffects
	) {
		return tokenStoreFactory.newTokenStore(
				accountStore,
				ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels(),
				sideEffects);
	}

	void decodeInput(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		this.transactionBody = TransactionBody.newBuilder();
		try {
			this.transactionBody = this.precompile.body(input, aliasResolver);
		} catch (Exception ignore) {
			transactionBody = null;
		}
	}

	@SuppressWarnings("rawtypes")
	protected Bytes computeInternal(final MessageFrame frame) {
		Bytes result;
		ExpirableTxnRecord.Builder childRecord;
		try {
			validateTrue(frame.getRemainingGas().compareTo(gasRequirement) >= 0, INSUFFICIENT_GAS);

			precompile.handleSentHbars(frame);
			precompile.customizeTrackingLedgers(ledgers);
			precompile.run(frame);

			// As in HederaLedger.commit(), we must first commit the ledgers before creating our
			// synthetic record, as the ledger interceptors will populate the sideEffectsTracker
			ledgers.commit();

			childRecord = creator.createSuccessfulSyntheticRecord(
					precompile.getCustomFees(), sideEffectsTracker, EMPTY_MEMO);
			result = precompile.getSuccessResultFor(childRecord);
			addContractCallResultToRecord(childRecord, result, Optional.empty(), frame);
		} catch (final InvalidTransactionException e) {
			final var status = e.getResponseCode();
			childRecord = creator.createUnsuccessfulSyntheticRecord(status);
			result = precompile.getFailureResultFor(status);
			addContractCallResultToRecord(childRecord, result, Optional.of(status), frame);
			if (e.isReverting()) {
				frame.setState(MessageFrame.State.REVERT);
				frame.setRevertReason(e.getRevertReason());
			}
		} catch (final Exception e) {
			log.warn("Internal precompile failure", e);
			childRecord = creator.createUnsuccessfulSyntheticRecord(FAIL_INVALID);
			result = precompile.getFailureResultFor(FAIL_INVALID);
			addContractCallResultToRecord(childRecord, result, Optional.of(FAIL_INVALID), frame);
		}

		// This should always have a parent stacked updater
		final var parentUpdater = updater.parentUpdater();
		if (parentUpdater.isPresent()) {
			final var parent = (AbstractLedgerWorldUpdater) parentUpdater.get();
			parent.manageInProgressRecord(recordsHistorian, childRecord, this.transactionBody);
		} else {
			throw new InvalidTransactionException("HTS precompile frame had no parent updater", FAIL_INVALID);
		}

		return result;
	}

	private void addContractCallResultToRecord(
			final ExpirableTxnRecord.Builder childRecord,
			final Bytes result,
			final Optional<ResponseCodeEnum> errorStatus,
			final MessageFrame messageFrame
	) {
		if (dynamicProperties.shouldExportPrecompileResults()) {
			final var evmFnResult = new EvmFnResult(
					HTS_PRECOMPILE_MIRROR_ENTITY_ID,
					result != null ? result.toArrayUnsafe() : EvmFnResult.EMPTY,
					errorStatus.map(ResponseCodeEnum::name).orElse(null),
					EvmFnResult.EMPTY,
					this.gasRequirement.toLong(),
					Collections.emptyList(),
					Collections.emptyList(),
					EvmFnResult.EMPTY,
					Collections.emptyMap(),
					precompile.shouldAddTraceabilityFieldsToRecord() ? messageFrame.getRemainingGas().toLong() : 0L,
					precompile.shouldAddTraceabilityFieldsToRecord() ? messageFrame.getValue().toLong() : 0L,
					precompile.shouldAddTraceabilityFieldsToRecord() ? messageFrame.getInputData().toArrayUnsafe() :
							EvmFnResult.EMPTY,
					EntityId.fromAddress(senderAddress));
			childRecord.setContractCallResult(evmFnResult);
		}
	}

	/* --- Constructor functional interfaces for mocking --- */
	@FunctionalInterface
	interface MintLogicFactory {
		MintLogic newMintLogic(
				OptionValidator validator,
				TypedTokenStore tokenStore,
				AccountStore accountStore,
				GlobalDynamicProperties dynamicProperties);
	}

	@FunctionalInterface
	interface CreateLogicFactory {
		CreateLogic newTokenCreateLogic(
				AccountStore accountStore,
				TypedTokenStore tokenStore,
				GlobalDynamicProperties dynamicProperties,
				SigImpactHistorian sigImpactHistorian,
				EntityIdSource entityIdSource,
				OptionValidator validator);
	}

	@FunctionalInterface
	interface BurnLogicFactory {
		BurnLogic newBurnLogic(
				OptionValidator validator,
				TypedTokenStore tokenStore,
				AccountStore accountStore,
				GlobalDynamicProperties dynamicProperties);
	}

	@FunctionalInterface
	interface AssociateLogicFactory {
		AssociateLogic newAssociateLogic(
				final TypedTokenStore tokenStore,
				final AccountStore accountStore,
				final GlobalDynamicProperties dynamicProperties);
	}

	@FunctionalInterface
	interface DissociateLogicFactory {
		DissociateLogic newDissociateLogic(
				OptionValidator validator,
				TypedTokenStore tokenStore,
				AccountStore accountStore,
				DissociationFactory dissociationFactory);
	}

	@FunctionalInterface
	interface ApproveAllowanceLogicFactory {
		ApproveAllowanceLogic newApproveAllowanceLogic(
				final AccountStore accountStore,
				final TypedTokenStore tokenStore,
				final GlobalDynamicProperties dynamicProperties);
	}

	@FunctionalInterface
	interface DeleteAllowanceLogicFactory {
		DeleteAllowanceLogic newDeleteAllowanceLogic(
				final AccountStore accountStore,
				final TypedTokenStore tokenStore);
	}

	@FunctionalInterface
	interface TransferLogicFactory {
		TransferLogic newLogic(
				TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger,
				TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger,
				TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger,
				HederaTokenStore tokenStore,
				SideEffectsTracker sideEffectsTracker,
				GlobalDynamicProperties dynamicProperties,
				OptionValidator validator,
				AutoCreationLogic autoCreationLogic,
				RecordsHistorian recordsHistorian);
	}

	@FunctionalInterface
	interface AccountStoreFactory {
		AccountStore newAccountStore(
				OptionValidator validator,
				BackingStore<AccountID, MerkleAccount> accounts);
	}

	@FunctionalInterface
	interface TokenStoreFactory {
		TypedTokenStore newTokenStore(
				AccountStore accountStore,
				BackingStore<TokenID, MerkleToken> tokens,
				BackingStore<NftId, MerkleUniqueToken> uniqueTokens,
				BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> tokenRels,
				SideEffectsTracker sideEffectsTracker);
	}

	@FunctionalInterface
	interface HederaTokenStoreFactory {
		HederaTokenStore newHederaTokenStore(
				EntityIdSource ids,
				OptionValidator validator,
				SideEffectsTracker sideEffectsTracker,
				GlobalDynamicProperties properties,
				TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger,
				TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger,
				BackingStore<TokenID, MerkleToken> backingTokens);
	}

	@FunctionalInterface
	interface RedirectExecutorFactory {
		RedirectViewExecutor newRedirectExecutor(
				Bytes input,
				MessageFrame frame,
				EncodingFacade encoder,
				DecodingFacade decoder,
				RedirectGasCalculator gasCalculator);
	}

	private abstract class AbstractAssociatePrecompile implements Precompile {
		protected Association associateOp;

		@Override
		public void run(final MessageFrame frame) {
			Objects.requireNonNull(associateOp);

			/* --- Check required signatures --- */
			final var accountId = Id.fromGrpcAccount(associateOp.accountId());
			final var hasRequiredSigs = validateKey(frame, accountId.asEvmAddress(), sigsVerifier::hasActiveKey);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var accountStore = createAccountStore();
			final var tokenStore = createTokenStore(accountStore, sideEffectsTracker);

			/* --- Execute the transaction and capture its results --- */
			final var associateLogic = associateLogicFactory.newAssociateLogic(tokenStore, accountStore,
					dynamicProperties);
			final var validity = associateLogic.validateSyntax(transactionBody.build());
			validateTrue(validity == OK, validity);
			associateLogic.associate(accountId, associateOp.tokenIds());
		}

		@Override
		public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
			return precompilePricingUtils.getMinimumPriceInTinybars(ASSOCIATE, consensusTime);
		}
	}

	protected class AssociatePrecompile extends AbstractAssociatePrecompile {
		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			associateOp = decoder.decodeAssociation(input, aliasResolver);
			return syntheticTxnFactory.createAssociate(associateOp);
		}
	}

	protected class MultiAssociatePrecompile extends AbstractAssociatePrecompile {
		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			associateOp = decoder.decodeMultipleAssociations(input, aliasResolver);
			return syntheticTxnFactory.createAssociate(associateOp);
		}
	}

	private abstract class AbstractDissociatePrecompile implements Precompile {
		protected Dissociation dissociateOp;

		@Override
		public void run(
				final MessageFrame frame
		) {
			Objects.requireNonNull(dissociateOp);

			/* --- Check required signatures --- */
			final var accountId = Id.fromGrpcAccount(dissociateOp.accountId());
			final var hasRequiredSigs = validateKey(frame, accountId.asEvmAddress(), sigsVerifier::hasActiveKey);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var accountStore = createAccountStore();
			final var tokenStore = createTokenStore(accountStore, sideEffectsTracker);

			/* --- Execute the transaction and capture its results --- */
			final var dissociateLogic = dissociateLogicFactory.newDissociateLogic(
					validator, tokenStore, accountStore, dissociationFactory);
			final var validity = dissociateLogic.validateSyntax(transactionBody.build());
			validateTrue(validity == OK, validity);
			dissociateLogic.dissociate(accountId, dissociateOp.tokenIds());
		}

		@Override
		public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
			return precompilePricingUtils.getMinimumPriceInTinybars(DISSOCIATE, consensusTime);
		}
	}

	protected class DissociatePrecompile extends AbstractDissociatePrecompile {
		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			dissociateOp = decoder.decodeDissociate(input, aliasResolver);
			return syntheticTxnFactory.createDissociate(dissociateOp);
		}
	}

	protected class MultiDissociatePrecompile extends AbstractDissociatePrecompile {
		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			dissociateOp = decoder.decodeMultipleDissociations(input, aliasResolver);
			return syntheticTxnFactory.createDissociate(dissociateOp);
		}
	}

	protected class MintPrecompile implements Precompile {
		private MintWrapper mintOp;

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			mintOp = decoder.decodeMint(input);
			return syntheticTxnFactory.createMint(mintOp);
		}

		@Override
		public void run(final MessageFrame frame) {
			Objects.requireNonNull(mintOp);

			/* --- Check required signatures --- */
			final var tokenId = Id.fromGrpcToken(mintOp.tokenType());
			final var hasRequiredSigs = validateKey(frame, tokenId.asEvmAddress(), sigsVerifier::hasActiveSupplyKey);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var scopedAccountStore = createAccountStore();
			final var scopedTokenStore = createTokenStore(scopedAccountStore, sideEffectsTracker);
			final var mintLogic = mintLogicFactory.newMintLogic(
					validator, scopedTokenStore, scopedAccountStore, dynamicProperties);

			final var validity = mintLogic.validateSyntax(transactionBody.build());
			validateTrue(validity == OK, validity);
			/* --- Execute the transaction and capture its results --- */
			if (mintOp.type() == NON_FUNGIBLE_UNIQUE) {
				final var newMeta = mintOp.metadata();
				final var creationTime = recordsHistorian.nextFollowingChildConsensusTime();
				mintLogic.mint(tokenId, newMeta.size(), 0, newMeta, creationTime);
			} else {
				mintLogic.mint(tokenId, 0, mintOp.amount(), NO_METADATA, Instant.EPOCH);
			}
		}

		@Override
		public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
			Objects.requireNonNull(mintOp);

			return precompilePricingUtils.getMinimumPriceInTinybars(
					(mintOp.type() == NON_FUNGIBLE_UNIQUE) ? MINT_NFT : MINT_FUNGIBLE, consensusTime);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final var receiptBuilder = childRecord.getReceiptBuilder();
			validateTrue(receiptBuilder != null, FAIL_INVALID);
			return encoder.encodeMintSuccess(
					childRecord.getReceiptBuilder().getNewTotalSupply(),
					childRecord.getReceiptBuilder().getSerialNumbers());
		}

		@Override
		public Bytes getFailureResultFor(final ResponseCodeEnum status) {
			return encoder.encodeMintFailure(status);
		}
	}

	/**
	 * Executes the logic of creating a token from {@link HTSPrecompiledContract}.
	 *
	 * <p>When a token create call is received, the execution can follow one of the following 4 scenarios:</p>
	 * <ol>
	 *     <li>The calling smart contract has sent a corrupt input to the {@link HTSPrecompiledContract} (e.g.
	 *     passing random bytes through the {@code encode()} Solidity method), which cannot be decoded to a valid
	 *     {@link TokenCreateWrapper}.
	 *     		<ul>
	 *     		 	<li><b>result</b> - the {@link DecodingFacade} throws an exception and null is returned
	 *     		 	from the {@link HTSPrecompiledContract}, setting the message frame's revert reason to the
	 *                {@code ERROR_DECODING_INPUT_REVERT_REASON} constant
	 *     		 	</li>
	 *     		 	<li><b>gas cost</b> - the current value returned from {@code dynamicProperties.htsDefaultGasCost()}
	 *     		 	<li><b>hbar cost</b> - all sent HBars are refunded to the frame sender
	 *     		 	</li>
	 *     		</ul>
	 *     	</li>
	 *     <li>The decoding succeeds, we create a valid {@link TokenCreateWrapper}, but we cannot translate it to a
	 *     valid token create {@link TransactionBody}. This comes from <b>difference in the design of the Solidity
	 *     function interface and the HAPI (protobufs)</b>
	 *     		<ul>
	 *     		 	<li><b>result</b> - {@link MessageFrame}'s revertReason is set to the
	 *                {@code ERROR_DECODING_INPUT_REVERT_REASON} constant and null is returned from the
	 *                {@link HTSPrecompiledContract}</li>
	 * 	    		<li><b>gas cost</b> - the current value returned from {@code dynamicProperties.htsDefaultGasCost()}
	 * 	    		<li><b>hbar cost</b> - all sent HBars are refunded to the frame sender
	 *     		</ul>
	 *     	</li>
	 *     <li>The decoding succeeds, we create a valid {@link TokenCreateWrapper}, we successfully translate it to a
	 *     valid token create {@link TransactionBody}. However, the {@link CreateChecks} validations find an input
	 *     error.
	 *     		<ul>
	 *     		 	<li><b>result</b> - a child {@link ExpirableTxnRecord} is created, containing the error response
	 *     		 	code. (from the point of view of the EVM this is a successful precompile call, however, from a
	 *     		 	Hedera's perspective there has been a problem during the execution)
	 *     		 	</li>
	 * 	    		<li><b>gas cost</b> - 100 000 gas</li>
	 * 	    		<li><b>hbar cost</b> - the HBars needed for the token creation are charged from the
	 * 	    		frame sender address (any excess HBars are refunded)
	 * 	    		</li>
	 *     		</ul>
	 *     	</li>
	 *     <li>The decoding succeeds, we create a valid {@link TokenCreateWrapper}, we successfully translate it to a
	 *     valid token create {@link TransactionBody}, the {@link CreateChecks} token create validations pass and the
	 *     whole execution flow succeeds.
	 *     		<ul>
	 *     		 	<li><b>result</b> - a child {@link ExpirableTxnRecord} is created, containing the successful
	 *     		 	response code and the ID of the newly created token.</li>
	 * 	    		<li><b>gas cost</b> - 100 000 gas</li>
	 * 	    		<li><b>hbar cost</b> - the HBars needed for the token creation are charged from the
	 * 	    		frame sender address (any excess HBars are refunded)
	 *     		</ul>
	 *     	</li>
	 * </ol>
	 */
	protected class TokenCreatePrecompile implements Precompile {
		protected TokenCreateWrapper tokenCreateOp;

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			tokenCreateOp = switch (functionId) {
				case ABI_ID_CREATE_FUNGIBLE_TOKEN -> decoder.decodeFungibleCreate(input, aliasResolver);
				case ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES -> decoder.decodeFungibleCreateWithFees(input,
						aliasResolver);
				case ABI_ID_CREATE_NON_FUNGIBLE_TOKEN -> decoder.decodeNonFungibleCreate(input, aliasResolver);
				case ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES -> decoder.decodeNonFungibleCreateWithFees(input,
						aliasResolver);
				default -> null;
			};

			/* --- Validate Solidity input and massage it to be able to transform it to tokenCreateTxnBody --- */
			verifySolidityInput();
			try {
				replaceInheritedProperties();
			} catch (DecoderException e) {
				throw new InvalidTransactionException(FAIL_INVALID);
			}

			return syntheticTxnFactory.createTokenCreate(tokenCreateOp);
		}

		@Override
		public void run(final MessageFrame frame) {
			Objects.requireNonNull(tokenCreateOp);

			/* --- Validate the synthetic create txn body before proceeding with the rest of the execution --- */
			final var creationTime = recordsHistorian.nextFollowingChildConsensusTime();
			final var result = tokenCreateChecks.validatorForConsTime(creationTime).apply(transactionBody.build());
			validateTrue(result == OK, result);

			/* --- Check required signatures --- */
			final var treasuryId = Id.fromGrpcAccount(tokenCreateOp.getTreasury());
			final var treasuryHasSigned = validateKey(frame, treasuryId.asEvmAddress(), sigsVerifier::hasActiveKey);
			validateTrue(treasuryHasSigned, INVALID_SIGNATURE);
			tokenCreateOp.getAdminKey().ifPresent(key -> validateTrue(validateAdminKey(frame, key), INVALID_SIGNATURE));

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var scopedAccountStore = createAccountStore();
			final var scopedTokenStore = createTokenStore(scopedAccountStore, sideEffectsTracker);
			final var tokenCreateLogic = createLogicFactory.newTokenCreateLogic(
					scopedAccountStore, scopedTokenStore,
					dynamicProperties, sigImpactHistorian, entityIdSource, validator);

			/* --- Execute the transaction and capture its results --- */
			tokenCreateLogic.create(
					creationTime.getEpochSecond(),
					EntityIdUtils.accountIdFromEvmAddress(senderAddress),
					transactionBody.getTokenCreation());
		}

		@Override
		public void customizeTrackingLedgers(final WorldLedgers worldLedgers) {
			worldLedgers.customizeForAutoAssociatingOp(sideEffectsTracker);
		}

		@Override
		public void handleSentHbars(final MessageFrame frame) {
			final var timestampSeconds = frame.getBlockValues().getTimestamp();
			final var timestamp = Timestamp.newBuilder().setSeconds(timestampSeconds).build();
			final var gasPriceInTinybars = feeCalculator.get()
					.estimatedGasPriceInTinybars(ContractCall, timestamp);
			final var calculatedFeeInTinybars = gasFeeInTinybars(
					transactionBody.setTransactionID(TransactionID.newBuilder().setTransactionValidStart(
							timestamp).build()), Instant.ofEpochSecond(timestampSeconds));

			final var tinybarsRequirement = calculatedFeeInTinybars + (calculatedFeeInTinybars / 5)
					- precompile.getMinimumFeeInTinybars(timestamp) * gasPriceInTinybars;

			validateTrue(frame.getValue().greaterOrEqualThan(Wei.of(tinybarsRequirement)), INSUFFICIENT_TX_FEE);

			updater.getAccount(senderAddress).getMutable()
					.decrementBalance(Wei.of(tinybarsRequirement));
			updater.getAccount(Id.fromGrpcAccount(dynamicProperties.fundingAccount()).asEvmAddress()).getMutable()
					.incrementBalance(Wei.of(tinybarsRequirement));
		}

		/* --- Due to differences in Solidity and protobuf interfaces, perform custom checks on the input  --- */
		private void verifySolidityInput() {
			/*
			 * Verify initial supply and decimals fall withing the allowed ranges of the types
			 * they convert to (long and int, respectively), since in the Solidity interface
			 * they are specified as uint256s and illegal values may be passed as input.
			 */
			if (tokenCreateOp.isFungible()) {
				validateTrue(tokenCreateOp.getInitSupply().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) < 1,
						INVALID_TRANSACTION_BODY);
				validateTrue(tokenCreateOp.getDecimals().compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) < 1,
						INVALID_TRANSACTION_BODY);
			}

			/*
			 * Check keys validity. The `TokenKey` struct in `IHederaTokenService.sol`
			 * defines a `keyType` bit field, which smart contract developers will use to
			 * set the type of key the `KeyValue` field will be used for. For example, if the
			 * `keyType` field is set to `00000001`, then the key value will be used for adminKey.
			 * If it is set to `00000011` the key value will be used for both adminKey and kycKey.
			 * Since an array of `TokenKey` structs is passed to the precompile, we have to
			 * check if each one specifies the type of key it applies to (that the bit field
			 * is not `00000000` and no bit bigger than 6 is set) and also that there are not multiple
			 * keys values for the same key type (e.g. multiple `TokenKey` instances have the adminKey bit set)
			 */
			final var tokenKeys = tokenCreateOp.getTokenKeys();
			if (!tokenKeys.isEmpty()) {
				for (int i = 0, tokenKeysSize = tokenKeys.size(); i < tokenKeysSize; i++) {
					final var tokenKey = tokenKeys.get(i);
					validateTrue(tokenKey.key().getKeyValueType() != INVALID_KEY, INVALID_TRANSACTION_BODY);
					final var tokenKeyBitField = tokenKey.keyType();
					validateTrue(tokenKeyBitField != 0 && tokenKeyBitField < 128, INVALID_TRANSACTION_BODY);
					for (int j = i + 1; j < tokenKeysSize; j++) {
						validateTrue((tokenKeyBitField & tokenKeys.get(j).keyType()) == 0,
								INVALID_TRANSACTION_BODY);
					}
				}
			}

			/*
			 * The denomination of a fixed fee depends on the values of tokenId, useHbarsForPayment
			 * useCurrentTokenForPayment. Exactly one of the values of the struct should be set.
			 */
			if (!tokenCreateOp.getFixedFees().isEmpty()) {
				for (final var fixedFee : tokenCreateOp.getFixedFees()) {
					validateTrue(fixedFee.getFixedFeePayment() != INVALID_PAYMENT, INVALID_TRANSACTION_BODY);
				}
			}

			/*
			 * When a royalty fee with fallback fee is specified, we need to check that
			 * the fallback fixed fee is valid.
			 */
			if (!tokenCreateOp.getRoyaltyFees().isEmpty()) {
				for (final var royaltyFee : tokenCreateOp.getRoyaltyFees()) {
					if (royaltyFee.fallbackFixedFee() != null) {
						validateTrue(royaltyFee.fallbackFixedFee().getFixedFeePayment() != INVALID_PAYMENT,
								INVALID_TRANSACTION_BODY);
					}
				}
			}
		}

		private void replaceInheritedKeysWithSenderKey(AccountID parentId) throws DecoderException {
			tokenCreateOp.setAllInheritedKeysTo((JKey) ledgers.accounts().get(parentId, AccountProperty.KEY));
		}

		private void replaceInheritedProperties() throws DecoderException {
			final var parentId = EntityIdUtils.accountIdFromEvmAddress(senderAddress);
			final var parentAutoRenewId = (EntityId) ledgers.accounts().get(parentId, AUTO_RENEW_ACCOUNT_ID);
			if (!MISSING_ENTITY_ID.equals(parentAutoRenewId) && !tokenCreateOp.hasAutoRenewAccount()) {
				tokenCreateOp.inheritAutoRenewAccount(parentAutoRenewId);
			}
			replaceInheritedKeysWithSenderKey(parentId);
		}

		private boolean validateAdminKey(
				final MessageFrame frame,
				final TokenCreateWrapper.TokenKeyWrapper tokenKeyWrapper
		) {
			final var key = tokenKeyWrapper.key();
			return switch (key.getKeyValueType()) {
				case INHERIT_ACCOUNT_KEY -> validateKey(frame, senderAddress, sigsVerifier::hasActiveKey);
				case CONTRACT_ID -> validateKey(frame, asTypedEvmAddress(key.getContractID()),
						sigsVerifier::hasActiveKey);
				case DELEGATABLE_CONTRACT_ID -> validateKey(frame, asTypedEvmAddress(key.getDelegatableContractID()),
						sigsVerifier::hasActiveKey);
				case ED25519 -> validateCryptoKey(new JEd25519Key(key.getEd25519Key()),
						sigsVerifier::cryptoKeyIsActive);
				case ECDSA_SECPK256K1 -> validateCryptoKey(new JECDSASecp256k1Key(key.getEcdsaSecp256k1()),
						sigsVerifier::cryptoKeyIsActive);
				default -> false;
			};
		}

		private boolean validateCryptoKey(final JKey key, final Predicate<JKey> keyActiveTest) {
			return keyActiveTest.test(key);
		}

		@Override
		public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
			return 100_000L;
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final var receiptBuilder = childRecord.getReceiptBuilder();
			validateTrue(receiptBuilder != null, FAIL_INVALID);
			return encoder.encodeCreateSuccess(
					asTypedEvmAddress(childRecord.getReceiptBuilder().getTokenId().toGrpcTokenId()));
		}

		@Override
		public Bytes getFailureResultFor(final ResponseCodeEnum status) {
			return encoder.encodeCreateFailure(status);
		}
	}

	protected class TransferPrecompile implements Precompile {
		private ResponseCodeEnum impliedValidity;
		private ImpliedTransfers impliedTransfers;
		private List<BalanceChange> explicitChanges;
		private TransactionBody.Builder syntheticTxn;
		protected List<TokenTransferWrapper> transferOp;
		protected HederaTokenStore hederaTokenStore;

		protected void initializeHederaTokenStore() {
			this.hederaTokenStore = hederaTokenStoreFactory.newHederaTokenStore(
					ids,
					validator,
					sideEffectsTracker,
					dynamicProperties,
					ledgers.tokenRels(), ledgers.nfts(), ledgers.tokens());
		}

		@Override
		public void customizeTrackingLedgers(final WorldLedgers worldLedgers) {
			worldLedgers.customizeForAutoAssociatingOp(sideEffectsTracker);
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			transferOp = switch (functionId) {
				case ABI_ID_CRYPTO_TRANSFER -> decoder.decodeCryptoTransfer(input, aliasResolver);
				case ABI_ID_TRANSFER_TOKENS -> decoder.decodeTransferTokens(input, aliasResolver);
				case ABI_ID_TRANSFER_TOKEN -> decoder.decodeTransferToken(input, aliasResolver);
				case ABI_ID_TRANSFER_NFTS -> decoder.decodeTransferNFTs(input, aliasResolver);
				case ABI_ID_TRANSFER_NFT -> decoder.decodeTransferNFT(input, aliasResolver);
				default -> null;
			};
			syntheticTxn = syntheticTxnFactory.createCryptoTransfer(transferOp);
			extrapolateDetailsFromSyntheticTxn();

			initializeHederaTokenStore();
			return syntheticTxn;
		}

		@Override
		public void addImplicitCostsIn(final TxnAccessor accessor) {
			if (impliedTransfers != null) {
				reCalculateXferMeta(accessor, impliedTransfers);
			}
		}

		@Override
		public void run(
				final MessageFrame frame
		) {
			if (impliedValidity == null) {
				extrapolateDetailsFromSyntheticTxn();
			}
			if (impliedValidity != OK) {
				throw new InvalidTransactionException(impliedValidity);
			}

			/* We remember this size to know to ignore receiverSigRequired=true for custom fee payments */
			final var numExplicitChanges = explicitChanges.size();
			final var assessmentStatus = impliedTransfers.getMeta().code();
			validateTrue(assessmentStatus == OK, assessmentStatus);
			var changes = impliedTransfers.getAllBalanceChanges();

			hederaTokenStore.setAccountsLedger(ledgers.accounts());

			final var transferLogic = transferLogicFactory.newLogic(
					ledgers.accounts(), ledgers.nfts(), ledgers.tokenRels(), hederaTokenStore,
					sideEffectsTracker,
					dynamicProperties,
					validator,
					null,
					recordsHistorian);

			for (int i = 0, n = changes.size(); i < n; i++) {
				final var change = changes.get(i);
				final var units = change.getAggregatedUnits();
				if (change.isForNft() || units < 0) {
					if (change.isApprovedAllowance()) {
						// Signing requirements are skipped for changes to be authorized via an allowance
						continue;
					}
					final var hasSenderSig = validateKey(
							frame, change.getAccount().asEvmAddress(), sigsVerifier::hasActiveKey);
					validateTrue(hasSenderSig, INVALID_SIGNATURE);
				}
				if (i >= numExplicitChanges) {
					// Ignore receiver sig requirements for custom fee payments (which are never NFT transfers)
					continue;
				}
				var hasReceiverSigIfReq = true;
				if (change.isForNft()) {
					final var counterPartyAddress = asTypedEvmAddress(change.counterPartyAccountId());
					hasReceiverSigIfReq = validateKey(frame, counterPartyAddress,
							sigsVerifier::hasActiveKeyOrNoReceiverSigReq);
				} else if (units > 0) {
					hasReceiverSigIfReq = validateKey(frame, change.getAccount().asEvmAddress(),
							sigsVerifier::hasActiveKeyOrNoReceiverSigReq);
				}
				validateTrue(hasReceiverSigIfReq, INVALID_SIGNATURE);
			}

			transferLogic.doZeroSum(changes);
		}

		@Override
		public List<FcAssessedCustomFee> getCustomFees() {
			return impliedTransfers.getAssessedCustomFees();
		}

		private void extrapolateDetailsFromSyntheticTxn() {
			final var op = syntheticTxn.getCryptoTransfer();
			impliedValidity = impliedTransfersMarshal.validityWithCurrentProps(op);
			if (impliedValidity != OK) {
				return;
			}
			explicitChanges = constructBalanceChanges(transferOp);
			impliedTransfers = impliedTransfersMarshal.assessCustomFeesAndValidate(
					0,
					0,
					explicitChanges,
					NO_ALIASES,
					impliedTransfersMarshal.currentProps());
		}

		private List<BalanceChange> constructBalanceChanges(final List<TokenTransferWrapper> transferOp) {
			final List<BalanceChange> allChanges = new ArrayList<>();
			for (final TokenTransferWrapper tokenTransferWrapper : transferOp) {
				final List<BalanceChange> changes = new ArrayList<>();

				for (final var fungibleTransfer : tokenTransferWrapper.fungibleTransfers()) {
					if (fungibleTransfer.sender != null && fungibleTransfer.receiver != null) {
						changes.addAll(List.of(
								BalanceChange.changingFtUnits(
										Id.fromGrpcToken(fungibleTransfer.getDenomination()),
										fungibleTransfer.getDenomination(),
										aaWith(fungibleTransfer.receiver, fungibleTransfer.amount,
												fungibleTransfer.isApproval),
										EntityIdUtils.accountIdFromEvmAddress(
												HTSPrecompiledContract.this.senderAddress)),
								BalanceChange.changingFtUnits(
										Id.fromGrpcToken(fungibleTransfer.getDenomination()),
										fungibleTransfer.getDenomination(),
										aaWith(fungibleTransfer.sender, -fungibleTransfer.amount,
												fungibleTransfer.isApproval),
										EntityIdUtils.accountIdFromEvmAddress(
												HTSPrecompiledContract.this.senderAddress))));
					} else if (fungibleTransfer.sender == null) {
						changes.add(
								BalanceChange.changingFtUnits(
										Id.fromGrpcToken(fungibleTransfer.getDenomination()),
										fungibleTransfer.getDenomination(),
										aaWith(fungibleTransfer.receiver, fungibleTransfer.amount,
												fungibleTransfer.isApproval),
										EntityIdUtils.accountIdFromEvmAddress(
												HTSPrecompiledContract.this.senderAddress)));
					} else {
						changes.add(
								BalanceChange.changingFtUnits(
										Id.fromGrpcToken(fungibleTransfer.getDenomination()),
										fungibleTransfer.getDenomination(),
										aaWith(fungibleTransfer.sender, -fungibleTransfer.amount,
												fungibleTransfer.isApproval),
										EntityIdUtils.accountIdFromEvmAddress(
												HTSPrecompiledContract.this.senderAddress)));
					}
				}
				if (changes.isEmpty()) {
					for (final var nftExchange : tokenTransferWrapper.nftExchanges()) {
						changes.add(
								BalanceChange.changingNftOwnership(
										Id.fromGrpcToken(nftExchange.getTokenType()),
										nftExchange.getTokenType(),
										nftExchange.asGrpc(),
										EntityIdUtils.accountIdFromEvmAddress(HTSPrecompiledContract.this.senderAddress))
						);
					}
				}

				allChanges.addAll(changes);
			}
			return allChanges;
		}

		private AccountAmount aaWith(final AccountID account, final long amount, final boolean isApproval) {
			return AccountAmount.newBuilder()
					.setAccountID(account)
					.setAmount(amount)
					.setIsApproval(isApproval)
					.build();
		}

		@Override
		public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
			Objects.requireNonNull(transferOp);
			long accumulatedCost = 0;
			boolean customFees = impliedTransfers != null && !impliedTransfers.getAssessedCustomFees().isEmpty();
			// For fungible there are always at least two operations, so only charge half for each operation
			long nftHalfTxCost = precompilePricingUtils.getMinimumPriceInTinybars(
					customFees ? PrecompilePricingUtils.GasCostType.TRANSFER_FUNGIBLE_CUSTOM_FEES :
							PrecompilePricingUtils.GasCostType.TRANSFER_FUNGIBLE,
					consensusTime) / 2;
			// NFTs are atomic, one line can do it.
			long fungibleHalfTxCost = precompilePricingUtils.getMinimumPriceInTinybars(
					customFees ? PrecompilePricingUtils.GasCostType.TRANSFER_NFT_CUSTOM_FEES :
							PrecompilePricingUtils.GasCostType.TRANSFER_NFT,
					consensusTime);
			for (var transfer : transferOp) {
				accumulatedCost += transfer.fungibleTransfers().size() * fungibleHalfTxCost;
				accumulatedCost += transfer.nftExchanges().size() * nftHalfTxCost;
			}
			return accumulatedCost;
		}

	}

	protected class BurnPrecompile implements Precompile {
		private BurnWrapper burnOp;

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			burnOp = decoder.decodeBurn(input);
			return syntheticTxnFactory.createBurn(burnOp);
		}

		@Override
		public void run(final MessageFrame frame) {
			Objects.requireNonNull(burnOp);

			/* --- Check required signatures --- */
			final var tokenId = Id.fromGrpcToken(burnOp.tokenType());
			final var hasRequiredSigs = validateKey(
					frame, tokenId.asEvmAddress(), sigsVerifier::hasActiveSupplyKey);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var scopedAccountStore = createAccountStore();
			final var scopedTokenStore = createTokenStore(scopedAccountStore, sideEffectsTracker);
			final var burnLogic = burnLogicFactory.newBurnLogic(
					validator, scopedTokenStore, scopedAccountStore, dynamicProperties);
			final var validity = burnLogic.validateSyntax(transactionBody.build());
			validateTrue(validity == OK, validity);

			/* --- Execute the transaction and capture its results --- */
			if (burnOp.type() == NON_FUNGIBLE_UNIQUE) {
				final var targetSerialNos = burnOp.serialNos();
				burnLogic.burn(tokenId, 0, targetSerialNos);
			} else {
				burnLogic.burn(tokenId, burnOp.amount(), NO_SERIAL_NOS);
			}
		}

		@Override
		public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
			Objects.requireNonNull(burnOp);
			return precompilePricingUtils.getMinimumPriceInTinybars(
					(burnOp.type() == NON_FUNGIBLE_UNIQUE) ? MINT_NFT : MINT_FUNGIBLE, consensusTime);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final var receiptBuilder = childRecord.getReceiptBuilder();
			validateTrue(receiptBuilder != null, FAIL_INVALID);
			return encoder.encodeBurnSuccess(childRecord.getReceiptBuilder().getNewTotalSupply());
		}

		@Override
		public Bytes getFailureResultFor(final ResponseCodeEnum status) {
			return encoder.encodeBurnFailure(status);
		}
	}

	protected abstract class ERCReadOnlyAbstractPrecompile implements Precompile {
		protected TokenID tokenId;

		protected ERCReadOnlyAbstractPrecompile(final TokenID tokenId) {
			this.tokenId = tokenId;
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			return syntheticTxnFactory.createTransactionCall(1L, input);
		}

		@Override
		public void run(final MessageFrame frame) {
			// No changes to state to apply
		}

		@Override
		public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
			return 100;
		}

		@Override
		public boolean shouldAddTraceabilityFieldsToRecord() {
			return false;
		}
	}

	protected class ERCTransferPrecompile extends TransferPrecompile {
		private final TokenID tokenId;
		private final AccountID callerId;
		private final boolean isFungible;

		public ERCTransferPrecompile(final TokenID tokenId, final Address callerAddress, final boolean isFungible) {
			this.callerId = EntityIdUtils.accountIdFromEvmAddress(callerAddress);
			this.tokenId = tokenId;
			this.isFungible = isFungible;
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			super.initializeHederaTokenStore();

			final var nestedInput = input.slice(24);
			super.transferOp = switch (nestedInput.getInt(0)) {
				case ABI_ID_ERC_TRANSFER -> decoder.decodeERCTransfer(nestedInput, tokenId, callerId, aliasResolver);
				case ABI_ID_ERC_TRANSFER_FROM -> {
					final var operatorId = EntityId.fromGrpcAccountId(callerId);
					yield decoder.decodeERCTransferFrom(
							nestedInput, tokenId, isFungible, aliasResolver, ledgers, operatorId);
				}
				default -> null;
			};
			super.syntheticTxn = syntheticTxnFactory.createCryptoTransfer(transferOp);
			super.extrapolateDetailsFromSyntheticTxn();
			return super.syntheticTxn;
		}

		@Override
		public void run(final MessageFrame frame) {
			if (!isFungible) {
				final var nftExchange = transferOp.get(0).nftExchanges().get(0);
				final var nftId = NftId.fromGrpc(nftExchange.getTokenType(), nftExchange.getSerialNo());
				validateTrueOrRevert(ledgers.nfts().contains(nftId), INVALID_TOKEN_NFT_SERIAL_NUMBER);
			}
			try {
				super.run(frame);
			} catch (InvalidTransactionException e) {
				throw InvalidTransactionException.fromReverting(e.getResponseCode());
			}
			if (isFungible) {
				frame.addLog(getLogForFungibleTransfer());
			} else {
				frame.addLog(getLogForNftExchange());
			}
		}

		private Log getLogForFungibleTransfer() {
			final var fungibleTransfers = super.transferOp.get(0).fungibleTransfers();
			Address sender = null;
			Address receiver = null;
			BigInteger amount = BigInteger.ZERO;
			for (final var fungibleTransfer : fungibleTransfers) {
				if (fungibleTransfer.sender != null) {
					sender = asTypedEvmAddress(fungibleTransfer.sender);
				}
				if (fungibleTransfer.receiver != null) {
					receiver = asTypedEvmAddress(fungibleTransfer.receiver);
					amount = BigInteger.valueOf(fungibleTransfer.amount);
				}
			}

			return EncodingFacade.LogBuilder.logBuilder()
					.forLogger(HTSPrecompiledContract.TYPED_HTS_PRECOMPILED_CONTRACT_ADDRESS)
					.forEventSignature(TRANSFER_EVENT)
					.forIndexedArgument(sender)
					.forIndexedArgument(receiver)
					.forDataItem(amount)
					.build();
		}

		private Log getLogForNftExchange() {
			final var nftExchanges = super.transferOp.get(0).nftExchanges();
			final var nftExchange = nftExchanges.get(0).asGrpc();
			final var sender = asTypedEvmAddress(nftExchange.getSenderAccountID());
			final var receiver = asTypedEvmAddress(nftExchange.getReceiverAccountID());
			final var serialNumber = nftExchange.getSerialNumber();

			return EncodingFacade.LogBuilder.logBuilder()
					.forLogger(HTSPrecompiledContract.TYPED_HTS_PRECOMPILED_CONTRACT_ADDRESS)
					.forEventSignature(TRANSFER_EVENT)
					.forIndexedArgument(sender)
					.forIndexedArgument(receiver)
					.forIndexedArgument(serialNumber)
					.build();
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			if (isFungible) {
				return encoder.encodeEcFungibleTransfer(true);
			} else {
				return Bytes.EMPTY;
			}
		}
	}

	protected class NamePrecompile extends ERCReadOnlyAbstractPrecompile {
		public NamePrecompile(TokenID tokenId) {
			super(tokenId);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final var name = ledgers.nameOf(tokenId);
			return encoder.encodeName(name);
		}
	}

	protected class SymbolPrecompile extends ERCReadOnlyAbstractPrecompile {
		public SymbolPrecompile(final TokenID tokenId) {
			super(tokenId);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final var symbol = ledgers.symbolOf(tokenId);
			return encoder.encodeSymbol(symbol);
		}
	}

	protected class DecimalsPrecompile extends ERCReadOnlyAbstractPrecompile {
		public DecimalsPrecompile(final TokenID tokenId) {
			super(tokenId);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final var decimals = ledgers.decimalsOf(tokenId);
			return encoder.encodeDecimals(decimals);
		}
	}

	protected class TotalSupplyPrecompile extends ERCReadOnlyAbstractPrecompile {
		public TotalSupplyPrecompile(final TokenID tokenId) {
			super(tokenId);
		}

		@Override
		public Bytes getSuccessResultFor(ExpirableTxnRecord.Builder childRecord) {
			final var totalSupply = ledgers.totalSupplyOf(tokenId);
			return encoder.encodeTotalSupply(totalSupply);
		}
	}

	protected class TokenURIPrecompile extends ERCReadOnlyAbstractPrecompile {
		private NftId nftId;

		public TokenURIPrecompile(final TokenID tokenId) {
			super(tokenId);
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var wrapper = decoder.decodeTokenUriNFT(input.slice(24));
			nftId = new NftId(tokenId.getShardNum(), tokenId.getRealmNum(), tokenId.getTokenNum(), wrapper.serialNo());
			return super.body(input, aliasResolver);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final var metadata = ledgers.metadataOf(nftId);
			return encoder.encodeTokenUri(metadata);
		}
	}

	protected class OwnerOfPrecompile extends ERCReadOnlyAbstractPrecompile {
		private NftId nftId;

		public OwnerOfPrecompile(final TokenID tokenId) {
			super(tokenId);
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var wrapper = decoder.decodeOwnerOf(input.slice(24));
			nftId = new NftId(tokenId.getShardNum(), tokenId.getRealmNum(), tokenId.getTokenNum(), wrapper.serialNo());
			return super.body(input, aliasResolver);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final var nftsLedger = ledgers.nfts();
			validateTrueOrRevert(nftsLedger.contains(nftId), INVALID_TOKEN_NFT_SERIAL_NUMBER);
			final var owner = ledgers.ownerOf(nftId);
			final var priorityAddress = ledgers.canonicalAddress(owner);
			return encoder.encodeOwner(priorityAddress);
		}
	}

	protected class BalanceOfPrecompile extends ERCReadOnlyAbstractPrecompile {
		private BalanceOfWrapper balanceWrapper;

		public BalanceOfPrecompile(final TokenID tokenId) {
			super(tokenId);
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var nestedInput = input.slice(24);
			balanceWrapper = decoder.decodeBalanceOf(nestedInput, aliasResolver);
			return super.body(input, aliasResolver);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final var balance = ledgers.balanceOf(balanceWrapper.accountId(), tokenId);
			return encoder.encodeBalance(balance);
		}
	}

	protected class AllowancePrecompile extends ERCReadOnlyAbstractPrecompile {
		private TokenAllowanceWrapper allowanceWrapper;

		public AllowancePrecompile(final TokenID tokenId) {
			super(tokenId);
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var nestedInput = input.slice(24);
			allowanceWrapper = decoder.decodeTokenAllowance(nestedInput, aliasResolver);

			return super.body(input, aliasResolver);
		}

		@Override
		@SuppressWarnings("unchecked")
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger = ledgers.accounts();
			validateTrueOrRevert(accountsLedger.contains(allowanceWrapper.owner()), INVALID_ALLOWANCE_OWNER_ID);
			final var allowances = (TreeMap<FcTokenAllowanceId, Long>) accountsLedger.get(
					allowanceWrapper.owner(), FUNGIBLE_TOKEN_ALLOWANCES);
			final var fcTokenAllowanceId = FcTokenAllowanceId.from(tokenId, allowanceWrapper.spender());
			final var value = allowances.getOrDefault(fcTokenAllowanceId, 0L);
			return encoder.encodeAllowance(value);
		}
	}

	protected class ApprovePrecompile implements Precompile {
		protected ApproveWrapper approveOp;
		@Nullable
		protected EntityId operatorId;
		@Nullable
		protected EntityId ownerId;
		protected TokenID tokenId;

		private final boolean isFungible;

		public ApprovePrecompile(final TokenID tokenId, final boolean isFungible) {
			this.tokenId = tokenId;
			this.isFungible = isFungible;
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var nestedInput = input.slice(24);
			operatorId = EntityId.fromAddress(senderAddress);
			approveOp = decoder.decodeTokenApprove(nestedInput, tokenId, isFungible, aliasResolver);
			if (isFungible) {
				return syntheticTxnFactory.createFungibleApproval(approveOp);
			} else {
				final var nftId = NftId.fromGrpc(tokenId, approveOp.serialNumber().longValue());
				ownerId = ledgers.ownerIfPresent(nftId);
				// Per the ERC-721 spec, "The zero address indicates there is no approved address"; so
				// translate this approveAllowance into a deleteAllowance
				if (isNftApprovalRevocation()) {
					final var nominalOwnerId = ownerId != null ? ownerId : MISSING_ENTITY_ID;
					return syntheticTxnFactory.createDeleteAllowance(approveOp, nominalOwnerId);
				} else {
					return syntheticTxnFactory.createNonfungibleApproval(approveOp, ownerId, operatorId);
				}
			}
		}

		@Override
		public void run(final MessageFrame frame) {
			Objects.requireNonNull(approveOp);

			validateTrueOrRevert(isFungible || ownerId != null, INVALID_TOKEN_NFT_SERIAL_NUMBER);
			final var grpcOperatorId = Objects.requireNonNull(operatorId).toGrpcAccountId();
			//  Per the ERC-721 spec, "Throws unless `msg.sender` is the current NFT owner, or
			//  an authorized operator of the current owner"
			if (!isFungible) {
				final var isApproved = operatorId.equals(ownerId) ||
						ledgers.hasApprovedForAll(ownerId.toGrpcAccountId(), grpcOperatorId, tokenId);
				validateTrueOrRevert(isApproved, SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
			}

			// --- Build the necessary infrastructure to execute the transaction ---
			final var accountStore = createAccountStore();
			final var tokenStore = createTokenStore(accountStore, sideEffectsTracker);
			final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(grpcOperatorId));
			// --- Execute the transaction and capture its results ---
			if (isNftApprovalRevocation()) {
				final var deleteAllowanceLogic =
						deleteAllowanceLogicFactory.newDeleteAllowanceLogic(accountStore, tokenStore);
				final var revocationOp = transactionBody.getCryptoDeleteAllowance();
				final var revocationWrapper = revocationOp.getNftAllowancesList();
				final var status = deleteAllowanceChecks.deleteAllowancesValidation(
						revocationWrapper, payerAccount, currentView);
				validateTrueOrRevert(status == OK, status);
				deleteAllowanceLogic.deleteAllowance(revocationWrapper, grpcOperatorId);
			} else {
				final var status = approveAllowanceChecks.allowancesValidation(
						transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
						transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
						transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
						payerAccount,
						currentView);
				validateTrueOrRevert(status == OK, status);
				final var approveAllowanceLogic = approveAllowanceLogicFactory.newApproveAllowanceLogic(
						accountStore, tokenStore, dynamicProperties);
				try {
					approveAllowanceLogic.approveAllowance(
							transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
							transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
							transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
							grpcOperatorId);
				} catch (InvalidTransactionException e) {
					throw InvalidTransactionException.fromReverting(e.getResponseCode());
				}
			}
			if (isFungible) {
				frame.addLog(getLogForFungibleAdjustAllowance());
			} else {
				frame.addLog(getLogForNftAdjustAllowance());
			}
		}

		@Override
		public long getMinimumFeeInTinybars(Timestamp consensusTime) {
			if (isNftApprovalRevocation()) {
				return precompilePricingUtils.getMinimumPriceInTinybars(DELETE_NFT_APPROVE, consensusTime);
			} else {
				return precompilePricingUtils.getMinimumPriceInTinybars(APPROVE, consensusTime);
			}
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			return encoder.encodeApprove(true);
		}

		private boolean isNftApprovalRevocation() {
			return Objects.requireNonNull(approveOp).spender().getAccountNum() == 0;
		}

		private Log getLogForFungibleAdjustAllowance() {
			return EncodingFacade.LogBuilder.logBuilder()
					.forLogger(HTSPrecompiledContract.TYPED_HTS_PRECOMPILED_CONTRACT_ADDRESS)
					.forEventSignature(APPROVAL_EVENT)
					.forIndexedArgument(senderAddress)
					.forIndexedArgument(asTypedEvmAddress(approveOp.spender()))
					.forDataItem(BigInteger.valueOf(approveOp.amount().longValue())).build();
		}

		private Log getLogForNftAdjustAllowance() {
			return EncodingFacade.LogBuilder.logBuilder()
					.forLogger(HTSPrecompiledContract.TYPED_HTS_PRECOMPILED_CONTRACT_ADDRESS)
					.forEventSignature(APPROVAL_EVENT)
					.forIndexedArgument(senderAddress)
					.forIndexedArgument(asTypedEvmAddress(approveOp.spender()))
					.forIndexedArgument(BigInteger.valueOf(approveOp.serialNumber().longValue())).build();
		}
	}

	protected class SetApprovalForAllPrecompile implements Precompile {
		protected TokenID tokenId;
		private SetApprovalForAllWrapper setApprovalForAllWrapper;

		public SetApprovalForAllPrecompile(final TokenID tokenId) {
			this.tokenId = tokenId;
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var nestedInput = input.slice(24);
			setApprovalForAllWrapper = decoder.decodeSetApprovalForAll(nestedInput, aliasResolver);
			return syntheticTxnFactory.createApproveAllowanceForAllNFT(setApprovalForAllWrapper, tokenId);
		}

		@Override
		public void run(MessageFrame frame) {
			Objects.requireNonNull(setApprovalForAllWrapper);
			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var accountStore = createAccountStore();
			final var tokenStore = createTokenStore(accountStore, sideEffectsTracker);
			final var payerAccount = accountStore.loadAccount(
					Id.fromGrpcAccount(EntityIdUtils.accountIdFromEvmAddress(senderAddress)));

			final var status = approveAllowanceChecks.allowancesValidation(
					transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
					transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
					transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
					payerAccount,
					currentView);
			validateTrueOrRevert(status == OK, status);

			/* --- Execute the transaction and capture its results --- */
			final var approveAllowanceLogic = approveAllowanceLogicFactory.newApproveAllowanceLogic(
					accountStore, tokenStore, dynamicProperties);
			approveAllowanceLogic.approveAllowance(transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
					transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
					transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
					EntityIdUtils.accountIdFromEvmAddress(frame.getSenderAddress()));

			frame.addLog(getLogForSetApprovalForAll());
		}

		@Override
		public long getMinimumFeeInTinybars(Timestamp consensusTime) {
			return precompilePricingUtils.getMinimumPriceInTinybars(APPROVE, consensusTime);
		}

		private Log getLogForSetApprovalForAll() {
			return EncodingFacade.LogBuilder.logBuilder()
					.forLogger(HTSPrecompiledContract.TYPED_HTS_PRECOMPILED_CONTRACT_ADDRESS)
					.forEventSignature(APPROVAL_FOR_ALL_EVENT)
					.forIndexedArgument(senderAddress)
					.forIndexedArgument(asTypedEvmAddress(setApprovalForAllWrapper.to()))
					.forDataItem(setApprovalForAllWrapper.approved()).build();
		}

	}

	protected class GetApprovedPrecompile extends ERCReadOnlyAbstractPrecompile {
		GetApprovedWrapper getApprovedWrapper;

		public GetApprovedPrecompile(final TokenID tokenId) {
			super(tokenId);
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var nestedInput = input.slice(24);
			getApprovedWrapper = decoder.decodeGetApproved(nestedInput);
			return super.body(input, aliasResolver);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final var nftsLedger = ledgers.nfts();
			final var nftId = NftId.fromGrpc(tokenId, getApprovedWrapper.serialNo());
			validateTrueOrRevert(nftsLedger.contains(nftId), INVALID_TOKEN_NFT_SERIAL_NUMBER);
			final var spender = (EntityId) nftsLedger.get(nftId, SPENDER);
			return encoder.encodeGetApproved(spender.toEvmAddress());
		}
	}

	protected class IsApprovedForAllPrecompile extends ERCReadOnlyAbstractPrecompile {
		private IsApproveForAllWrapper isApproveForAllWrapper;

		public IsApprovedForAllPrecompile(final TokenID tokenId) {
			super(tokenId);
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var nestedInput = input.slice(24);
			isApproveForAllWrapper = decoder.decodeIsApprovedForAll(nestedInput, aliasResolver);
			return super.body(input, aliasResolver);
		}

		@Override
		@SuppressWarnings("unchecked")
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final var accountsLedger = ledgers.accounts();
			var answer = true;
			final var ownerId = isApproveForAllWrapper.owner();
			answer &= accountsLedger.contains(ownerId);
			final var operatorId = isApproveForAllWrapper.operator();
			answer &= accountsLedger.contains(operatorId);
			if (answer) {
				final var allowances = (Set<FcTokenAllowanceId>) accountsLedger.get(
						ownerId, APPROVE_FOR_ALL_NFTS_ALLOWANCES);
				final var allowanceId = FcTokenAllowanceId.from(tokenId, operatorId);
				answer &= allowances.contains(allowanceId);
			}
			return encoder.encodeIsApprovedForAll(answer);
		}
	}

	/**
	 * Checks if a key implicit in a target address is active in the current frame using a {@link
	 * ContractActivationTest}.
	 * <p>
	 * We massage the current frame a bit to ensure that a precompile being executed via delegate call is tested as
	 * such.
	 * There are three cases.
	 * <ol>
	 *     <li>The precompile is being executed via a delegate call, so the current frame's <b>recipient</b>
	 *     (not sender) is really the "active" contract that can match a {@code delegatable_contract_id} key; or,
	 *     <li>The precompile is being executed via a call, but the calling code was executed via
	 *     a delegate call, so although the current frame's sender <b>is</b> the "active" contract, it must
	 *     be evaluated using an activation test that restricts to {@code delegatable_contract_id} keys; or,</li>
	 *     <li>The precompile is being executed via a call, and the calling code is being executed as
	 *     part of a non-delegate call.</li>
	 * </ol>
	 * <p>
	 * Note that because the {@link DecodingFacade} converts every address to its "mirror" address form
	 * (as needed for e.g. the {@link TransferLogic} implementation), we can assume the target address
	 * is a mirror address. All other addresses we resolve to their mirror form before proceeding.
	 *
	 * @param frame
	 * 		current frame
	 * @param target
	 * 		the element to test for key activation, in standard form
	 * @param activationTest
	 * 		the function which should be invoked for key validation
	 * @return whether the implied key is active
	 */
	private boolean validateKey(
			final MessageFrame frame,
			final Address target,
			final ContractActivationTest activationTest
	) {
		final var aliases = updater.aliases();
		final var recipient = aliases.resolveForEvm(frame.getRecipientAddress());
		final var sender = aliases.resolveForEvm(frame.getSenderAddress());

		if (isDelegateCall(frame) && !isToken(frame, recipient)) {
			return activationTest.apply(true, target, recipient, ledgers);
		} else {
			final var parentFrame = getParentFrame(frame);
			return activationTest.apply(parentFrame.isPresent() && isDelegateCall(parentFrame.get()), target, sender,
					ledgers);
		}
	}

	boolean isToken(final MessageFrame frame, final Address address) {
		final var account = frame.getWorldUpdater().get(address);
		if (account != null) {
			return account.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE;
		}
		return false;
	}


	@FunctionalInterface
	private interface ContractActivationTest {
		/**
		 * Returns whether a key implicit in the target address is active, given an idealized message
		 * frame in which:
		 * <ul>
		 * 	 <li>The {@code recipient} address is the account receiving the call operation; and,</li>
		 * 	 <li>The {@code contract} address is the account with the code being executed; and,</li>
		 * 	 <li>Any {@code ContractID} or {@code delegatable_contract_id} key that matches the
		 *     {@code activeContract} address should be considered active (modulo whether the recipient
		 * 	 and contract imply a delegate call).</li>
		 * </ul>
		 * <p>
		 * Note the target address might not imply an account key, but e.g. a token supply key.
		 *
		 * @param isDelegateCall
		 * 		a flag showing if the message represented by the active frame is invoked via {@code delegatecall}
		 * @param target
		 * 		an address with an implicit key understood by this implementation
		 * @param activeContract
		 * 		the contract address that can activate a contract or delegatable contract key
		 * @param worldLedgers
		 * 		the worldLedgers representing current state
		 * @return whether the implicit key has an active signature in this context
		 */
		boolean apply(
				boolean isDelegateCall,
				Address target,
				Address activeContract,
				WorldLedgers worldLedgers);
	}

	private Optional<MessageFrame> getParentFrame(final MessageFrame currentFrame) {
		final var it = currentFrame.getMessageFrameStack().descendingIterator();

		if (it.hasNext()) {
			it.next();
		} else {
			return Optional.empty();
		}

		MessageFrame parentFrame;
		if (it.hasNext()) {
			parentFrame = it.next();
		} else {
			return Optional.empty();
		}

		return Optional.of(parentFrame);
	}

	private boolean isDelegateCall(final MessageFrame frame) {
		final var contract = frame.getContractAddress();
		final var recipient = frame.getRecipientAddress();
		return !contract.equals(recipient);
	}

	private Gas defaultGas() {
		return Gas.of(dynamicProperties.htsDefaultGasCost());
	}

	private long gasFeeInTinybars(final TransactionBody.Builder txBody, final Instant consensusTime) {
		final var signedTxn = SignedTransaction.newBuilder()
				.setBodyBytes(txBody.build().toByteString())
				.setSigMap(SignatureMap.getDefaultInstance())
				.build();
		final var txn = Transaction.newBuilder()
				.setSignedTransactionBytes(signedTxn.toByteString())
				.build();

		final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
		precompile.addImplicitCostsIn(accessor);
		final var fees = feeCalculator.get().computeFee(accessor, EMPTY_KEY, currentView, consensusTime);
		return fees.getServiceFee() + fees.getNetworkFee() + fees.getNodeFee();
	}

	/* --- Only used by unit tests --- */
	void setCreateLogicFactory(final CreateLogicFactory createLogicFactory) {
		this.createLogicFactory = createLogicFactory;
	}

	void setMintLogicFactory(final MintLogicFactory mintLogicFactory) {
		this.mintLogicFactory = mintLogicFactory;
	}

	void setDissociateLogicFactory(final DissociateLogicFactory dissociateLogicFactory) {
		this.dissociateLogicFactory = dissociateLogicFactory;
	}

	public void setBurnLogicFactory(final BurnLogicFactory burnLogicFactory) {
		this.burnLogicFactory = burnLogicFactory;
	}

	void setTransferLogicFactory(final TransferLogicFactory transferLogicFactory) {
		this.transferLogicFactory = transferLogicFactory;
	}

	void setTokenStoreFactory(final TokenStoreFactory tokenStoreFactory) {
		this.tokenStoreFactory = tokenStoreFactory;
	}

	void setHederaTokenStoreFactory(final HederaTokenStoreFactory hederaTokenStoreFactory) {
		this.hederaTokenStoreFactory = hederaTokenStoreFactory;
	}

	void setAccountStoreFactory(final AccountStoreFactory accountStoreFactory) {
		this.accountStoreFactory = accountStoreFactory;
	}

	void setSideEffectsFactory(final Supplier<SideEffectsTracker> sideEffectsFactory) {
		this.sideEffectsFactory = sideEffectsFactory;
	}

	void setAssociateLogicFactory(final AssociateLogicFactory associateLogicFactory) {
		this.associateLogicFactory = associateLogicFactory;
	}

	@VisibleForTesting
	void setDeleteAllowanceLogicFactory(final DeleteAllowanceLogicFactory deleteAllowanceLogicFactory) {
		this.deleteAllowanceLogicFactory = deleteAllowanceLogicFactory;
	}

	@VisibleForTesting
	void setApproveAllowanceLogicFactory(final ApproveAllowanceLogicFactory approveAllowanceLogicFactory) {
		this.approveAllowanceLogicFactory = approveAllowanceLogicFactory;
	}

	@VisibleForTesting
	public Precompile getPrecompile() {
		return precompile;
	}

	@VisibleForTesting
	void setTokenReadOnlyTransaction(final boolean tokenReadOnlyTransaction) {
		isTokenReadOnlyTransaction = tokenReadOnlyTransaction;
	}

	@VisibleForTesting
	boolean isTokenReadOnlyTransaction() {
		return isTokenReadOnlyTransaction;
	}
}
