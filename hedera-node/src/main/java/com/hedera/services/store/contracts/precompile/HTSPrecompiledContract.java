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
import static com.hedera.services.ledger.ids.ExceptionalEntityIdSource.NOOP_ID_SOURCE;
import static com.hedera.services.store.tokens.views.UniqueTokenViewsManager.NOOP_VIEWS_MANAGER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

@Singleton
public class HTSPrecompiledContract extends AbstractPrecompiledContract {
	private static final Bytes SUCCESS_RESULT = resultFrom(SUCCESS);
	private static final Bytes STATIC_CALL_REVERT_REASON = Bytes.of("HTS precompiles are not static".getBytes());
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
	private final GlobalDynamicProperties dynamicProperties;
	private final OptionValidator validator;
	private final SoliditySigsVerifier sigsVerifier;
	private final AccountRecordsHistorian recordsHistorian;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final DissociationFactory dissociationFactory;

	private final ImpliedTransfersMarshal impliedTransfersMarshal;

	//cryptoTransfer(TokenTransferList[] calldata tokenTransfers)
	protected static final int ABI_ID_CRYPTO_TRANSFER = 0x189a554c;
	//transferTokens(address token, address[] calldata accountId, int64[] calldata amount)
	protected static final int ABI_ID_TRANSFER_TOKENS = 0x82bba493;
	//transferToken(address token, address sender, address recipient, int64 amount)
	protected static final int ABI_ID_TRANSFER_TOKEN = 0xeca36917;
	//transferNFTs(address token, address[] calldata sender, address[] calldata receiver, int64[] calldata serialNumber)
	protected static final int ABI_ID_TRANSFER_NFTS = 0x2c4ba191;
	//transferNFT(address token,  address sender, address recipient, int64 serialNum)
	protected static final int ABI_ID_TRANSFER_NFT = 0x5cfc9011;
	//mintToken(address token, uint64 amount, bytes calldata metadata)
	protected static final int ABI_ID_MINT_TOKEN = 0x36dcedf0;
	//burnToken(address token, uint64 amount, int64[] calldata serialNumbers)
	protected static final int ABI_ID_BURN_TOKEN = 0xacb9cff9;
	//associateTokens(address account, address[] calldata tokens)
	protected static final int ABI_ID_ASSOCIATE_TOKENS = 0x2e63879b;
	//associateToken(address account, address token)
	protected static final int ABI_ID_ASSOCIATE_TOKEN = 0x49146bde;
	//dissociateTokens(address account, address[] calldata tokens)
	protected static final int ABI_ID_DISSOCIATE_TOKENS = 0x78b63918;
	//dissociateToken(address account, address token)
	protected static final int ABI_ID_DISSOCIATE_TOKEN = 0x099794e8;

	@Inject
	public HTSPrecompiledContract(
			final OptionValidator validator,
			final GlobalDynamicProperties dynamicProperties,
			final GasCalculator gasCalculator,
			final AccountRecordsHistorian recordsHistorian,
			final TxnAwareSoliditySigsVerifier sigsVerifier,
			final DecodingFacade decoder,
			final SyntheticTxnFactory syntheticTxnFactory,
			final ExpiringCreations creator,
			final DissociationFactory dissociationFactory,
			final ImpliedTransfersMarshal impliedTransfersMarshal
	) {
		super("HTS", gasCalculator);

		this.decoder = decoder;

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
		return Gas.of(10_000); // revisit cost, this is arbitrary
	}

	@Override
	public Bytes compute(final Bytes input, final MessageFrame messageFrame) {
		if (messageFrame.isStatic()) {
			messageFrame.setRevertReason(STATIC_CALL_REVERT_REASON);
			return null;
		}

		final var functionId = input.getInt(0);
		switch (functionId) {
			case ABI_ID_CRYPTO_TRANSFER:
			case ABI_ID_TRANSFER_TOKENS:
			case ABI_ID_TRANSFER_TOKEN:
			case ABI_ID_TRANSFER_NFTS:
			case ABI_ID_TRANSFER_NFT:
				return computeTransfer(input, messageFrame);
			case ABI_ID_MINT_TOKEN:
				return computeMintToken(input, messageFrame);
			case ABI_ID_BURN_TOKEN:
				return computeBurnToken(input, messageFrame);
			case ABI_ID_ASSOCIATE_TOKENS:
				return computeAssociateTokens(input, messageFrame);
			case ABI_ID_ASSOCIATE_TOKEN:
				return computeAssociateToken(input, messageFrame);
			case ABI_ID_DISSOCIATE_TOKENS:
				return computeDissociateTokens(input, messageFrame);
			case ABI_ID_DISSOCIATE_TOKEN:
				return computeDissociateToken(input, messageFrame);
			default:
				return null;
		}
	}

	@SuppressWarnings("unused")
	protected Bytes computeTransfer(final Bytes input, final MessageFrame frame) {
		return computeInternal(frame, input, new TransferPrecompile());
	}

	@SuppressWarnings("unused")
	protected Bytes computeMintToken(final Bytes input, final MessageFrame frame) {
		return computeInternal(frame, input, new MintPrecompile());
	}

	@SuppressWarnings("unused")
	protected Bytes computeBurnToken(final Bytes input, final MessageFrame frame) {
		return computeInternal(frame, input, new BurnPrecompile());
	}

	@SuppressWarnings("unused")
	protected Bytes computeAssociateTokens(final Bytes input, final MessageFrame frame) {
		return computeInternal(frame, input, new MultiAssociatePrecompile());
	}

	protected Bytes computeAssociateToken(final Bytes input, final MessageFrame frame) {
		return computeInternal(frame, input, new AssociatePrecompile());
	}

	protected Bytes computeDissociateTokens(final Bytes input, final MessageFrame frame) {
		return computeInternal(frame, input, new MultiDissociatePrecompile());
	}

	protected Bytes computeDissociateToken(final Bytes input, final MessageFrame frame) {
		return computeInternal(frame, input, new DissociatePrecompile());
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

	@SuppressWarnings("rawtypes")
	private Bytes computeInternal(
			final MessageFrame frame,
			final Bytes input,
			final Precompile precompile
	) {
		final var contract = frame.getContractAddress();
		final var recipient = frame.getRecipientAddress();
		final var updater = (AbstractLedgerWorldUpdater) frame.getWorldUpdater();
		final var ledgers = updater.wrappedTrackingLedgers();

		TransactionBody.Builder synthBody = TransactionBody.newBuilder();
		Bytes result = SUCCESS_RESULT;
		ExpirableTxnRecord.Builder childRecord;
		try {
			synthBody = precompile.body(input);
			childRecord = precompile.run(recipient, contract, ledgers);
			ledgers.commit();
		} catch (InvalidTransactionException e) {
			final var status = e.getResponseCode();
			childRecord = creator.createUnsuccessfulSyntheticRecord(status);
			result = resultFrom(status);
		} catch (Exception e) {
			final var status = ResponseCodeEnum.FAIL_INVALID;
			childRecord = creator.createUnsuccessfulSyntheticRecord(status);
			result = resultFrom(status);
		}

		/*-- The updater here should always have a parent updater --*/
		if(updater.parentUpdater().isPresent()) {
			final var parent = (AbstractLedgerWorldUpdater) updater.parentUpdater().get();
			parent.manageInProgressRecord(recordsHistorian, childRecord, synthBody);
		} else {
			throw new InvalidTransactionException(FAIL_INVALID);
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
		TransferLogic newLogic(TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger,
							   TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger,
							   TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger,
							   HederaTokenStore tokenStore,
							   SideEffectsTracker sideEffectsTracker,
							   UniqueTokenViewsManager tokenViewsManager,
							   GlobalDynamicProperties dynamicProperties,
							   OptionValidator validator);
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
		TransactionBody.Builder body(final Bytes input);

		ExpirableTxnRecord.Builder run(final Address recipient, final Address contract, final WorldLedgers ledgers);
	}

	private abstract class AbstractAssociatePrecompile implements Precompile {
		protected SyntheticTxnFactory.Association associateOp;

		@Override
		public ExpirableTxnRecord.Builder run(
				final Address recipient,
				final Address contract,
				final WorldLedgers ledgers
		) {
			Objects.requireNonNull(associateOp);

			/* --- Check required signatures --- */
			final var accountId = Id.fromGrpcAccount(associateOp.getAccountId());
			final var hasRequiredSigs = sigsVerifier.hasActiveKey(accountId, recipient, contract);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var sideEffects = sideEffectsFactory.get();
			final var accountStore = createAccountStore(ledgers);
			final var tokenStore = createTokenStore(ledgers, accountStore, sideEffects);

			/* --- Execute the transaction and capture its results --- */
			final var associateLogic = associateLogicFactory.newAssociateLogic(
					tokenStore, accountStore, dynamicProperties);
			associateLogic.associate(accountId, associateOp.getTokenIds());
			return creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects);
		}
	}

	private class AssociatePrecompile extends AbstractAssociatePrecompile {
		@Override
		public TransactionBody.Builder body(final Bytes input) {
			associateOp = decoder.decodeAssociation(input);
			return syntheticTxnFactory.createAssociate(associateOp);
		}
	}

	private class MultiAssociatePrecompile extends AbstractAssociatePrecompile {
		@Override
		public TransactionBody.Builder body(final Bytes input) {
			associateOp = decoder.decodeMultipleAssociations(input);
			return syntheticTxnFactory.createAssociate(associateOp);
		}
	}

	private abstract class AbstractDissociatePrecompile implements Precompile {
		protected SyntheticTxnFactory.Dissociation dissociateOp;

		@Override
		public ExpirableTxnRecord.Builder run(
				final Address recipient,
				final Address contract,
				final WorldLedgers ledgers
		) {
			Objects.requireNonNull(dissociateOp);

			/* --- Check required signatures --- */
			final var accountId = Id.fromGrpcAccount(dissociateOp.getAccountId());
			final var hasRequiredSigs = sigsVerifier.hasActiveKey(accountId, recipient, contract);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var sideEffects = sideEffectsFactory.get();
			final var accountStore = createAccountStore(ledgers);
			final var tokenStore = createTokenStore(ledgers, accountStore, sideEffects);

			/* --- Execute the transaction and capture its results --- */
			final var dissociateLogic = dissociateLogicFactory.newDissociateLogic(
					validator, tokenStore, accountStore, dissociationFactory);
			dissociateLogic.dissociate(accountId, dissociateOp.getTokenIds());
			return creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects);
		}
	}

	private class DissociatePrecompile extends AbstractDissociatePrecompile {
		@Override
		public TransactionBody.Builder body(final Bytes input) {
			dissociateOp = decoder.decodeDissociate(input);
			return syntheticTxnFactory.createDissociate(dissociateOp);
		}
	}

	private class MultiDissociatePrecompile extends AbstractDissociatePrecompile {
		@Override
		public TransactionBody.Builder body(final Bytes input) {
			dissociateOp = decoder.decodeMultipleDissociations(input);
			return syntheticTxnFactory.createDissociate(dissociateOp);
		}
	}

	private class MintPrecompile implements Precompile {
		private SyntheticTxnFactory.MintWrapper mintOp;

		@Override
		public TransactionBody.Builder body(final Bytes input) {
			mintOp = decoder.decodeMint(input);
			return syntheticTxnFactory.createMint(mintOp);
		}

		@Override
		public ExpirableTxnRecord.Builder run(
				final Address recipient,
				final Address contract,
				final WorldLedgers ledgers
		) {
			Objects.requireNonNull(mintOp);

			/* --- Check required signatures --- */
			final var tokenId = Id.fromGrpcToken(mintOp.getTokenType());
			final var hasRequiredSigs = sigsVerifier.hasActiveSupplyKey(tokenId, recipient, contract);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var sideEffects = sideEffectsFactory.get();
			final var scopedAccountStore = createAccountStore(ledgers);
			final var scopedTokenStore = createTokenStore(ledgers, scopedAccountStore, sideEffects);
			final var mintLogic = mintLogicFactory.newMintLogic(validator, scopedTokenStore, scopedAccountStore);

			/* --- Execute the transaction and capture its results --- */
			if (mintOp.type() == NON_FUNGIBLE_UNIQUE) {
				final var newMeta = mintOp.getMetadata();
				final var creationTime = recordsHistorian.nextFollowingChildConsensusTime();
				mintLogic.mint(tokenId, newMeta.size(), 0, newMeta, creationTime);
			} else {
				mintLogic.mint(tokenId, 0, mintOp.getAmount(), NO_METADATA, Instant.EPOCH);
			}
			return creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects);
		}
	}

	private class TransferPrecompile implements Precompile {
		private SyntheticTxnFactory.TokenTransferLists transferOp;

		@Override
		public TransactionBody.Builder body(final Bytes input) {
			switch (input.getInt(0)) {
				case ABI_ID_CRYPTO_TRANSFER:
					transferOp = decoder.decodeCryptoTransfer(input);
					break;
				case ABI_ID_TRANSFER_TOKENS:
					transferOp = decoder.decodeTransferTokens(input);
					break;
				case ABI_ID_TRANSFER_TOKEN:
					transferOp = decoder.decodeTransferToken(input);
					break;
				case ABI_ID_TRANSFER_NFTS:
					transferOp = decoder.decodeTransferNFTs(input);
					break;
				case ABI_ID_TRANSFER_NFT:
					transferOp = decoder.decodeTransferNFT(input);
					break;
				default:
					throw new InvalidTransactionException(FAIL_INVALID);
			}
			return syntheticTxnFactory.createCryptoTransfer(transferOp.getNftExchanges(),
					transferOp.getFungibleTransfers());
		}

		private List<BalanceChange> constructBalanceChanges(SyntheticTxnFactory.TokenTransferLists transferOp) {
			List<BalanceChange> changes = new ArrayList<>();
			for (SyntheticTxnFactory.FungibleTokenTransfer fungibleTransfer : transferOp.getFungibleTransfers()) {
				if (fungibleTransfer.sender != null && fungibleTransfer.receiver != null) {
					changes.addAll(List.of(
							BalanceChange.changingFtUnits(
									Id.fromGrpcToken(fungibleTransfer.getDenomination()),
									fungibleTransfer.getDenomination(),
									AccountAmount.newBuilder().setAccountID(fungibleTransfer.receiver).setAmount(fungibleTransfer.amount).build()
							),
							BalanceChange.changingFtUnits(
									Id.fromGrpcToken(fungibleTransfer.getDenomination()),
									fungibleTransfer.getDenomination(),
									AccountAmount.newBuilder().setAccountID(fungibleTransfer.sender).setAmount(-fungibleTransfer.amount).build()
							))
					);
				} else if (fungibleTransfer.sender == null) {
					changes.add(
							BalanceChange.changingFtUnits(
									Id.fromGrpcToken(fungibleTransfer.getDenomination()),
									fungibleTransfer.getDenomination(),
									AccountAmount.newBuilder().setAccountID(fungibleTransfer.receiver).setAmount(fungibleTransfer.amount).build()
							)
					);
				} else {
					changes.add(
							BalanceChange.changingFtUnits(
									Id.fromGrpcToken(fungibleTransfer.getDenomination()),
									fungibleTransfer.getDenomination(),
									AccountAmount.newBuilder().setAccountID(fungibleTransfer.sender).setAmount(-fungibleTransfer.amount).build()
							)
					);
				}
			}

			if (changes.isEmpty()) {
				for (SyntheticTxnFactory.NftExchange nftExchange : transferOp.getNftExchanges()) {
					changes.add(
							BalanceChange.changingNftOwnership(
									Id.fromGrpcToken(nftExchange.getTokenType()),
									nftExchange.getTokenType(),
									nftExchange.nftTransfer()
							)
					);
				}
			}

			return changes;
		}

		@Override
		public ExpirableTxnRecord.Builder run(
				final Address recipient,
				final Address contract,
				final WorldLedgers ledgers
		) {
			final List<BalanceChange> changes = constructBalanceChanges(transferOp);
			var validated = impliedTransfersMarshal.assessCustomFeesAndValidate(
					changes,
					0,
					impliedTransfersMarshal.currentProps()
			);

			final var assessmentStatus = validated.getMeta().code();
			validateTrue(assessmentStatus == OK, assessmentStatus);

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
					validator);

			for (final var change : changes) {
				final var units = change.units();
				if (units < 0) {
					final var hasSenderSig = sigsVerifier.hasActiveKey(change.getAccount(), recipient, contract);
					validateTrue(hasSenderSig, INVALID_SIGNATURE);
				} else if (units > 0) {
					/* Need to add the Id.asSolidityAddress() method. */
					final var hasReceiverSigIfReq =
							sigsVerifier.hasActiveKeyOrNoReceiverSigReq(change.getAccount().asEvmAddress(),
									recipient, contract);
					validateTrue(hasReceiverSigIfReq, INVALID_SIGNATURE);
				}
			}

			transferLogic.transfer(validated.getAllBalanceChanges());

			return creator.createSuccessfulSyntheticRecord(validated.getAssessedCustomFees(),
					sideEffects);
		}
	}

	private class BurnPrecompile implements Precompile {
		private SyntheticTxnFactory.BurnWrapper burnOp;

		@Override
		public TransactionBody.Builder body(final Bytes input) {
			burnOp = decoder.decodeBurn(input);
			return syntheticTxnFactory.createBurn(burnOp);
		}

		@Override
		public ExpirableTxnRecord.Builder run(
				final Address recipient,
				final Address contract,
				final WorldLedgers ledgers
		) {
			Objects.requireNonNull(burnOp);

			/* --- Check required signatures --- */
			final var tokenId = Id.fromGrpcToken(burnOp.getTokenType());
			final var hasRequiredSigs = sigsVerifier.hasActiveSupplyKey(tokenId, recipient, contract);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var sideEffects = sideEffectsFactory.get();
			final var scopedAccountStore = createAccountStore(ledgers);
			final var scopedTokenStore = createTokenStore(ledgers, scopedAccountStore, sideEffects);
			final var burnLogic = burnLogicFactory.newBurnLogic(scopedTokenStore, scopedAccountStore);

			/* --- Execute the transaction and capture its results --- */
			if (burnOp.type() == NON_FUNGIBLE_UNIQUE) {
				final var targetSerialNos = burnOp.getSerialNos();
				burnLogic.burn(tokenId, 0, targetSerialNos);
			} else {
				burnLogic.burn(tokenId, burnOp.getAmount(), NO_SERIAL_NOS);
			}
			return creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects);
		}
	}

	/* --- Only used by unit tests --- */
	void setMintLogicFactory(final MintLogicFactory mintLogicFactory) {
		this.mintLogicFactory = mintLogicFactory;
	}

	/* --- Only used by unit tests --- */
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
}