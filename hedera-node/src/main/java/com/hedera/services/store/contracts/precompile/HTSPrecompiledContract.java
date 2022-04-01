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
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.AccountRecordsHistorian;
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
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.crypto.AdjustAllowanceLogic;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.txns.crypto.validators.AdjustAllowanceChecks;
import com.hedera.services.txns.token.AssociateLogic;
import com.hedera.services.txns.token.BurnLogic;
import com.hedera.services.txns.token.DissociateLogic;
import com.hedera.services.txns.token.MintLogic;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.grpc.marshalling.ImpliedTransfers.NO_ALIASES;
import static com.hedera.services.ledger.backing.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.ids.ExceptionalEntityIdSource.NOOP_ID_SOURCE;
import static com.hedera.services.ledger.properties.AccountProperty.FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hedera.services.ledger.properties.AccountProperty.NFT_ALLOWANCES;
import static com.hedera.services.ledger.properties.NftProperty.METADATA;
import static com.hedera.services.ledger.properties.NftProperty.OWNER;
import static com.hedera.services.ledger.properties.TokenProperty.DECIMALS;
import static com.hedera.services.ledger.properties.TokenProperty.NAME;
import static com.hedera.services.ledger.properties.TokenProperty.SYMBOL;
import static com.hedera.services.ledger.properties.TokenProperty.TOKEN_TYPE;
import static com.hedera.services.ledger.properties.TokenProperty.TOTAL_SUPPLY;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.HederaWorldState.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;
import static com.hedera.services.store.contracts.precompile.PrecompilePricingUtils.GasCostType.ASSOCIATE;
import static com.hedera.services.store.contracts.precompile.PrecompilePricingUtils.GasCostType.DISSOCIATE;
import static com.hedera.services.store.contracts.precompile.PrecompilePricingUtils.GasCostType.MINT_FUNGIBLE;
import static com.hedera.services.store.contracts.precompile.PrecompilePricingUtils.GasCostType.MINT_NFT;
import static com.hedera.services.txns.span.SpanMapManager.reCalculateXferMeta;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

@Singleton
public class HTSPrecompiledContract extends AbstractPrecompiledContract {
	private static final Logger log = LogManager.getLogger(HTSPrecompiledContract.class);

	public static final String HTS_PRECOMPILED_CONTRACT_ADDRESS = "0x167";
	public static final ContractID HTS_PRECOMPILE_MIRROR_ID = contractIdFromEvmAddress(
			Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArrayUnsafe());
	public static final EntityId HTS_PRECOMPILE_MIRROR_ENTITY_ID = EntityId.fromGrpcContractId(HTS_PRECOMPILE_MIRROR_ID);

	private static final Bytes SUCCESS_RESULT = resultFrom(SUCCESS);
	private static final Bytes STATIC_CALL_REVERT_REASON = Bytes.of("HTS precompiles are not static".getBytes());
	private static final String NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON = "Invalid operation for ERC-20 token!";
	private static final String NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON = "Invalid operation for ERC-721 token!";
	private static final Bytes ERROR_DECODING_INPUT_REVERT_REASON = Bytes.of(
			"Error decoding precompile input".getBytes());
	private static final List<Long> NO_SERIAL_NOS = Collections.emptyList();
	private static final List<ByteString> NO_METADATA = Collections.emptyList();
	private static final List<FcAssessedCustomFee> NO_CUSTOM_FEES = Collections.emptyList();
	private static final EntityIdSource ids = NOOP_ID_SOURCE;
	private static final String URI_QUERY_NON_EXISTING_TOKEN_ERROR = "ERC721Metadata: URI query for nonexistent token";

	/* Precompiles cannot change treasury accounts */
	public static final TypedTokenStore.LegacyTreasuryAdder NOOP_TREASURY_ADDER = (aId, tId) -> {
	};
	public static final TypedTokenStore.LegacyTreasuryRemover NOOP_TREASURY_REMOVER = (aId, tId) -> {
	};

	private MintLogicFactory mintLogicFactory = MintLogic::new;
	private BurnLogicFactory burnLogicFactory = BurnLogic::new;
	private AssociateLogicFactory associateLogicFactory = AssociateLogic::new;
	private DissociateLogicFactory dissociateLogicFactory = DissociateLogic::new;
	private AdjustAllowanceLogicFactory adjustAllowanceLogicFactory = AdjustAllowanceLogic::new;
	private TransferLogicFactory transferLogicFactory = TransferLogic::new;
	private TokenStoreFactory tokenStoreFactory = TypedTokenStore::new;
	private HederaTokenStoreFactory hederaTokenStoreFactory = HederaTokenStore::new;
	private AccountStoreFactory accountStoreFactory = AccountStore::new;
	private Supplier<SideEffectsTracker> sideEffectsFactory = SideEffectsTracker::new;

	private final EntityCreator creator;
	private final DecodingFacade decoder;
	private final EncodingFacade encoder;
	private final GlobalDynamicProperties dynamicProperties;
	private final OptionValidator validator;
	private final EvmSigsVerifier sigsVerifier;
	private final AccountRecordsHistorian recordsHistorian;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final DissociationFactory dissociationFactory;
	private final UsagePricesProvider resourceCosts;

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
	protected static final int ABI_ID_NAME = 0x06fdde03;
	//symbol()
	protected static final int ABI_ID_SYMBOL = 0x95d89b41;
	//decimals()
	protected static final int ABI_ID_DECIMALS = 0x313ce567;
	//totalSupply()
	protected static final int ABI_ID_TOTAL_SUPPLY_TOKEN = 0x18160ddd;
	//balanceOf(address account)
	protected static final int ABI_ID_BALANCE_OF_TOKEN = 0x70a08231;
	//transfer(address recipient, uint256 amount)
	protected static final int ABI_ID_ERC_TRANSFER = 0xa9059cbb;
	//transferFrom(address sender, address recipient, uint256 amount)
	//transferFrom(address from, address to, uint256 tokenId)
	protected static final int ABI_ID_ERC_TRANSFER_FROM = 0x23b872dd;
	//allowance(address token, address owner, address spender)
	protected static final int ABI_ID_ALLOWANCE = -0x229d12c2;
	//approve(address token, address spender, uint256 amount)
	//approve(address token, address to, uint256 tokenId)
	protected static final int ABI_ID_APPROVE = 0x95ea7b3;
	//setApprovalForAll(address token, address operator, bool approved)
	protected static final int ABI_ID_SET_APPROVAL_FOR_ALL = -0x5dd34b9b;
	//getApproved(address token, uint256 tokenId)
	protected static final int ABI_ID_GET_APPROVED = 0x081812fc;
	//isApprovedForAll(address token, address owner, address operator)
	protected static final int ABI_ID_IS_APPROVED_FOR_ALL = -0x167a163b;
	//ownerOf(uint256 tokenId)
	protected static final int ABI_ID_OWNER_OF_NFT = 0x6352211e;
	//tokenURI(uint256 tokenId)
	protected static final int ABI_ID_TOKEN_URI_NFT = 0xc87b56dd;

	//Transfer(address indexed from, address indexed to, uint256 indexed tokenId)
	//Transfer(address indexed from, address indexed to, uint256 value)
	private static final Bytes TRANSFER_EVENT = Bytes.fromHexString(
			"ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
	//Approval(address indexed owner, address indexed spender, uint256 value)
	//Approval(address indexed owner, address indexed approved, uint256 indexed tokenId)
	private static final Bytes APPROVAL_EVENT = Bytes.fromHexString("8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925");
	//ApprovalForAll(address indexed owner, address indexed operator, bool approved)
	private static final Bytes APPROVAL_FOR_ALL_EVENT = Bytes.fromHexString("17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31");

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
	private AdjustAllowanceChecks allowanceChecks;

	@Inject
	public HTSPrecompiledContract(
			final OptionValidator validator,
			final GlobalDynamicProperties dynamicProperties,
			final GasCalculator gasCalculator,
			final AccountRecordsHistorian recordsHistorian,
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
			final AdjustAllowanceChecks allowanceChecks
			) {
		super("HTS", gasCalculator);
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
		this.allowanceChecks = allowanceChecks;
	}

	@Override
	public Gas gasRequirement(final Bytes bytes) {
		return gasRequirement;
	}

	@Override
	public Bytes compute(final Bytes input, final MessageFrame messageFrame) {
		boolean isRedirectProxy = ABI_ID_REDIRECT_FOR_TOKEN == input.getInt(0);

		if (messageFrame.isStatic() && !isRedirectProxy) {
			messageFrame.setRevertReason(STATIC_CALL_REVERT_REASON);
			return null;
		}

		prepareFields(messageFrame);
		final UnaryOperator<byte[]> aliasResolver = updater::unaliased;

		prepareComputation(input, aliasResolver);

		gasRequirement = Gas.of(dynamicProperties.htsDefaultGasCost());

		if (this.precompile == null || this.transactionBody == null) {
			messageFrame.setRevertReason(ERROR_DECODING_INPUT_REVERT_REASON);
			return null;
		}

		if (isTokenReadOnlyTransaction) {
			computeViewFunctionGasRequirement(messageFrame.getBlockValues().getTimestamp());
		} else {
			computeGasRequirement(messageFrame.getBlockValues().getTimestamp());
		}

		return computeInternal(messageFrame);
	}

	void prepareFields(final MessageFrame messageFrame) {
		this.updater = (HederaStackedWorldStateUpdater) messageFrame.getWorldUpdater();
		this.sideEffectsTracker = sideEffectsFactory.get();
		this.ledgers = updater.wrappedTrackingLedgers(sideEffectsTracker);

		final var unaliasedSenderAddress = updater.unaliased(messageFrame.getSenderAddress().toArray());
		if (unaliasedSenderAddress != null) {
			this.senderAddress = Address.wrap(Bytes.of(unaliasedSenderAddress));
			return;
		}

		this.senderAddress = messageFrame.getSenderAddress();
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
		final Timestamp timestamp = Timestamp.newBuilder().setSeconds(
				blockTimestamp).build();
		final var usagePrices = resourceCosts.defaultPricesGiven(HederaFunctionality.TokenGetInfo, timestamp);
		final var transactionGetRecordQuery = TransactionGetRecordQuery.newBuilder()
				.build();
		final var query = Query.newBuilder().setTransactionGetRecord(transactionGetRecordQuery);
		final var fee =
				feeCalculator.get().estimatePayment(query.buildPartial(), usagePrices, currentView, timestamp,
						ResponseType.ANSWER_ONLY);

		final long gasPriceInTinybars = feeCalculator.get().estimatedGasPriceInTinybars(ContractCall, timestamp);

		final long calculatedFeeInTinybars = fee.getNetworkFee() + fee.getNodeFee() + fee.getServiceFee();

		final long minimumFeeInTinybars = precompile.getMinimumFeeInTinybars(timestamp);
		final long actualFeeInTinybars = Math.max(minimumFeeInTinybars, calculatedFeeInTinybars);

		// convert to gas cost
		final Gas baseGasCost = Gas.of((actualFeeInTinybars + gasPriceInTinybars - 1) / gasPriceInTinybars);

		// charge premium
		gasRequirement = baseGasCost.plus((baseGasCost.dividedBy(5)));
	}

	void prepareComputation(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		this.precompile = null;
		this.transactionBody = null;

		this.functionId = input.getInt(0);
		this.gasRequirement = null;

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
						final var tokenAddress = input.slice(4, 20);
						final var tokenID = EntityIdUtils.tokenIdFromEvmAddress(tokenAddress.toArray());
						final var nestedInput = input.slice(24);
						final var tokensLedger = ledgers.tokens();
						final var isFungibleToken = TokenType.FUNGIBLE_COMMON.equals(tokensLedger.get(tokenID,
								TOKEN_TYPE));

						Precompile nestedPrecompile;
						this.isTokenReadOnlyTransaction = true;
						final var nestedFunctionSelector = nestedInput.getInt(0);

						if (ABI_ID_NAME == nestedFunctionSelector) {
							nestedPrecompile = new NamePrecompile(tokenID);
						} else if (ABI_ID_SYMBOL == nestedFunctionSelector) {
							nestedPrecompile = new SymbolPrecompile(tokenID);
						} else if (ABI_ID_DECIMALS == nestedFunctionSelector) {
							if (!isFungibleToken) {
								throw new InvalidTransactionException(NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON,
										FAIL_INVALID);
							}
							nestedPrecompile = new DecimalsPrecompile(tokenID);
						} else if (ABI_ID_TOTAL_SUPPLY_TOKEN == nestedFunctionSelector) {
							nestedPrecompile = new TotalSupplyPrecompile(tokenID);
						} else if (ABI_ID_BALANCE_OF_TOKEN == nestedFunctionSelector) {
							nestedPrecompile = new BalanceOfPrecompile(tokenID);
						} else if (ABI_ID_OWNER_OF_NFT == nestedFunctionSelector) {
							if (isFungibleToken) {
								throw new InvalidTransactionException(NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON,
										FAIL_INVALID);
							}
							nestedPrecompile = new OwnerOfPrecompile(tokenID);
						} else if (ABI_ID_TOKEN_URI_NFT == nestedFunctionSelector) {
							if (isFungibleToken) {
								throw new InvalidTransactionException(NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON,
										FAIL_INVALID);
							}
							nestedPrecompile = new TokenURIPrecompile(tokenID);
						} else if (ABI_ID_ERC_TRANSFER == nestedFunctionSelector) {
							this.isTokenReadOnlyTransaction = false;
							if (!isFungibleToken) {
								throw new InvalidTransactionException(NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON,
										FAIL_INVALID);
							}
							nestedPrecompile = new ERCTransferPrecompile(tokenID, this.senderAddress, isFungibleToken);
						} else if (ABI_ID_ERC_TRANSFER_FROM == nestedFunctionSelector) {
							this.isTokenReadOnlyTransaction = false;
							nestedPrecompile = new ERCTransferPrecompile(tokenID, this.senderAddress, isFungibleToken);
						} else if (ABI_ID_ALLOWANCE == nestedFunctionSelector) {
							nestedPrecompile = new AllowancePrecompile(tokenID);
						} else if (ABI_ID_APPROVE == nestedFunctionSelector) {
							this.isTokenReadOnlyTransaction = false;
							nestedPrecompile = new ApprovePrecompile(tokenID, isFungibleToken);
						} else if (ABI_ID_SET_APPROVAL_FOR_ALL == nestedFunctionSelector) {
							nestedPrecompile = new SetApprovalForAllPrecompile(tokenID);
						} else if (ABI_ID_GET_APPROVED == nestedFunctionSelector) {
							nestedPrecompile = new GetApprovedPrecompile(tokenID);
						} else if (ABI_ID_IS_APPROVED_FOR_ALL == nestedFunctionSelector) {
							nestedPrecompile = new IsApprovedForAllPrecompile(tokenID);
						} else {
							this.isTokenReadOnlyTransaction = false;
							nestedPrecompile = null;
						}

						yield nestedPrecompile;
					}
					default -> null;
				};
		if (precompile != null) {
			decodeInput(input, aliasResolver);
		}
	}

	/* --- Helpers --- */
	private AccountStore createAccountStore() {
		return accountStoreFactory.newAccountStore(validator, dynamicProperties, ledgers.accounts());
	}

	private TypedTokenStore createTokenStore(
			final AccountStore accountStore,
			final SideEffectsTracker sideEffects
	) {
		return tokenStoreFactory.newTokenStore(
				accountStore,
				ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels(),
				NOOP_TREASURY_ADDER, NOOP_TREASURY_REMOVER,
				sideEffects);
	}

	private static Bytes resultFrom(final ResponseCodeEnum status) {
		return UInt256.valueOf(status.getNumber());
	}

	void decodeInput(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		this.transactionBody = TransactionBody.newBuilder();
		try {
			this.transactionBody = this.precompile.body(input, aliasResolver);
		} catch (Exception e) {
			log.warn("Internal precompile failure", e);
			transactionBody = null;
		}
	}

	@SuppressWarnings("rawtypes")
	protected Bytes computeInternal(final MessageFrame frame) {
		Bytes result;
		ExpirableTxnRecord.Builder childRecord;
		try {
			validateTrue(frame.getRemainingGas().compareTo(gasRequirement) >= 0, INSUFFICIENT_GAS);

			precompile.run(frame);
			// As in HederaLedger.commit(), we must first commit the ledgers before creating our
			// synthetic record, as the ledger interceptors will populate the sideEffectsTracker
			ledgers.commit();

			childRecord = creator.createSuccessfulSyntheticRecord(
					precompile.getCustomFees(), sideEffectsTracker, EMPTY_MEMO);

			result = precompile.getSuccessResultFor(childRecord);
			addContractCallResultToRecord(childRecord, result, Optional.empty(), frame);
		} catch (InvalidTransactionException e) {
			final var status = e.getResponseCode();
			childRecord = creator.createUnsuccessfulSyntheticRecord(status);
			result = precompile.getFailureResultFor(status);
			addContractCallResultToRecord(childRecord, result, Optional.of(status), frame);
		} catch (Exception e) {
			log.warn("Internal precompile failure", e);
			childRecord = creator.createUnsuccessfulSyntheticRecord(FAIL_INVALID);
			result = precompile.getFailureResultFor(FAIL_INVALID);
			addContractCallResultToRecord(childRecord, result, Optional.of(FAIL_INVALID), frame);
		}

		/*-- The updater here should always have a parent updater --*/
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
							EvmFnResult.EMPTY);
			childRecord.setContractCallResult(evmFnResult);
		}
	}

	/* --- Constructor functional interfaces for mocking --- */
	@FunctionalInterface
	interface MintLogicFactory {
		MintLogic newMintLogic(OptionValidator validator, TypedTokenStore tokenStore, AccountStore accountStore);
	}

	@FunctionalInterface
	interface BurnLogicFactory {
		BurnLogic newBurnLogic(TypedTokenStore tokenStore, AccountStore accountStore);
	}

	@FunctionalInterface
	interface AssociateLogicFactory {
		AssociateLogic newAssociateLogic(
				final TypedTokenStore tokenStore,
				final AccountStore accountStore);
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
	interface AdjustAllowanceLogicFactory {
		AdjustAllowanceLogic newAdjustAllowanceLogic(
				AccountStore accountStore,
				GlobalDynamicProperties dynamicProperties,
				SideEffectsTracker sideEffectsTracker);
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
				AccountRecordsHistorian recordsHistorian);
	}

	@FunctionalInterface
	interface AccountStoreFactory {
		AccountStore newAccountStore(
				OptionValidator validator,
				GlobalDynamicProperties dynamicProperties,
				BackingStore<AccountID, MerkleAccount> accounts);
	}

	@FunctionalInterface
	interface TokenStoreFactory {
		TypedTokenStore newTokenStore(
				AccountStore accountStore,
				BackingStore<TokenID, MerkleToken> tokens,
				BackingStore<NftId, MerkleUniqueToken> uniqueTokens,
				BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> tokenRels,
				TypedTokenStore.LegacyTreasuryAdder treasuryAdder,
				TypedTokenStore.LegacyTreasuryRemover treasuryRemover,
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

	/* --- The precompile implementations --- */
	interface Precompile {
		TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver);

		void run(MessageFrame frame);

		long getMinimumFeeInTinybars(Timestamp consensusTime);

		default void addImplicitCostsIn(TxnAccessor accessor) {
			/* No-op */
		}

		default Bytes getSuccessResultFor(ExpirableTxnRecord.Builder childRecord) {
			return SUCCESS_RESULT;
		}

		default Bytes getFailureResultFor(ResponseCodeEnum status) {
			return resultFrom(status);
		}

		default List<FcAssessedCustomFee> getCustomFees() {
			return NO_CUSTOM_FEES;
		}

		default boolean shouldAddTraceabilityFieldsToRecord() {
			return true;
		}
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
			final var associateLogic = associateLogicFactory.newAssociateLogic(tokenStore, accountStore);
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
		public void run(
				final MessageFrame frame
		) {
			Objects.requireNonNull(mintOp);

			/* --- Check required signatures --- */
			final var tokenId = Id.fromGrpcToken(mintOp.tokenType());
			final var hasRequiredSigs = validateKey(frame, tokenId.asEvmAddress(), sigsVerifier::hasActiveSupplyKey);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var scopedAccountStore = createAccountStore();
			final var scopedTokenStore = createTokenStore(scopedAccountStore, sideEffectsTracker);
			final var mintLogic = mintLogicFactory.newMintLogic(validator, scopedTokenStore, scopedAccountStore);

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
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			transferOp = switch (functionId) {
				case ABI_ID_CRYPTO_TRANSFER -> decoder.decodeCryptoTransfer(input, aliasResolver);
				case ABI_ID_TRANSFER_TOKENS -> decoder.decodeTransferTokens(input, aliasResolver);
				case ABI_ID_TRANSFER_TOKEN -> decoder.decodeTransferToken(input, aliasResolver);
				case ABI_ID_TRANSFER_NFTS -> decoder.decodeTransferNFTs(input, aliasResolver);
				case ABI_ID_TRANSFER_NFT -> decoder.decodeTransferNFT(input, aliasResolver);
				default -> throw new InvalidTransactionException(
						"Transfer precompile received unknown functionId=" + functionId + " (via " + input + ")",
						FAIL_INVALID);
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
			if (impliedValidity != ResponseCodeEnum.OK) {
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
					final var hasSenderSig = validateKey(frame, change.getAccount().asEvmAddress(),
							sigsVerifier::hasActiveKey);
					validateTrue(hasSenderSig, INVALID_SIGNATURE);
				}
				if (i >= numExplicitChanges) {
					/* Ignore receiver sig requirements for custom fee payments (which are never NFT transfers) */
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
			if (impliedValidity != ResponseCodeEnum.OK) {
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
										aaWith(fungibleTransfer.receiver, fungibleTransfer.amount), null),
								BalanceChange.changingFtUnits(
										Id.fromGrpcToken(fungibleTransfer.getDenomination()),
										fungibleTransfer.getDenomination(),
										aaWith(fungibleTransfer.sender, -fungibleTransfer.amount), null)));
					} else if (fungibleTransfer.sender == null) {
						changes.add(
								BalanceChange.changingFtUnits(
										Id.fromGrpcToken(fungibleTransfer.getDenomination()),
										fungibleTransfer.getDenomination(),
										aaWith(fungibleTransfer.receiver, fungibleTransfer.amount), null));
					} else {
						changes.add(
								BalanceChange.changingFtUnits(
										Id.fromGrpcToken(fungibleTransfer.getDenomination()),
										fungibleTransfer.getDenomination(),
										aaWith(fungibleTransfer.sender, -fungibleTransfer.amount), null));
					}
				}
				if (changes.isEmpty()) {
					for (final var nftExchange : tokenTransferWrapper.nftExchanges()) {
						changes.add(
								BalanceChange.changingNftOwnership(
										Id.fromGrpcToken(nftExchange.getTokenType()),
										nftExchange.getTokenType(),
										nftExchange.asGrpc(), null
								)
						);
					}
				}

				allChanges.addAll(changes);
			}
			return allChanges;
		}

		private AccountAmount aaWith(final AccountID account, final long amount) {
			return AccountAmount.newBuilder()
					.setAccountID(account)
					.setAmount(amount)
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
			final var burnLogic = burnLogicFactory.newBurnLogic(scopedTokenStore, scopedAccountStore);

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
		protected TokenID tokenID;

		protected ERCReadOnlyAbstractPrecompile(final TokenID tokenID) {
			this.tokenID = tokenID;
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
		public Bytes getFailureResultFor(ResponseCodeEnum status) {
			return null;
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
		private final TokenID tokenID;
		private final AccountID callerAccountID;
		private final boolean isFungible;

		public ERCTransferPrecompile(final TokenID tokenID, final Address callerAccount, final boolean isFungible) {
			this.callerAccountID = EntityIdUtils.accountIdFromEvmAddress(callerAccount);
			this.tokenID = tokenID;
			this.isFungible = isFungible;
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			super.initializeHederaTokenStore();

			final var nestedInput = input.slice(24);
			super.transferOp = switch (nestedInput.getInt(0)) {
				case ABI_ID_ERC_TRANSFER -> decoder.decodeErcTransfer(nestedInput, tokenID, callerAccountID, aliasResolver);
				case ABI_ID_ERC_TRANSFER_FROM -> decoder.decodeERCTransferFrom(nestedInput, tokenID,
						isFungible, aliasResolver);
				default -> throw new InvalidTransactionException(
						"Transfer precompile received unknown functionId=" + functionId + " (via " + nestedInput + ")",
						FAIL_INVALID);
			};
			super.syntheticTxn = syntheticTxnFactory.createCryptoTransfer(transferOp);
			super.extrapolateDetailsFromSyntheticTxn();
			return super.syntheticTxn;
		}

		@Override
		public void run(final MessageFrame frame) {
			super.run(frame);

			final var precompileAddress = Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS);

			if (isFungible) {
				frame.addLog(getLogForFungibleTransfer(precompileAddress));
			} else {
				frame.addLog(getLogForNftExchange(precompileAddress));
			}

		}

		private Log getLogForFungibleTransfer(final Address logger) {
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

			return EncodingFacade.LogBuilder.logBuilder().forLogger(logger)
					.forEventSignature(TRANSFER_EVENT)
					.forIndexedArgument(sender)
					.forIndexedArgument(receiver)
					.forDataItem(amount).build();
		}

		private Log getLogForNftExchange(final Address logger) {
			final var nftExchanges = super.transferOp.get(0).nftExchanges();
			final var nftExchange = nftExchanges.get(0).asGrpc();
			final var sender = asTypedEvmAddress(nftExchange.getSenderAccountID());
			final var receiver = asTypedEvmAddress(nftExchange.getReceiverAccountID());
			final var serialNumber = nftExchange.getSerialNumber();

			return EncodingFacade.LogBuilder.logBuilder().forLogger(logger)
					.forEventSignature(TRANSFER_EVENT)
					.forIndexedArgument(sender)
					.forIndexedArgument(receiver)
					.forIndexedArgument(serialNumber).build();
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			if (isFungible) {
				return encoder.encodeEcFungibleTransfer(true);
			} else {
				return Bytes.EMPTY;
			}
		}

		@Override
		public Bytes getFailureResultFor(final ResponseCodeEnum status) {
			if (isFungible) {
				return resultFrom(status);
			} else {
				return null;
			}
		}
	}

	protected class NamePrecompile extends ERCReadOnlyAbstractPrecompile {

		public NamePrecompile(TokenID tokenID) {
			super(tokenID);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger = ledgers.tokens();
			final var name = (String) tokensLedger.get(tokenID, NAME);

			return encoder.encodeName(name);
		}
	}

	protected class SymbolPrecompile extends ERCReadOnlyAbstractPrecompile {

		public SymbolPrecompile(final TokenID tokenID) {
			super(tokenID);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger = ledgers.tokens();
			final var symbol = (String) tokensLedger.get(tokenID, SYMBOL);

			return encoder.encodeSymbol(symbol);
		}
	}

	protected class DecimalsPrecompile extends ERCReadOnlyAbstractPrecompile {

		public DecimalsPrecompile(final TokenID tokenID) {
			super(tokenID);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger = ledgers.tokens();
			final var decimals = (Integer) tokensLedger.get(tokenID, DECIMALS);

			return encoder.encodeDecimals(decimals);
		}
	}

	protected class TotalSupplyPrecompile extends ERCReadOnlyAbstractPrecompile {

		public TotalSupplyPrecompile(final TokenID tokenID) {
			super(tokenID);
		}

		@Override
		public Bytes getSuccessResultFor(ExpirableTxnRecord.Builder childRecord) {
			final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger = ledgers.tokens();
			final var totalSupply = (long) tokensLedger.get(tokenID, TOTAL_SUPPLY);

			return encoder.encodeTotalSupply(totalSupply);
		}
	}

	protected class TokenURIPrecompile extends ERCReadOnlyAbstractPrecompile {
		private OwnerOfAndTokenURIWrapper tokenUriWrapper;

		public TokenURIPrecompile(final TokenID tokenID) {
			super(tokenID);
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var nestedInput = input.slice(24);
			tokenUriWrapper = decoder.decodeTokenUriNFT(nestedInput);

			return super.body(input, aliasResolver);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger = ledgers.nfts();
			var nftId = new NftId(tokenID.getShardNum(), tokenID.getRealmNum(), tokenID.getTokenNum(),
					tokenUriWrapper.tokenId());
			// If the requested serial num doesn't exist, we return the standard ERC error message
			var metaData = nftsLedger.exists(nftId)
					? new String((byte[]) nftsLedger.get(nftId, METADATA))
					: URI_QUERY_NON_EXISTING_TOKEN_ERROR;

			return encoder.encodeTokenUri(metaData);
		}
	}

	protected class OwnerOfPrecompile extends ERCReadOnlyAbstractPrecompile {
		private NftId nft;

		public OwnerOfPrecompile(final TokenID tokenID) {
			super(tokenID);
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var wrapper = decoder.decodeOwnerOf(input.slice(24));
			nft = new NftId(tokenID.getShardNum(), tokenID.getRealmNum(), tokenID.getTokenNum(), wrapper.tokenId());
			validateTrue(ledgers.nfts().exists(nft), INVALID_TOKEN_NFT_SERIAL_NUMBER);

			return super.body(input, aliasResolver);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final var owner = ledgers.ownerOf(nft);
			final var priorityAddress = updater.priorityAddress(owner);
			return encoder.encodeOwner(priorityAddress);
		}
	}

	protected class BalanceOfPrecompile extends ERCReadOnlyAbstractPrecompile {
		private BalanceOfWrapper balanceWrapper;

		public BalanceOfPrecompile(final TokenID tokenID) {
			super(tokenID);
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var nestedInput = input.slice(24);
			balanceWrapper = decoder.decodeBalanceOf(nestedInput, aliasResolver);
			return super.body(input, aliasResolver);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final var tokenRelsLedger = ledgers.tokenRels();
			final var rel = asTokenRel(balanceWrapper.accountId(), tokenID);
			final var balance = tokenRelsLedger.exists(rel) ? (long) tokenRelsLedger.get(rel, TOKEN_BALANCE) : 0L;
			return encoder.encodeBalance(balance);
		}
	}

	protected class AllowancePrecompile extends ERCReadOnlyAbstractPrecompile {
		private TokenAllowanceWrapper allowanceWrapper;

		public AllowancePrecompile(final TokenID tokenID) {
			super(tokenID);
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var nestedInput = input.slice(24);
			allowanceWrapper = decoder.decodeTokenAllowance(nestedInput, aliasResolver);

			return super.body(input, aliasResolver);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger = ledgers.accounts();
			final var allowance = (TreeMap<FcTokenAllowanceId, Long>) accountsLedger.get(allowanceWrapper.owner(), FUNGIBLE_TOKEN_ALLOWANCES);
			long value = 0;
			for (Map.Entry<FcTokenAllowanceId, Long> e : allowance.entrySet()) {
				if (allowanceWrapper.spender().getAccountNum() == e.getKey().getSpenderNum().longValue()) {
					value = e.getValue();
				}
			}
			return encoder.encodeAllowance(value);
		}
	}

	protected class ApprovePrecompile implements Precompile {
		protected ApproveWrapper approveOp;
		protected TokenID token;
		private final boolean isFungible;

		public ApprovePrecompile(final TokenID token, final boolean isFungible) {
			this.token = token;
			this.isFungible = isFungible;
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var accountId = EntityIdUtils.accountIdFromEvmAddress(senderAddress);
			final var fungibleTokenAllowances = (Map<FcTokenAllowanceId, Long>) ledgers.accounts().get(accountId, AccountProperty.FUNGIBLE_TOKEN_ALLOWANCES);

			final var nestedInput = input.slice(24);
			approveOp = decoder.decodeTokenApprove(nestedInput, token, isFungible, aliasResolver);

			if (isFungible) {
				long value = 0;
				for (Map.Entry<FcTokenAllowanceId, Long> e : fungibleTokenAllowances.entrySet()) {
					if (approveOp.spender().getAccountNum() == e.getKey().getSpenderNum().longValue()) {
						value = e.getValue();
					}
				}
				return syntheticTxnFactory.createAdjustAllowance(approveOp.withAdjustment(approveOp.amount().subtract(BigInteger.valueOf(value))));
			} else {
				return syntheticTxnFactory.createAdjustAllowance(approveOp);
			}
		}

		@Override
		public void run(final MessageFrame frame) {
			Objects.requireNonNull(approveOp);
			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var accountStore = createAccountStore();
			final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(EntityIdUtils.accountIdFromEvmAddress(senderAddress)));

			final var checkResponseCode = allowanceChecks.allowancesValidation(transactionBody.getCryptoAdjustAllowance().getCryptoAllowancesList(),
					transactionBody.getCryptoAdjustAllowance().getTokenAllowancesList(),
					transactionBody.getCryptoAdjustAllowance().getNftAllowancesList(),
					payerAccount,
					dynamicProperties.maxAllowanceLimitPerTransaction());

			if (!OK.equals(checkResponseCode)) {
				throw new InvalidTransactionException(checkResponseCode);
			}

			/* --- Execute the transaction and capture its results --- */
			final var adjustAllowanceLogic = adjustAllowanceLogicFactory.newAdjustAllowanceLogic(
					accountStore, dynamicProperties, sideEffectsTracker);
			adjustAllowanceLogic.adjustAllowance(transactionBody.getCryptoAdjustAllowance().getCryptoAllowancesList(),
					transactionBody.getCryptoAdjustAllowance().getTokenAllowancesList(),
					transactionBody.getCryptoAdjustAllowance().getNftAllowancesList(),
					EntityIdUtils.accountIdFromEvmAddress(frame.getSenderAddress()));

			final var precompileAddress = Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS);

			if (isFungible) {
				frame.addLog(getLogForFungibleAdjustAllowance(precompileAddress));
			} else {
				frame.addLog(getLogForNftAdjustAllowance(precompileAddress));
			}
		}

		@Override
		public long getMinimumFeeInTinybars(Timestamp consensusTime) {
			return 0;
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			return encoder.encodeApprove(true);
		}

		private Log getLogForFungibleAdjustAllowance(final Address logger) {
			return EncodingFacade.LogBuilder.logBuilder().forLogger(logger)
					.forEventSignature(APPROVAL_EVENT)
					.forIndexedArgument(senderAddress)
					.forIndexedArgument(asTypedEvmAddress(approveOp.spender()))
					.forDataItem(BigInteger.valueOf(approveOp.amount().longValue())).build();
		}

		private Log getLogForNftAdjustAllowance(final Address logger) {
			return EncodingFacade.LogBuilder.logBuilder().forLogger(logger)
					.forEventSignature(APPROVAL_EVENT)
					.forIndexedArgument(senderAddress)
					.forIndexedArgument(asTypedEvmAddress(approveOp.spender()))
					.forIndexedArgument(BigInteger.valueOf(approveOp.serialNumber().longValue())).build();
		}
	}

	protected class SetApprovalForAllPrecompile implements Precompile {
		protected TokenID tokenID;
		private SetApprovalForAllWrapper setApprovalForAllWrapper;

		public SetApprovalForAllPrecompile(final TokenID tokenID) {
			this.tokenID = tokenID;
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var nestedInput = input.slice(24);
			setApprovalForAllWrapper = decoder.decodeSetApprovalForAll(nestedInput, aliasResolver);

			return syntheticTxnFactory.createAdjustAllowanceForAllNFT(setApprovalForAllWrapper, tokenID);
		}

		@Override
		public void run(MessageFrame frame) {
			Objects.requireNonNull(setApprovalForAllWrapper);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var accountStore = createAccountStore();
			final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(EntityIdUtils.accountIdFromEvmAddress(senderAddress)));

			final var checkResponseCode = allowanceChecks.allowancesValidation(transactionBody.getCryptoAdjustAllowance().getCryptoAllowancesList(),
					transactionBody.getCryptoAdjustAllowance().getTokenAllowancesList(),
					transactionBody.getCryptoAdjustAllowance().getNftAllowancesList(),
					payerAccount,
					dynamicProperties.maxAllowanceLimitPerTransaction());

			validateTrue(OK.equals(checkResponseCode), checkResponseCode);

			/* --- Execute the transaction and capture its results --- */
			final var adjustAllowanceLogic = adjustAllowanceLogicFactory.newAdjustAllowanceLogic(
					accountStore, dynamicProperties, sideEffectsTracker);
			adjustAllowanceLogic.adjustAllowance(transactionBody.getCryptoAdjustAllowance().getCryptoAllowancesList(),
					transactionBody.getCryptoAdjustAllowance().getTokenAllowancesList(),
					transactionBody.getCryptoAdjustAllowance().getNftAllowancesList(),
					EntityIdUtils.accountIdFromEvmAddress(frame.getSenderAddress()));


			final var precompileAddress = Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS);

			frame.addLog(getLogForSetApprovalForAll(precompileAddress));
		}

		@Override
		public long getMinimumFeeInTinybars(Timestamp consensusTime) {
			return 0;
		}

		private Log getLogForSetApprovalForAll(final Address logger) {
			return EncodingFacade.LogBuilder.logBuilder().forLogger(logger)
					.forEventSignature(APPROVAL_FOR_ALL_EVENT)
					.forIndexedArgument(senderAddress)
					.forIndexedArgument(asTypedEvmAddress(setApprovalForAllWrapper.to()))
					.forDataItem(setApprovalForAllWrapper.approved()).build();
		}

	}

	protected class GetApprovedPrecompile extends ERCReadOnlyAbstractPrecompile {
		GetApprovedWrapper getApprovedWrapper;

		public GetApprovedPrecompile(final TokenID tokenID) {
			super(tokenID);
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var nestedInput = input.slice(24);
			getApprovedWrapper = decoder.decodeGetApproved(nestedInput);

			return super.body(input, aliasResolver);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger = ledgers.nfts();
			var nftId = new NftId(tokenID.getShardNum(), tokenID.getRealmNum(), tokenID.getTokenNum(),
					getApprovedWrapper.tokenId());
			var owner = (EntityId) nftsLedger.get(nftId, OWNER);
			TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger = ledgers.accounts();
			var allowances = (Map<FcTokenAllowanceId, FcTokenAllowance>) accountsLedger.get(owner.toGrpcAccountId(), NFT_ALLOWANCES);

			validateFalse(allowances.size() > 1, FAIL_INVALID);

			Address spender;

			Optional<Map.Entry<FcTokenAllowanceId, FcTokenAllowance>> allowance = allowances.entrySet().stream().findFirst();
			if (allowance.isPresent()) {
				spender = allowance.get().getKey().getSpenderNum().toEvmAddress();
				return encoder.encodeGetApproved(spender);
			}

			return encoder.encodeGetApproved(Address.fromHexString("0"));
		}

	}

	protected class IsApprovedForAllPrecompile extends ERCReadOnlyAbstractPrecompile {
		private IsApproveForAllWrapper isApproveForAllWrapper;

		public IsApprovedForAllPrecompile(final TokenID tokenID) {
			super(tokenID);
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			final var nestedInput = input.slice(24);
			isApproveForAllWrapper = decoder.decodeIsApprovedForAll(nestedInput, aliasResolver);

			return super.body(input, aliasResolver);
		}

		@Override
		public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
			TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger = ledgers.accounts();
			var allowances = (Map<FcTokenAllowanceId, FcTokenAllowance>) accountsLedger.get(isApproveForAllWrapper.owner(), NFT_ALLOWANCES);
			boolean isApprovedForAll = false;

			for (Map.Entry<FcTokenAllowanceId, FcTokenAllowance> e : allowances.entrySet()) {
				if (isApproveForAllWrapper.operator().getAccountNum() == e.getKey().getSpenderNum().longValue()) {
					isApprovedForAll = e.getValue().isApprovedForAll();
				}
			}

			return encoder.encodeIsApprovedForAll(isApprovedForAll);
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
	 * @param frame          current frame
	 * @param target         the element to test for key activation, in standard form
	 * @param activationTest the function which should be invoked for key validation
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
			return activationTest.apply(parentFrame.isPresent() && isDelegateCall(parentFrame.get()), target, sender, ledgers);
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
		 * @param isDelegateCall a flag showing if the message represented by the active frame is invoked via {@code delegatecall}
		 * @param target         an address with an implicit key understood by this implementation
		 * @param activeContract the contract address that can activate a contract or delegatable contract key
		 * @param worldLedgers   the worldLedgers representing current state
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

	public Precompile getPrecompile() {
		return precompile;
	}
}
