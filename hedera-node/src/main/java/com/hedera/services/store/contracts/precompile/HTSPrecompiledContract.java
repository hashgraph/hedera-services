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
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.BalanceOfWrapper;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.GetApprovedWrapper;
import com.hedera.services.store.contracts.precompile.codec.IsApproveForAllWrapper;
import com.hedera.services.store.contracts.precompile.codec.SetApprovalForAllWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenAllowanceWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenTransferWrapper;
import com.hedera.services.store.contracts.precompile.impl.AssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.DissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.MintPrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiAssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiDissociatePrecompile;
import com.hedera.services.store.contracts.precompile.utils.DescriptorUtils;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.services.txns.token.validators.CreateChecks;
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
import java.util.function.UnaryOperator;

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.grpc.marshalling.ImpliedTransfers.NO_ALIASES;
import static com.hedera.services.ledger.properties.AccountProperty.APPROVE_FOR_ALL_NFTS_ALLOWANCES;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.properties.AccountProperty.FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hedera.services.ledger.properties.NftProperty.SPENDER;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.store.contracts.precompile.codec.TokenCreateWrapper.FixedFeeWrapper.FixedFeePayment.INVALID_PAYMENT;
import static com.hedera.services.store.contracts.precompile.codec.TokenCreateWrapper.KeyValueWrapper.KeyValueType.INVALID_KEY;
import static com.hedera.services.store.contracts.precompile.utils.DescriptorUtils.isTokenProxyRedirect;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.APPROVE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.DELETE_NFT_APPROVE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.MINT_FUNGIBLE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.MINT_NFT;
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
	public static final ContractID HTS_PRECOMPILE_MIRROR_ID = contractIdFromEvmAddress(
			Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArrayUnsafe());
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
	public static final String URI_QUERY_NON_EXISTING_TOKEN_ERROR = "ERC721Metadata: URI query for nonexistent token";

	private final EntityCreator creator;
	private final DecodingFacade decoder;
	private final EncodingFacade encoder;
	private final GlobalDynamicProperties dynamicProperties;
	private final EvmSigsVerifier sigsVerifier;
	private final RecordsHistorian recordsHistorian;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final UsagePricesProvider resourceCosts;
	private final CreateChecks tokenCreateChecks;
	private final InfrastructureFactory infrastructureFactory;
	private final ImpliedTransfersMarshal impliedTransfersMarshal;
	private final DeleteAllowanceChecks deleteAllowanceChecks;
	private final ApproveAllowanceChecks approveAllowanceChecks;

	private int functionId;
	private Precompile precompile;
	private TransactionBody.Builder transactionBody;
	private final Provider<FeeCalculator> feeCalculator;
	private long gasRequirement = 0;
	private final StateView currentView;
	private SideEffectsTracker sideEffectsTracker;
	private final PrecompilePricingUtils precompilePricingUtils;
	private WorldLedgers ledgers;
	private Address senderAddress;
	private HederaStackedWorldStateUpdater updater;
	private boolean isTokenReadOnlyTransaction = false;

	@Inject
	public HTSPrecompiledContract(
			final GlobalDynamicProperties dynamicProperties,
			final GasCalculator gasCalculator,
			final RecordsHistorian recordsHistorian,
			final TxnAwareEvmSigsVerifier sigsVerifier,
			final DecodingFacade decoder,
			final EncodingFacade encoder,
			final SyntheticTxnFactory syntheticTxnFactory,
			final ExpiringCreations creator,
			final ImpliedTransfersMarshal impliedTransfersMarshal,
			final Provider<FeeCalculator> feeCalculator,
			final StateView currentView,
			final PrecompilePricingUtils precompilePricingUtils,
			final UsagePricesProvider resourceCosts,
			final CreateChecks tokenCreateChecks,
			final InfrastructureFactory infrastructureFactory,
			final DeleteAllowanceChecks deleteAllowanceChecks,
			final ApproveAllowanceChecks approveAllowanceChecks
	) {
		super("HTS", gasCalculator);
		this.decoder = decoder;
		this.encoder = encoder;
		this.sigsVerifier = sigsVerifier;
		this.recordsHistorian = recordsHistorian;
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.creator = creator;
		this.dynamicProperties = dynamicProperties;
		this.impliedTransfersMarshal = impliedTransfersMarshal;
		this.feeCalculator = feeCalculator;
		this.currentView = currentView;
		this.precompilePricingUtils = precompilePricingUtils;
		this.resourceCosts = resourceCosts;
		this.tokenCreateChecks = tokenCreateChecks;
		this.infrastructureFactory = infrastructureFactory;
		this.approveAllowanceChecks = approveAllowanceChecks;
		this.deleteAllowanceChecks = deleteAllowanceChecks;
	}

	public Pair<Long, Bytes> computeCosted(final Bytes input, final MessageFrame frame) {
		if (frame.isStatic()) {
			if (!isTokenProxyRedirect(input)) {
				frame.setRevertReason(STATIC_CALL_REVERT_REASON);
				return Pair.of(defaultGas(), null);
			} else {
				final var proxyUpdater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
				if (!proxyUpdater.hasMutableLedgers()) {
					final var executor = infrastructureFactory.newRedirectExecutor(
							input, frame, this::computeViewFunctionGas);
					return executor.computeCosted();
				}
			}
		}
		final var result = compute(input, frame);
		return Pair.of(gasRequirement, result);
	}

	@Override
	public long gasRequirement(final Bytes bytes) {
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
			gasRequirement = precompile.getMinimumFeeInTinybars(Timestamp.newBuilder().setSeconds(now).build());
		} else {
			computeGasRequirement(now);
		}
		return computeInternal(frame);
	}

	void prepareFields(final MessageFrame frame) {
		this.updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
		this.sideEffectsTracker = infrastructureFactory.newSideEffects();
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
		final Long baseGasCost = (actualFeeInTinybars + gasPriceInTinybars - 1L) / gasPriceInTinybars;

		// charge premium
		gasRequirement = baseGasCost + (baseGasCost/5L);
	}

	void computeViewFunctionGasRequirement(final long blockTimestamp) {
		final var now = Timestamp.newBuilder().setSeconds(blockTimestamp).build();
		gasRequirement = computeViewFunctionGas(now, precompile.getMinimumFeeInTinybars(now));
	}

	long computeViewFunctionGas(final Timestamp now, final long minimumTinybarCost) {
		final var calculator = feeCalculator.get();
		final var usagePrices = resourceCosts.defaultPricesGiven(TokenGetInfo, now);
		final var fees = calculator.estimatePayment(
				SYNTHETIC_REDIRECT_QUERY, usagePrices, currentView, now, ANSWER_ONLY);

		final long gasPriceInTinybars = calculator.estimatedGasPriceInTinybars(ContractCall, now);
		final long calculatedFeeInTinybars = fees.getNetworkFee() + fees.getNodeFee() + fees.getServiceFee();
		final long actualFeeInTinybars = Math.max(minimumTinybarCost, calculatedFeeInTinybars);

		// convert to gas cost
		final long baseGasCost = (actualFeeInTinybars + gasPriceInTinybars - 1L) / gasPriceInTinybars;

		// charge premium
		return baseGasCost + (baseGasCost/5L);
	}

	void prepareComputation(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		this.precompile = null;
		this.transactionBody = null;

		this.functionId = input.getInt(0);
		this.gasRequirement = 0L;

		this.precompile =
				switch (functionId) {
					case AbiConstants.ABI_ID_CRYPTO_TRANSFER,
							AbiConstants.ABI_ID_TRANSFER_TOKENS,
							AbiConstants.ABI_ID_TRANSFER_TOKEN,
							AbiConstants.ABI_ID_TRANSFER_NFTS,
							AbiConstants.ABI_ID_TRANSFER_NFT -> new TransferPrecompile();
					case AbiConstants.ABI_ID_MINT_TOKEN -> new MintPrecompile(
							ledgers, decoder, encoder, updater.aliases(), sigsVerifier, recordsHistorian,
							sideEffectsTracker, syntheticTxnFactory, infrastructureFactory, precompilePricingUtils);
					case AbiConstants.ABI_ID_BURN_TOKEN -> new BurnPrecompile();
					case AbiConstants.ABI_ID_ASSOCIATE_TOKENS -> new MultiAssociatePrecompile(
							ledgers, decoder, updater.aliases(), sigsVerifier,
							sideEffectsTracker, syntheticTxnFactory, infrastructureFactory, precompilePricingUtils);
					case AbiConstants.ABI_ID_ASSOCIATE_TOKEN -> new AssociatePrecompile(
							ledgers, decoder, updater.aliases(), sigsVerifier,
							sideEffectsTracker, syntheticTxnFactory, infrastructureFactory, precompilePricingUtils);
					case AbiConstants.ABI_ID_DISSOCIATE_TOKENS -> new MultiDissociatePrecompile(
							ledgers, decoder, updater.aliases(), sigsVerifier,
							sideEffectsTracker, syntheticTxnFactory, infrastructureFactory, precompilePricingUtils);
					case AbiConstants.ABI_ID_DISSOCIATE_TOKEN -> new DissociatePrecompile(
							ledgers, decoder, updater.aliases(), sigsVerifier,
							sideEffectsTracker, syntheticTxnFactory, infrastructureFactory, precompilePricingUtils);
					case AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN -> {
						final var target = DescriptorUtils.getRedirectTarget(input);
						final var tokenId = target.tokenId();
						final var isFungibleToken = TokenType.FUNGIBLE_COMMON.equals(ledgers.typeOf(tokenId));
						Precompile nestedPrecompile;
						this.isTokenReadOnlyTransaction = true;
						final var nestedFunctionSelector = target.descriptor();
						if (AbiConstants.ABI_ID_NAME == nestedFunctionSelector) {
							nestedPrecompile = new NamePrecompile(tokenId);
						} else if (AbiConstants.ABI_ID_SYMBOL == nestedFunctionSelector) {
							nestedPrecompile = new SymbolPrecompile(tokenId);
						} else if (AbiConstants.ABI_ID_DECIMALS == nestedFunctionSelector) {
							if (!isFungibleToken) {
								throw new InvalidTransactionException(
										NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON, INVALID_TOKEN_ID);
							}
							nestedPrecompile = new DecimalsPrecompile(tokenId);
						} else if (AbiConstants.ABI_ID_TOTAL_SUPPLY_TOKEN == nestedFunctionSelector) {
							nestedPrecompile = new TotalSupplyPrecompile(tokenId);
						} else if (AbiConstants.ABI_ID_BALANCE_OF_TOKEN == nestedFunctionSelector) {
							nestedPrecompile = new BalanceOfPrecompile(tokenId);
						} else if (AbiConstants.ABI_ID_OWNER_OF_NFT == nestedFunctionSelector) {
							if (isFungibleToken) {
								throw new InvalidTransactionException(
										NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON, INVALID_TOKEN_ID);
							}
							nestedPrecompile = new OwnerOfPrecompile(tokenId);
						} else if (AbiConstants.ABI_ID_TOKEN_URI_NFT == nestedFunctionSelector) {
							if (isFungibleToken) {
								throw new InvalidTransactionException(
										NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON, INVALID_TOKEN_ID);
							}
							nestedPrecompile = new TokenURIPrecompile(tokenId);
						} else if (AbiConstants.ABI_ID_ERC_TRANSFER == nestedFunctionSelector) {
							this.isTokenReadOnlyTransaction = false;
							if (!isFungibleToken) {
								throw new InvalidTransactionException(
										NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON, INVALID_TOKEN_ID);
							}
							nestedPrecompile = new ERCTransferPrecompile(tokenId, this.senderAddress, isFungibleToken);
						} else if (AbiConstants.ABI_ID_ERC_TRANSFER_FROM == nestedFunctionSelector) {
							if (!dynamicProperties.areAllowancesEnabled()) {
								throw new InvalidTransactionException(NOT_SUPPORTED);
							}
							this.isTokenReadOnlyTransaction = false;
							nestedPrecompile = new ERCTransferPrecompile(tokenId, this.senderAddress, isFungibleToken);
						} else if (AbiConstants.ABI_ID_ALLOWANCE == nestedFunctionSelector) {
							if (!dynamicProperties.areAllowancesEnabled()) {
								throw new InvalidTransactionException(NOT_SUPPORTED);
							}
							nestedPrecompile = new AllowancePrecompile(tokenId);
						} else if (AbiConstants.ABI_ID_APPROVE == nestedFunctionSelector) {
							if (!dynamicProperties.areAllowancesEnabled()) {
								throw new InvalidTransactionException(NOT_SUPPORTED);
							}
							this.isTokenReadOnlyTransaction = false;
							nestedPrecompile = new ApprovePrecompile(tokenId, isFungibleToken);
						} else if (AbiConstants.ABI_ID_SET_APPROVAL_FOR_ALL == nestedFunctionSelector) {
							if (!dynamicProperties.areAllowancesEnabled()) {
								throw new InvalidTransactionException(NOT_SUPPORTED);
							}
							nestedPrecompile = new SetApprovalForAllPrecompile(tokenId);
						} else if (AbiConstants.ABI_ID_GET_APPROVED == nestedFunctionSelector) {
							if (!dynamicProperties.areAllowancesEnabled()) {
								throw new InvalidTransactionException(NOT_SUPPORTED);
							}
							nestedPrecompile = new GetApprovedPrecompile(tokenId);
						} else if (AbiConstants.ABI_ID_IS_APPROVED_FOR_ALL == nestedFunctionSelector) {
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
					case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN,
							AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES,
							AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN,
							AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES -> (dynamicProperties.isHTSPrecompileCreateEnabled())
							? new TokenCreatePrecompile()
							: null;
					default -> null;
				};
		if (precompile != null) {
			decodeInput(input, aliasResolver);
		}
	}

	/* --- Helpers --- */
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
			validateTrue(frame.getRemainingGas() >= gasRequirement, INSUFFICIENT_GAS);

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
					this.gasRequirement,
					Collections.emptyList(),
					Collections.emptyList(),
					EvmFnResult.EMPTY,
					Collections.emptyMap(),
					precompile.shouldAddTraceabilityFieldsToRecord() ? messageFrame.getRemainingGas() : 0L,
					precompile.shouldAddTraceabilityFieldsToRecord() ? messageFrame.getValue().toLong() : 0L,
					precompile.shouldAddTraceabilityFieldsToRecord() ? messageFrame.getInputData().toArrayUnsafe() :
							EvmFnResult.EMPTY,
					null);
			childRecord.setContractCallResult(evmFnResult);
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
				case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN -> decoder.decodeFungibleCreate(input, aliasResolver);
				case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES -> decoder.decodeFungibleCreateWithFees(input,
						aliasResolver);
				case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN -> decoder.decodeNonFungibleCreate(input,
						aliasResolver);
				case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES -> decoder.decodeNonFungibleCreateWithFees(
						input, aliasResolver);
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
			final var treasuryHasSigned = KeyActivationUtils.validateKey(
					frame, treasuryId.asEvmAddress(), sigsVerifier::hasActiveKey, ledgers, updater.aliases());
			validateTrue(treasuryHasSigned, INVALID_SIGNATURE);
			tokenCreateOp.getAdminKey().ifPresent(key -> validateTrue(validateAdminKey(frame, key), INVALID_SIGNATURE));

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
			final var tokenStore = infrastructureFactory.newTokenStore(
					accountStore, sideEffectsTracker, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());
			final var tokenCreateLogic = infrastructureFactory.newTokenCreateLogic(
					accountStore, tokenStore);

			/* --- Execute the transaction and capture its results --- */
			tokenCreateLogic.create(creationTime.getEpochSecond(), EntityIdUtils.accountIdFromEvmAddress(senderAddress),
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
				case INHERIT_ACCOUNT_KEY -> KeyActivationUtils.validateKey(
						frame, senderAddress, sigsVerifier::hasActiveKey, ledgers, updater.aliases());
				case CONTRACT_ID -> KeyActivationUtils.validateKey(
						frame, asTypedEvmAddress(key.getContractID()), sigsVerifier::hasActiveKey, ledgers,
						updater.aliases());
				case DELEGATABLE_CONTRACT_ID -> KeyActivationUtils.validateKey(
						frame, asTypedEvmAddress(key.getDelegatableContractID()), sigsVerifier::hasActiveKey, ledgers,
						updater.aliases());
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
			hederaTokenStore = infrastructureFactory.newHederaTokenStore(
					sideEffectsTracker, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());
		}

		@Override
		public void customizeTrackingLedgers(final WorldLedgers worldLedgers) {
			worldLedgers.customizeForAutoAssociatingOp(sideEffectsTracker);
		}

		@Override
		public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
			transferOp = switch (functionId) {
				case AbiConstants.ABI_ID_CRYPTO_TRANSFER -> decoder.decodeCryptoTransfer(input, aliasResolver);
				case AbiConstants.ABI_ID_TRANSFER_TOKENS -> decoder.decodeTransferTokens(input, aliasResolver);
				case AbiConstants.ABI_ID_TRANSFER_TOKEN -> decoder.decodeTransferToken(input, aliasResolver);
				case AbiConstants.ABI_ID_TRANSFER_NFTS -> decoder.decodeTransferNFTs(input, aliasResolver);
				case AbiConstants.ABI_ID_TRANSFER_NFT -> decoder.decodeTransferNFT(input, aliasResolver);
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
			if (impliedValidity != ResponseCodeEnum.OK) {
				throw new InvalidTransactionException(impliedValidity);
			}

			/* We remember this size to know to ignore receiverSigRequired=true for custom fee payments */
			final var numExplicitChanges = explicitChanges.size();
			final var assessmentStatus = impliedTransfers.getMeta().code();
			validateTrue(assessmentStatus == OK, assessmentStatus);
			var changes = impliedTransfers.getAllBalanceChanges();

			hederaTokenStore.setAccountsLedger(ledgers.accounts());

			final var transferLogic = infrastructureFactory.newTransferLogic(
					hederaTokenStore, sideEffectsTracker, ledgers.nfts(), ledgers.accounts(), ledgers.tokenRels());

			for (int i = 0, n = changes.size(); i < n; i++) {
				final var change = changes.get(i);
				final var units = change.getAggregatedUnits();
				if (change.isForNft() || units < 0) {
					if (change.isApprovedAllowance()) {
						// Signing requirements are skipped for changes to be authorized via an allowance
						continue;
					}
					final var hasSenderSig = KeyActivationUtils.validateKey(
							frame, change.getAccount().asEvmAddress(), sigsVerifier::hasActiveKey, ledgers,
							updater.aliases());
					validateTrue(hasSenderSig, INVALID_SIGNATURE);
				}
				if (i >= numExplicitChanges) {
					/* Ignore receiver sig requirements for custom fee payments (which are never NFT transfers) */
					continue;
				}
				var hasReceiverSigIfReq = true;
				if (change.isForNft()) {
					final var counterPartyAddress = asTypedEvmAddress(change.counterPartyAccountId());
					hasReceiverSigIfReq = KeyActivationUtils.validateKey(
							frame, counterPartyAddress, sigsVerifier::hasActiveKeyOrNoReceiverSigReq, ledgers,
							updater.aliases());
				} else if (units > 0) {
					hasReceiverSigIfReq = KeyActivationUtils.validateKey(
							frame, change.getAccount().asEvmAddress(), sigsVerifier::hasActiveKeyOrNoReceiverSigReq,
							ledgers, updater.aliases());
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
			final var hasRequiredSigs = KeyActivationUtils.validateKey(
					frame, tokenId.asEvmAddress(), sigsVerifier::hasActiveSupplyKey, ledgers, updater.aliases());
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
			final var tokenStore = infrastructureFactory.newTokenStore(
					accountStore, sideEffectsTracker, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());
			final var burnLogic = infrastructureFactory.newBurnLogic(accountStore, tokenStore);
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
				case AbiConstants.ABI_ID_ERC_TRANSFER -> decoder.decodeERCTransfer(nestedInput, tokenID,
						callerAccountID,
						aliasResolver);
				case AbiConstants.ABI_ID_ERC_TRANSFER_FROM -> {
					final var operatorId = EntityId.fromGrpcAccountId(callerAccountID);
					yield decoder.decodeERCTransferFrom(
							nestedInput, tokenID, isFungible, aliasResolver, ledgers, operatorId);
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
					.forEventSignature(AbiConstants.TRANSFER_EVENT)
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
					.forEventSignature(AbiConstants.TRANSFER_EVENT)
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
		public TotalSupplyPrecompile(final TokenID tokenID) {
			super(tokenID);
		}

		@Override
		public Bytes getSuccessResultFor(ExpirableTxnRecord.Builder childRecord) {
			final var totalSupply = ledgers.totalSupplyOf(tokenId);
			return encoder.encodeTotalSupply(totalSupply);
		}
	}

	protected class TokenURIPrecompile extends ERCReadOnlyAbstractPrecompile {
		private NftId nftId;

		public TokenURIPrecompile(final TokenID tokenID) {
			super(tokenID);
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

		public OwnerOfPrecompile(final TokenID tokenID) {
			super(tokenID);
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
			final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
			final var tokenStore = infrastructureFactory.newTokenStore(
					accountStore, sideEffectsTracker, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());
			final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(grpcOperatorId));
			// --- Execute the transaction and capture its results ---
			if (isNftApprovalRevocation()) {
				final var deleteAllowanceLogic =
						infrastructureFactory.newDeleteAllowanceLogic(accountStore, tokenStore);
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
				final var approveAllowanceLogic = infrastructureFactory.newApproveAllowanceLogic(
						accountStore, tokenStore);
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
			final var precompileAddress = Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS);

			if (isFungible) {
				frame.addLog(getLogForFungibleAdjustAllowance(precompileAddress));
			} else {
				frame.addLog(getLogForNftAdjustAllowance(precompileAddress));
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

		private Log getLogForFungibleAdjustAllowance(final Address logger) {
			return EncodingFacade.LogBuilder.logBuilder()
					.forLogger(logger)
					.forEventSignature(AbiConstants.APPROVAL_EVENT)
					.forIndexedArgument(senderAddress)
					.forIndexedArgument(asTypedEvmAddress(approveOp.spender()))
					.forDataItem(BigInteger.valueOf(approveOp.amount().longValue())).build();
		}

		private Log getLogForNftAdjustAllowance(final Address logger) {
			return EncodingFacade.LogBuilder.logBuilder()
					.forLogger(logger)
					.forEventSignature(AbiConstants.APPROVAL_EVENT)
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
			final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
			final var tokenStore = infrastructureFactory.newTokenStore(
					accountStore, sideEffectsTracker, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());
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
			final var approveAllowanceLogic = infrastructureFactory.newApproveAllowanceLogic(
					accountStore, tokenStore);
			approveAllowanceLogic.approveAllowance(transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
					transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
					transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
					EntityIdUtils.accountIdFromEvmAddress(frame.getSenderAddress()));

			final var precompileAddress = Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS);

			frame.addLog(getLogForSetApprovalForAll(precompileAddress));
		}

		@Override
		public long getMinimumFeeInTinybars(Timestamp consensusTime) {
			return precompilePricingUtils.getMinimumPriceInTinybars(APPROVE, consensusTime);
		}

		private Log getLogForSetApprovalForAll(final Address logger) {
			return EncodingFacade.LogBuilder.logBuilder()
					.forLogger(logger)
					.forEventSignature(AbiConstants.APPROVAL_FOR_ALL_EVENT)
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

	private long defaultGas() {
		return dynamicProperties.htsDefaultGasCost();
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

	@VisibleForTesting
	public Precompile getPrecompile() {
		return precompile;
	}
}
