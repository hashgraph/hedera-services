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
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
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
import com.hedera.services.store.tokens.views.UniqTokenViewsManager;
import com.hedera.services.txns.token.AssociateLogic;
import com.hedera.services.txns.token.BurnLogic;
import com.hedera.services.txns.token.DissociateLogic;
import com.hedera.services.txns.token.MintLogic;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
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
import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.tokens.views.UniqTokenViewsManager.NOOP_VIEWS_MANAGER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

@Singleton
public class HTSPrecompiledContract extends AbstractPrecompiledContract {
	private static final Bytes SUCCESS_RESULT = resultFrom(SUCCESS);
	private static final Bytes STATIC_CALL_REVERT_REASON = Bytes.of("HTS precompiles are not static".getBytes());
	private static final List<Long> NO_SERIAL_NOS = Collections.emptyList();
	private static final List<ByteString> NO_METADATA = Collections.emptyList();
	private static final List<FcAssessedCustomFee> NO_CUSTOM_FEES = Collections.emptyList();

	/* Precompiles cannot change treasury accounts */
	public static final TypedTokenStore.LegacyTreasuryAdder NOOP_TREASURY_ADDER = (aId, tId) -> {
	};
	public static final TypedTokenStore.LegacyTreasuryRemover NOOP_TREASURY_REMOVER = (aId, tId) -> {
	};

	private MintLogicFactory mintLogicFactory = MintLogic::new;
	private BurnLogicFactory burnLogicFactory = BurnLogic::new;
	private AssociateLogicFactory associateLogicFactory = AssociateLogic::new;
	private DissociateLogicFactory dissociateLogicFactory = DissociateLogic::new;
	private TokenStoreFactory tokenStoreFactory = TypedTokenStore::new;
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

	//cryptoTransfer(TokenTransferList[] calldata tokenTransfers)
	protected static final int ABI_ID_CRYPTO_TRANSFER = 0x189a554c;
	//transferTokens(address token, address[] calldata accountId, int64[] calldata amount)
	protected static final int ABI_ID_TRANSFER_TOKENS = 0x82bba493;
	//transferToken(address token, address sender, address recipient, int64 amount)
	protected static final int ABI_ID_TRANSFER_TOKEN = 0xeca36917;
	//transferNFTs(address token, address[] calldata sender, address[] calldata receiver, int64[] calldata serialNumber)
	protected static final int ABI_ID_TRANSFER_NFTS = 0x2c4ba191;
	//transferNFT(address token,  address sender, address recipient, int64 serialNum)
	protected static final int ABI_ID_TRANSFER_NFT = 0x7c502795;
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
			final SoliditySigsVerifier sigsVerifier,
			final DecodingFacade decoder,
			final SyntheticTxnFactory syntheticTxnFactory,
			final EntityCreator creator,
			final DissociationFactory dissociationFactory
	) {
		super("HTS", gasCalculator);

		this.decoder = decoder;
		this.validator = validator;
		this.creator = creator;
		this.sigsVerifier = sigsVerifier;
		this.dynamicProperties = dynamicProperties;
		this.recordsHistorian = recordsHistorian;
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.dissociationFactory = dissociationFactory;
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
				return computeCryptoTransfer(input, messageFrame);
			case ABI_ID_TRANSFER_TOKENS:
				return computeTransferTokens(input, messageFrame);
			case ABI_ID_TRANSFER_TOKEN:
				return computeTransferToken(input, messageFrame);
			case ABI_ID_TRANSFER_NFTS:
				return computeTransferNfts(input, messageFrame);
			case ABI_ID_TRANSFER_NFT:
				return computeTransferNft(input, messageFrame);
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
	protected Bytes computeCryptoTransfer(final Bytes input, final MessageFrame messageFrame) {
		return null;
	}

	@SuppressWarnings("unused")
	protected Bytes computeTransferTokens(final Bytes input, final MessageFrame messageFrame) {
		return null;
	}

	@SuppressWarnings("unused")
	protected Bytes computeTransferToken(final Bytes input, final MessageFrame messageFrame) {
		final Bytes tokenAddress = Address.wrap(input.slice(16, 20));
		final Bytes fromAddress = Address.wrap(input.slice(48, 20));
		final Bytes toAddress = Address.wrap(input.slice(80, 20));
		final BigInteger amount = input.slice(100, 32).toBigInteger();

		final var from = EntityIdUtils.accountParsedFromSolidityAddress(fromAddress.toArray());
		final var token = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArray());
		final var to = EntityIdUtils.accountParsedFromSolidityAddress(toAddress.toArray());

		return null;
	}

	@SuppressWarnings("unused")
	protected Bytes computeTransferNfts(final Bytes input, final MessageFrame messageFrame) {
		return null;
	}

	@SuppressWarnings("unused")
	protected Bytes computeTransferNft(final Bytes input, final MessageFrame messageFrame) {
		return null;
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

		final var synthBody = precompile.body(input);
		Bytes result = SUCCESS_RESULT;
		ExpirableTxnRecord.Builder childRecord;
		try {
			childRecord = precompile.run(recipient, contract, ledgers);
			ledgers.commit();
		} catch (InvalidTransactionException e) {
			final var status = e.getResponseCode();
			childRecord = creator.createUnsuccessfulSyntheticRecord(status);
			result = resultFrom(status);
		}

		updater.manageInProgressRecord(recordsHistorian, childRecord, synthBody);

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
	interface AccountStoreFactory {
		AccountStore newAccountStore(
				OptionValidator validator,
				GlobalDynamicProperties dynamicProperties,
				BackingStore<EntityNum, MerkleAccount> accounts);
	}

	@FunctionalInterface
	interface TokenStoreFactory {
		TypedTokenStore newTokenStore(
				AccountStore accountStore,
				BackingStore<TokenID, MerkleToken> tokens,
				BackingStore<NftId, MerkleUniqueToken> uniqueTokens,
				BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> tokenRels,
				UniqTokenViewsManager uniqTokenViewsManager,
				TypedTokenStore.LegacyTreasuryAdder treasuryAdder,
				TypedTokenStore.LegacyTreasuryRemover treasuryRemover,
				SideEffectsTracker sideEffectsTracker);
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

	public void setBurnLogicFactory(final BurnLogicFactory burnLogicFactory) {
		this.burnLogicFactory = burnLogicFactory;
	}

	void setDissociateLogicFactory(final DissociateLogicFactory dissociateLogicFactory) {
		this.dissociateLogicFactory = dissociateLogicFactory;
	}

	void setTokenStoreFactory(final TokenStoreFactory tokenStoreFactory) {
		this.tokenStoreFactory = tokenStoreFactory;
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
