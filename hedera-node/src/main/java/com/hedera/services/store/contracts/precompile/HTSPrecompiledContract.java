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
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.SoliditySigsVerifier;
import com.hedera.services.contracts.sources.TxnAwareSoliditySigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.store.tokens.views.UniqueTokenViewsManager;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.txns.token.AssociateLogic;
import com.hedera.services.txns.token.BurnLogic;
import com.hedera.services.txns.token.DissociateLogic;
import com.hedera.services.txns.token.MintLogic;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.grpc.marshalling.ImpliedTransfers.NO_ALIASES;
import static com.hedera.services.ledger.ids.ExceptionalEntityIdSource.NOOP_ID_SOURCE;
import static com.hedera.services.state.expiry.ExpiringCreations.EMPTY_MEMO;
import static com.hedera.services.store.tokens.views.UniqueTokenViewsManager.NOOP_VIEWS_MANAGER;
import static com.hedera.services.utils.EntityIdUtils.asTypedSolidityAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

@Singleton
public class HTSPrecompiledContract extends AbstractPrecompiledContract {
	private static final Logger log = LogManager.getLogger(HTSPrecompiledContract.class);

	private static final Bytes SUCCESS_RESULT = resultFrom(SUCCESS);
	private static final Bytes STATIC_CALL_REVERT_REASON = Bytes.of("HTS precompiles are not static".getBytes());
	private static final Bytes ERROR_DECODING_INPUT_REVERT_REASON = Bytes.of(
			"Error decoding precompile input".getBytes());
	private static final List<Long> NO_SERIAL_NOS = Collections.emptyList();
	private static final List<ByteString> NO_METADATA = Collections.emptyList();
	private static final List<FcAssessedCustomFee> NO_CUSTOM_FEES = Collections.emptyList();
	private static final EntityIdSource ids = NOOP_ID_SOURCE;

	/* Precompiles cannot change treasury accounts */
	public static final TypedTokenStore.LegacyTreasuryAdder NOOP_TREASURY_ADDER = (aId, tId) -> {
	};
	public static final TypedTokenStore.LegacyTreasuryRemover NOOP_TREASURY_REMOVER = (aId, tId) -> {
	};

	private MintLogicFactory mintLogicFactory = MintLogic::new;
	private BurnLogicFactory burnLogicFactory = BurnLogic::new;
	private AssociateLogicFactory associateLogicFactory = AssociateLogic::new;
	private DissociateLogicFactory dissociateLogicFactory = DissociateLogic::new;
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
	private final SoliditySigsVerifier sigsVerifier;
	private final AccountRecordsHistorian recordsHistorian;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final DissociationFactory dissociationFactory;

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

	private int functionId;
	private Precompile precompile;
	private TransactionBody.Builder transactionBody;

	@Inject
	public HTSPrecompiledContract(
			final OptionValidator validator,
			final GlobalDynamicProperties dynamicProperties,
			final GasCalculator gasCalculator,
			final AccountRecordsHistorian recordsHistorian,
			final TxnAwareSoliditySigsVerifier sigsVerifier,
			final DecodingFacade decoder,
			final EncodingFacade encoder,
			final SyntheticTxnFactory syntheticTxnFactory,
			final ExpiringCreations creator,
			final DissociationFactory dissociationFactory,
			final ImpliedTransfersMarshal impliedTransfersMarshal
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
	}

	@Override
	public Gas gasRequirement(final Bytes input) {
		this.precompile = null;
		this.transactionBody = null;

		this.functionId = input.getInt(0);
		var defaultGasPrice = dynamicProperties.htsDefaultGasCost();
		Gas gasRequirement = Gas.of(defaultGasPrice);

		switch (functionId) {
			case ABI_ID_CRYPTO_TRANSFER,
					ABI_ID_TRANSFER_TOKENS,
					ABI_ID_TRANSFER_TOKEN,
					ABI_ID_TRANSFER_NFTS,
					ABI_ID_TRANSFER_NFT: {
				this.precompile = new TransferPrecompile();
				decodeInput(input);
				var transfersCount = transactionBody.getCryptoTransfer().getTokenTransfersCount();
				/*-- 10K if only one transfer or 5K per index --*/
				if (transfersCount <= 1) {
					gasRequirement = Gas.of(defaultGasPrice);
				} else {
					gasRequirement = Gas.of((defaultGasPrice / 2) * transfersCount);
				}
				break;
			}
			case ABI_ID_MINT_TOKEN: {
				this.precompile = new MintPrecompile();
				decodeInput(input);
				/*-- 10K --*/
				gasRequirement = Gas.of(defaultGasPrice);
				break;
			}
			case ABI_ID_BURN_TOKEN: {
				this.precompile = new BurnPrecompile();
				decodeInput(input);
				/*-- 10K --*/
				gasRequirement = Gas.of(defaultGasPrice);
				break;
			}
			case ABI_ID_ASSOCIATE_TOKENS: {
				this.precompile = new MultiAssociatePrecompile();
				decodeInput(input);
				/*-- 10K per index --*/
				gasRequirement = Gas.of((defaultGasPrice) * this.transactionBody.getTokenAssociate().getTokensCount());
				break;
			}
			case ABI_ID_ASSOCIATE_TOKEN: {
				this.precompile = new AssociatePrecompile();
				decodeInput(input);
				/*-- 10K --*/
				gasRequirement = Gas.of(defaultGasPrice);
				break;
			}
			case ABI_ID_DISSOCIATE_TOKENS: {
				this.precompile = new MultiDissociatePrecompile();
				decodeInput(input);
				/*-- 10K per index --*/
				gasRequirement = Gas.of((defaultGasPrice) * this.transactionBody.getTokenDissociate().getTokensCount());
				break;
			}
			case ABI_ID_DISSOCIATE_TOKEN: {
				this.precompile = new DissociatePrecompile();
				decodeInput(input);
				/*-- 10K --*/
				gasRequirement = Gas.of(defaultGasPrice);
				break;
			}
			default:
		}
		return gasRequirement;
	}

	@Override
	public Bytes compute(final Bytes input, final MessageFrame messageFrame) {
		if (messageFrame.isStatic()) {
			messageFrame.setRevertReason(STATIC_CALL_REVERT_REASON);
			return null;
		}
		if (this.precompile == null || this.transactionBody == null) {
			messageFrame.setRevertReason(ERROR_DECODING_INPUT_REVERT_REASON);
			return null;
		}
		return computeInternal(messageFrame);
	}

	/* --- Helpers --- */
	private AccountStore createAccountStore(final WorldLedgers ledgers) {
		return accountStoreFactory.newAccountStore(validator, dynamicProperties, ledgers.accounts());
	}

	private TypedTokenStore createTokenStore(
			final WorldLedgers ledgers,
			final AccountStore accountStore,
			final SideEffectsTracker sideEffects
	) {
		return tokenStoreFactory.newTokenStore(
				accountStore,
				ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels(),
				NOOP_VIEWS_MANAGER, NOOP_TREASURY_ADDER, NOOP_TREASURY_REMOVER,
				sideEffects);
	}

	private static Bytes resultFrom(final ResponseCodeEnum status) {
		return UInt256.valueOf(status.getNumber());
	}

	private void decodeInput(Bytes input) {
		this.transactionBody = TransactionBody.newBuilder();
		try {
			this.transactionBody = this.precompile.body(input);
		} catch (Exception e) {
			log.warn("Internal precompile failure", e);
			throw new InvalidTransactionException("Cannot decode precompile input", FAIL_INVALID);
		}
	}

	@SuppressWarnings("rawtypes")
	protected Bytes computeInternal(final MessageFrame frame) {
		final var updater = (AbstractLedgerWorldUpdater) frame.getWorldUpdater();
		final var ledgers = updater.wrappedTrackingLedgers();

		Bytes result;
		ExpirableTxnRecord.Builder childRecord;
		try {
			childRecord = precompile.run(frame, ledgers);
			result = precompile.getSuccessResultFor(childRecord);
			ledgers.commit();
		} catch (InvalidTransactionException e) {
			final var status = e.getResponseCode();
			childRecord = creator.createUnsuccessfulSyntheticRecord(status);
			result = precompile.getFailureResultFor(status);
		} catch (Exception e) {
			log.warn("Internal precompile failure", e);
			childRecord = creator.createUnsuccessfulSyntheticRecord(FAIL_INVALID);
			result = precompile.getFailureResultFor(FAIL_INVALID);
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
				TypedTokenStore tokenStore,
				AccountStore accountStore,
				GlobalDynamicProperties dynamicProperties);
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
	interface TransferLogicFactory {
		TransferLogic newLogic(
				TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger,
				TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger,
				TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger,
				HederaTokenStore tokenStore,
				SideEffectsTracker sideEffectsTracker,
				UniqueTokenViewsManager tokenViewsManager,
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
				UniqueTokenViewsManager uniqTokenViewsManager,
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
				UniqueTokenViewsManager uniqueTokenViewsManager,
				GlobalDynamicProperties properties,
				TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger,
				TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger,
				BackingStore<TokenID, MerkleToken> backingTokens);
	}

	/* --- The precompile implementations --- */
	interface Precompile {
		TransactionBody.Builder body(Bytes input);

		ExpirableTxnRecord.Builder run(MessageFrame frame, WorldLedgers ledgers);

		default Bytes getSuccessResultFor(ExpirableTxnRecord.Builder childRecord) {
			return SUCCESS_RESULT;
		}

		default Bytes getFailureResultFor(ResponseCodeEnum status) {
			return resultFrom(status);
		}
	}

	private abstract class AbstractAssociatePrecompile implements Precompile {
		protected Association associateOp;

		@Override
		public ExpirableTxnRecord.Builder run(
				final MessageFrame frame,
				final WorldLedgers ledgers
		) {
			Objects.requireNonNull(associateOp);

			/* --- Check required signatures --- */
			final var accountId = Id.fromGrpcAccount(associateOp.accountId());
			accountId.asEvmAddress();
			final var hasRequiredSigs = validateKey(frame, accountId.asEvmAddress(), sigsVerifier::hasActiveKey);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var sideEffects = sideEffectsFactory.get();
			final var accountStore = createAccountStore(ledgers);
			final var tokenStore = createTokenStore(ledgers, accountStore, sideEffects);

			/* --- Execute the transaction and capture its results --- */
			final var associateLogic = associateLogicFactory.newAssociateLogic(
					tokenStore, accountStore, dynamicProperties);
			associateLogic.associate(accountId, associateOp.tokenIds());
			return creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects, EMPTY_MEMO);
		}
	}

	protected class AssociatePrecompile extends AbstractAssociatePrecompile {
		@Override
		public TransactionBody.Builder body(final Bytes input) {
			associateOp = decoder.decodeAssociation(input);
			return syntheticTxnFactory.createAssociate(associateOp);
		}
	}

	protected class MultiAssociatePrecompile extends AbstractAssociatePrecompile {
		@Override
		public TransactionBody.Builder body(final Bytes input) {
			associateOp = decoder.decodeMultipleAssociations(input);
			return syntheticTxnFactory.createAssociate(associateOp);
		}
	}

	private abstract class AbstractDissociatePrecompile implements Precompile {
		protected Dissociation dissociateOp;

		@Override
		public ExpirableTxnRecord.Builder run(
				final MessageFrame frame,
				final WorldLedgers ledgers
		) {
			Objects.requireNonNull(dissociateOp);

			/* --- Check required signatures --- */
			final var accountId = Id.fromGrpcAccount(dissociateOp.accountId());
			final var hasRequiredSigs = validateKey(frame, accountId.asEvmAddress(), sigsVerifier::hasActiveKey);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var sideEffects = sideEffectsFactory.get();
			final var accountStore = createAccountStore(ledgers);
			final var tokenStore = createTokenStore(ledgers, accountStore, sideEffects);

			/* --- Execute the transaction and capture its results --- */
			final var dissociateLogic = dissociateLogicFactory.newDissociateLogic(
					validator, tokenStore, accountStore, dissociationFactory);
			dissociateLogic.dissociate(accountId, dissociateOp.tokenIds());
			return creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects, EMPTY_MEMO);
		}
	}

	protected class DissociatePrecompile extends AbstractDissociatePrecompile {
		@Override
		public TransactionBody.Builder body(final Bytes input) {
			dissociateOp = decoder.decodeDissociate(input);
			return syntheticTxnFactory.createDissociate(dissociateOp);
		}
	}

	protected class MultiDissociatePrecompile extends AbstractDissociatePrecompile {
		@Override
		public TransactionBody.Builder body(final Bytes input) {
			dissociateOp = decoder.decodeMultipleDissociations(input);
			return syntheticTxnFactory.createDissociate(dissociateOp);
		}
	}

	protected class MintPrecompile implements Precompile {
		private MintWrapper mintOp;

		@Override
		public TransactionBody.Builder body(final Bytes input) {
			mintOp = decoder.decodeMint(input);
			return syntheticTxnFactory.createMint(mintOp);
		}

		@Override
		public ExpirableTxnRecord.Builder run(
				final MessageFrame frame,
				final WorldLedgers ledgers
		) {
			Objects.requireNonNull(mintOp);

			/* --- Check required signatures --- */
			final var tokenId = Id.fromGrpcToken(mintOp.tokenType());
			final var hasRequiredSigs = validateKey(frame, tokenId.asEvmAddress(), sigsVerifier::hasActiveSupplyKey);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var sideEffects = sideEffectsFactory.get();
			final var scopedAccountStore = createAccountStore(ledgers);
			final var scopedTokenStore = createTokenStore(ledgers, scopedAccountStore, sideEffects);
			final var mintLogic = mintLogicFactory.newMintLogic(validator, scopedTokenStore, scopedAccountStore);

			/* --- Execute the transaction and capture its results --- */
			if (mintOp.type() == NON_FUNGIBLE_UNIQUE) {
				final var newMeta = mintOp.metadata();
				final var creationTime = recordsHistorian.nextFollowingChildConsensusTime();
				mintLogic.mint(tokenId, newMeta.size(), 0, newMeta, creationTime);
			} else {
				mintLogic.mint(tokenId, 0, mintOp.amount(), NO_METADATA, Instant.EPOCH);
			}
			return creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects, EMPTY_MEMO);
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
		private List<TokenTransferWrapper> transferOp;
		private TransactionBody.Builder syntheticTxn;

		@Override
		public TransactionBody.Builder body(final Bytes input) {
			transferOp = switch (functionId) {
				case ABI_ID_CRYPTO_TRANSFER -> decoder.decodeCryptoTransfer(input);
				case ABI_ID_TRANSFER_TOKENS -> decoder.decodeTransferTokens(input);
				case ABI_ID_TRANSFER_TOKEN -> decoder.decodeTransferToken(input);
				case ABI_ID_TRANSFER_NFTS -> decoder.decodeTransferNFTs(input);
				case ABI_ID_TRANSFER_NFT -> decoder.decodeTransferNFT(input);
				default -> throw new InvalidTransactionException(
						"Transfer precompile received unknown functionId=" + functionId + " (via " + input + ")",
						FAIL_INVALID);
			};
			this.syntheticTxn = syntheticTxnFactory.createCryptoTransfer(transferOp);
			return syntheticTxn;
		}

		@Override
		public ExpirableTxnRecord.Builder run(
				final MessageFrame frame,
				final WorldLedgers ledgers
		) {
			final var op = syntheticTxn.getCryptoTransfer();
			final var validity = impliedTransfersMarshal.validityWithCurrentProps(op);
			if (validity != ResponseCodeEnum.OK) {
				throw new InvalidTransactionException(validity);
			}

			var changes = constructBalanceChanges(transferOp);
			/* We remember this size to know to ignore receiverSigRequired=true for custom fee payments */
			final var numExplicitChanges = changes.size();

			final var validated = impliedTransfersMarshal.assessCustomFeesAndValidate(
					0,
					0,
					changes,
					NO_ALIASES,
					impliedTransfersMarshal.currentProps());
			final var assessmentStatus = validated.getMeta().code();
			validateTrue(assessmentStatus == OK, assessmentStatus);
			changes = validated.getAllBalanceChanges();

			final var sideEffects = sideEffectsFactory.get();
			final var hederaTokenStore = hederaTokenStoreFactory.newHederaTokenStore(
					ids,
					validator,
					sideEffects,
					NOOP_VIEWS_MANAGER,
					dynamicProperties,
					ledgers.tokenRels(), ledgers.nfts(), ledgers.tokens());
			hederaTokenStore.setAccountsLedger(ledgers.accounts());

			final var transferLogic = transferLogicFactory.newLogic(
					ledgers.accounts(), ledgers.nfts(), ledgers.tokenRels(), hederaTokenStore,
					sideEffects,
					NOOP_VIEWS_MANAGER,
					dynamicProperties,
					validator,
					null,
					recordsHistorian);

			for (int i = 0, n = changes.size(); i < n; i++) {
				final var change = changes.get(i);
				final var units = change.units();
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
					final var counterPartyAddress = asTypedSolidityAddress(change.counterPartyAccountId());
					hasReceiverSigIfReq = validateKey(frame, counterPartyAddress,
							sigsVerifier::hasActiveKeyOrNoReceiverSigReq);
				} else if (units > 0) {
					hasReceiverSigIfReq = validateKey(frame, change.getAccount().asEvmAddress(),
							sigsVerifier::hasActiveKeyOrNoReceiverSigReq);
				}
				validateTrue(hasReceiverSigIfReq, INVALID_SIGNATURE);
			}

			transferLogic.doZeroSum(changes);

			return creator.createSuccessfulSyntheticRecord(validated.getAssessedCustomFees(), sideEffects, EMPTY_MEMO);
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
										aaWith(fungibleTransfer.receiver, fungibleTransfer.amount)),
								BalanceChange.changingFtUnits(
										Id.fromGrpcToken(fungibleTransfer.getDenomination()),
										fungibleTransfer.getDenomination(),
										aaWith(fungibleTransfer.sender, -fungibleTransfer.amount))));
					} else if (fungibleTransfer.sender == null) {
						changes.add(
								BalanceChange.changingFtUnits(
										Id.fromGrpcToken(fungibleTransfer.getDenomination()),
										fungibleTransfer.getDenomination(),
										aaWith(fungibleTransfer.receiver, fungibleTransfer.amount)));
					} else {
						changes.add(
								BalanceChange.changingFtUnits(
										Id.fromGrpcToken(fungibleTransfer.getDenomination()),
										fungibleTransfer.getDenomination(),
										aaWith(fungibleTransfer.sender, -fungibleTransfer.amount)));
					}
				}
				if (changes.isEmpty()) {
					for (final var nftExchange : tokenTransferWrapper.nftExchanges()) {
						changes.add(
								BalanceChange.changingNftOwnership(
										Id.fromGrpcToken(nftExchange.getTokenType()),
										nftExchange.getTokenType(),
										nftExchange.asGrpc()
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
	}

	protected class BurnPrecompile implements Precompile {
		private BurnWrapper burnOp;

		@Override
		public TransactionBody.Builder body(final Bytes input) {
			burnOp = decoder.decodeBurn(input);
			return syntheticTxnFactory.createBurn(burnOp);
		}

		@Override
		public ExpirableTxnRecord.Builder run(
				final MessageFrame frame,
				final WorldLedgers ledgers
		) {
			Objects.requireNonNull(burnOp);

			/* --- Check required signatures --- */
			final var tokenId = Id.fromGrpcToken(burnOp.tokenType());
			final var hasRequiredSigs = validateKey(frame, tokenId.asEvmAddress(), sigsVerifier::hasActiveSupplyKey);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var sideEffects = sideEffectsFactory.get();
			final var scopedAccountStore = createAccountStore(ledgers);
			final var scopedTokenStore = createTokenStore(ledgers, scopedAccountStore, sideEffects);
			final var burnLogic = burnLogicFactory.newBurnLogic(scopedTokenStore, scopedAccountStore);

			/* --- Execute the transaction and capture its results --- */
			if (burnOp.type() == NON_FUNGIBLE_UNIQUE) {
				final var targetSerialNos = burnOp.serialNos();
				burnLogic.burn(tokenId, 0, targetSerialNos);
			} else {
				burnLogic.burn(tokenId, burnOp.amount(), NO_SERIAL_NOS);
			}
			return creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects, EMPTY_MEMO);
		}

		@Override
		public Bytes getSuccessResultFor(ExpirableTxnRecord.Builder childRecord) {
			final var receiptBuilder = childRecord.getReceiptBuilder();
			validateTrue(receiptBuilder != null, FAIL_INVALID);
			return encoder.encodeBurnSuccess(childRecord.getReceiptBuilder().getNewTotalSupply());
		}

		@Override
		public Bytes getFailureResultFor(ResponseCodeEnum status) {
			return encoder.encodeBurnFailure(status);
		}
	}

	/**
	 * Checks if a key implicit in a target address is active in the current frame using a {@link
	 * ContractActivationTest}.
	 *
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
	 *
	 * @param frame
	 * 		current frame
	 * @param target
	 * 		the element to test for key activation
	 * @param activationTest
	 * 		the function which should be invoked for key validation
	 * @return whether the implied key is active
	 */
	private boolean validateKey(
			final MessageFrame frame,
			final Address target,
			final ContractActivationTest activationTest
	) {
		final var recipient = frame.getRecipientAddress();
		final var contract = frame.getContractAddress();
		final var sender = frame.getSenderAddress();

		if (isDelegateCall(frame)) {
			return activationTest.apply(target, recipient, contract, recipient);
		} else {
			final var parentFrame = getParentFrame(frame);
			if (parentFrame.isPresent() && isDelegateCall(parentFrame.get())) {
				final var parentRecipient = parentFrame.get().getRecipientAddress();
				return activationTest.apply(target, parentRecipient, contract, sender);
			} else {
				return activationTest.apply(target, recipient, contract, sender);
			}
		}
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
		 *
		 * Note the target address might not imply an account key, but e.g. a token supply key.
		 *
		 * @param target
		 * 		an address with an implicit key understood by this implementation
		 * @param recipient
		 * 		the idealized account receiving the call operation
		 * @param contract
		 * 		the idealized account whose code is being executed
		 * @param activeContract
		 * 		the contract address that can activate a contract or delegatable contract key
		 * @return whether the implicit key has an active signature in this context
		 */
		boolean apply(Address target, Address recipient, Address contract, Address activeContract);
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
