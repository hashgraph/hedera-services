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
import com.hedera.services.txns.token.DissociateLogic;
import com.hedera.services.txns.token.MintLogic;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.tokens.views.UniqTokenViewsManager.NOOP_VIEWS_MANAGER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static java.util.Collections.singletonList;

@Singleton
public class HTSPrecompiledContract extends AbstractPrecompiledContract {

	private static final List<FcAssessedCustomFee> NO_CUSTOM_FEES = Collections.emptyList();

	public static final TypedTokenStore.LegacyTreasuryAdder NOOP_TREASURY_ADDER = (aId, tId) -> {
		/* Precompiles cannot change treasury accounts */
	};
	public static final TypedTokenStore.LegacyTreasuryRemover NOOP_TREASURY_REMOVER = (aId, tId) -> {
		/* Precompiles cannot change treasury accounts */
	};

	private MintLogicFactory mintLogicFactory = MintLogic::new;
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
	private DissociationFactory dissociationFactory;

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
			messageFrame.setRevertReason(
					Bytes.of("Cannot interact with HTS in a static call".getBytes(StandardCharsets.UTF_8)));
			return null;
		}

		int functionId = input.getInt(0);
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
			default: {
				// Null is the "Precompile Failed" signal
				return null;
			}
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
		/* --- Get the frame context --- */
		final var contract = frame.getContractAddress();
		final var recipient = frame.getRecipientAddress();
		final var updater = (AbstractLedgerWorldUpdater) frame.getWorldUpdater();
		final var ledgers = updater.wrappedTrackingLedgers();

		/* --- Parse the input --- */
		final var mintOp = decoder.decodeMint(input);
		final var synthBody = syntheticTxnFactory.createNonFungibleMint(mintOp);
		final var newMeta = mintOp.getMetadata();

		Bytes result;
		ExpirableTxnRecord.Builder childRecord;
		try {
			/* --- Check the required supply key has an active signature --- */
			final var tokenId = Id.fromGrpcToken(mintOp.getTokenType());
			final var hasRequiredSigs = sigsVerifier.hasActiveSupplyKey(tokenId, recipient, contract);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var sideEffects = sideEffectsFactory.get();
			final var scopedAccountStore = createAccountStore(ledgers);
			final var scopedTokenStore = createTokenStore(ledgers, scopedAccountStore, sideEffects);
			final var mintLogic = mintLogicFactory.newLogic(validator, scopedTokenStore, scopedAccountStore);

			/* --- Execute the transaction and capture its results --- */
			final var creationTime = recordsHistorian.nextFollowingChildConsensusTime();
			mintLogic.mint(tokenId, newMeta.size(), 0, newMeta, creationTime);
			childRecord = creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects);
			result = UInt256.valueOf(ResponseCodeEnum.SUCCESS_VALUE);
			ledgers.commit();
		} catch (InvalidTransactionException e) {
			childRecord = creator.createUnsuccessfulSyntheticRecord(e.getResponseCode());
			result = UInt256.valueOf(e.getResponseCode().getNumber());
		}

		/* --- And track the created child record --- */
		updater.manageInProgressRecord(recordsHistorian, childRecord, synthBody);

		return result;
	}

	@SuppressWarnings("unused")
	protected Bytes computeBurnToken(final Bytes input, final MessageFrame messageFrame) {
		return null;
	}

	@SuppressWarnings("unused")
	protected Bytes computeAssociateTokens(final Bytes input, final MessageFrame messageFrame) {
		return null;
	}

	protected Bytes computeAssociateToken(final Bytes input, final MessageFrame messageFrame) {
		/* --- Get the frame context --- */
		final var updater = (AbstractLedgerWorldUpdater) messageFrame.getWorldUpdater();
		final var ledgers = updater.wrappedTrackingLedgers();
		final var contract = messageFrame.getContractAddress();
		final var recipient = messageFrame.getRecipientAddress();

		/* --- Parse the input --- */
		final var associateOp = decoder.decodeAssociate(input);
		final var synthBody = syntheticTxnFactory.createAssociate(associateOp);

		ExpirableTxnRecord.Builder childRecord;
		Bytes result;

		try {
			/* --- Check the required key has an active signature --- */
			final var hasRequiredSigs =
					sigsVerifier.hasActiveKey(Id.fromGrpcAccount(associateOp.getAccountID()),
							recipient, contract);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var sideEffects = sideEffectsFactory.get();
			final var accountStore = createAccountStore(ledgers);
			final var tokenStore = createTokenStore(ledgers, accountStore, sideEffects);

			/* --- Execute the transaction and capture its results --- */
			final var associateLogic =
					associateLogicFactory.newAssociateLogic(tokenStore, accountStore, dynamicProperties);
			associateLogic.associate(Id.fromGrpcAccount(associateOp.getAccountID()),
					singletonList(associateOp.getTokenID()));
			ledgers.commit();

			childRecord = creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects);
			result = UInt256.valueOf(ResponseCodeEnum.SUCCESS_VALUE);
		} catch (InvalidTransactionException e) {
			childRecord = creator.createUnsuccessfulSyntheticRecord(e.getResponseCode());
			result = UInt256.valueOf(e.getResponseCode().getNumber());
		}

		/* --- And track the created child record --- */
		updater.manageInProgressRecord(recordsHistorian, childRecord, synthBody);
		return result;
	}

	@SuppressWarnings("unused")
	protected Bytes computeDissociateTokens(final Bytes input, final MessageFrame messageFrame) {
		return null;
	}

	@SuppressWarnings("unused")
	protected Bytes computeDissociateToken(final Bytes input, final MessageFrame messageFrame) {
		/* --- Get the frame context --- */
		final var updater = (AbstractLedgerWorldUpdater) messageFrame.getWorldUpdater();
		final var ledgers = updater.wrappedTrackingLedgers();
		final var contract = messageFrame.getContractAddress();
		final var recipient = messageFrame.getRecipientAddress();

		/* --- Parse the input --- */
		final var dissociateOp = decoder.decodeDissociate(input);
		final var synthBody = syntheticTxnFactory.createDissociate(dissociateOp);

		ExpirableTxnRecord.Builder childRecord;
		Bytes result;

		try {
			/* --- Check the required key has an active signature --- */
			final var hasRequiredSigs =
					sigsVerifier.hasActiveKey(Id.fromGrpcAccount(dissociateOp.getAccountID()), recipient, contract);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* --- Build the necessary infrastructure to execute the transaction --- */
			final var sideEffects =  sideEffectsFactory.get();
			final var accountStore = createAccountStore(ledgers);
			final var tokenStore = createTokenStore(ledgers, accountStore, sideEffects);

			/* --- Execute the transaction and capture its results --- */
			DissociateLogic dissociateLogic =
					dissociateLogicFactory.newDissociateLogic(validator, tokenStore, accountStore, dissociationFactory);
			dissociateLogic.dissociate(Id.fromGrpcAccount(dissociateOp.getAccountID()), singletonList(dissociateOp.getTokenID()));
			ledgers.commit();

			childRecord = creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects);
			result = UInt256.valueOf(ResponseCodeEnum.SUCCESS_VALUE);
		} catch (InvalidTransactionException e) {
			childRecord = creator.createUnsuccessfulSyntheticRecord(e.getResponseCode());
			result = UInt256.valueOf(e.getResponseCode().getNumber());
		}
		/* --- And track the created child record --- */
		updater.manageInProgressRecord(recordsHistorian, childRecord, synthBody);
		return result;
	}

	/* Helpers */
	private AccountStore createAccountStore(final WorldLedgers ledgers) {
		return accountStoreFactory.newAccountStore(validator, dynamicProperties, ledgers.accounts());
	}

	private TypedTokenStore createTokenStore(
			final WorldLedgers ledgers,
			final AccountStore accountStore,
			final SideEffectsTracker sideEffects) {
		return tokenStoreFactory.newTokenStore(
				accountStore, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels(),
				NOOP_VIEWS_MANAGER, NOOP_TREASURY_ADDER, NOOP_TREASURY_REMOVER,
				sideEffects);
	}

	/* --- Constructor functional interfaces for mocking --- */
	@FunctionalInterface
	interface MintLogicFactory {
		MintLogic newLogic(OptionValidator validator, TypedTokenStore tokenStore, AccountStore accountStore);
	}

	@FunctionalInterface
	interface AssociateLogicFactory {
		AssociateLogic newAssociateLogic(final TypedTokenStore tokenStore,
										 final AccountStore accountStore,
										 final GlobalDynamicProperties dynamicProperties);
	}

	@FunctionalInterface
	interface DissociateLogicFactory {
		DissociateLogic newDissociateLogic(final OptionValidator validator,
										   final TypedTokenStore tokenStore,
										   final AccountStore accountStore,
										   final DissociationFactory dissociationFactory);
	}

	@FunctionalInterface
	interface AccountStoreFactory {
		AccountStore newAccountStore(
				final OptionValidator validator,
				final GlobalDynamicProperties dynamicProperties,
				final BackingStore<AccountID, MerkleAccount> accounts);
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

	/* --- Only used by unit tests --- */
	void setMintLogicFactory(final MintLogicFactory mintLogicFactory) {
		this.mintLogicFactory = mintLogicFactory;
	}

	/* --- Only used by unit tests --- */
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