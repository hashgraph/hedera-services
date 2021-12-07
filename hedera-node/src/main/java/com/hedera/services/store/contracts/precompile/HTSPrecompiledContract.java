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
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.HederaLedger;
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
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.tokens.views.UniqTokenViewsManager;
import com.hedera.services.txns.token.MintLogic;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.expiry.ExpiringCreations.EMPTY_MEMO;
import static com.hedera.services.store.tokens.views.UniqTokenViewsManager.NOOP_VIEWS_MANAGER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

@Singleton
public class HTSPrecompiledContract extends AbstractPrecompiledContract {
	private static final Logger LOG = LogManager.getLogger(HTSPrecompiledContract.class);

	public static final List<FcAssessedCustomFee> NO_CUSTOM_FEES = Collections.emptyList();

	public static final TypedTokenStore.LegacyTreasuryAdder NOOP_TREASURY_ADDER = (aId, tId) -> {
		/* Precompiles cannot change treasury accounts */
	};
	public static final TypedTokenStore.LegacyTreasuryRemover NOOP_TREASURY_REMOVER = (aId, tId) -> {
		/* Precompiles cannot change treasury accounts */
	};

	private MintLogicFactory mintLogicFactory = MintLogic::new;
	private TokenStoreFactory tokenStoreFactory = TypedTokenStore::new;
	private AccountStoreFactory accountStoreFactory = AccountStore::new;
	private Supplier<SideEffectsTracker> sideEffectsFactory = SideEffectsTracker::new;

	private final HederaLedger ledger;
	private final EntityCreator creator;
	private final DecodingFacade decoder;
	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final GlobalDynamicProperties dynamicProperties;
	private final OptionValidator validator;
	private final SoliditySigsVerifier sigsVerifier;
	private final AccountRecordsHistorian recordsHistorian;
	private final SyntheticTxnFactory syntheticTxnFactory;

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
			final HederaLedger ledger,
			final AccountStore accountStore,
			final OptionValidator validator,
			final TypedTokenStore tokenStore,
			final GlobalDynamicProperties dynamicProperties,
			final GasCalculator gasCalculator,
			final AccountRecordsHistorian recordsHistorian,
			final SoliditySigsVerifier sigsVerifier,
			final DecodingFacade decoder,
			final SyntheticTxnFactory syntheticTxnFactory,
			final EntityCreator creator
	) {
		super("HTS", gasCalculator);

		this.ledger = ledger;
		this.decoder = decoder;
		this.validator = validator;
		this.creator = creator;
		this.tokenStore = tokenStore;
		this.accountStore = accountStore;
		this.sigsVerifier = sigsVerifier;
		this.dynamicProperties = dynamicProperties;
		this.recordsHistorian = recordsHistorian;
		this.syntheticTxnFactory = syntheticTxnFactory;
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

		// validate addresses, associations, etc

		final List<BalanceChange> changes = new ArrayList<>();
		changes.add(
				BalanceChange.changingFtUnits(
						Id.fromGrpcToken(token),
						token,
						AccountAmount.newBuilder().setAccountID(from).setAmount(-amount.longValue()).build()
				));
		changes.add(
				BalanceChange.changingFtUnits(
						Id.fromGrpcToken(token),
						token,
						AccountAmount.newBuilder().setAccountID(to).setAmount(amount.longValue()).build()
				));

		ResponseCodeEnum responseCode;
		try {
			ledger.doZeroSum(changes);
			responseCode = ResponseCodeEnum.SUCCESS;
		} catch (InvalidTransactionException ite) {
			responseCode = ite.getResponseCode();
			if (responseCode == FAIL_INVALID) {
				LOG.warn("HTS Precompiled Contract failed, status {} ", responseCode);
			}
		}

		return UInt256.valueOf(responseCode.getNumber());
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
			final var scopedAccountStore = accountStoreFactory.newAccountStore(
					validator, dynamicProperties, ledgers.accounts());
			final var scopedTokenStore = tokenStoreFactory.newTokenStore(
					scopedAccountStore, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels(),
					NOOP_VIEWS_MANAGER, NOOP_TREASURY_ADDER, NOOP_TREASURY_REMOVER,
					sideEffects);
			final var mintLogic = mintLogicFactory.newLogic(validator, scopedTokenStore, scopedAccountStore);

			/* --- Execute the transaction and capture its results --- */
			final var creationTime = recordsHistorian.nextFollowingChildConsensusTime();
			mintLogic.mint(tokenId, newMeta.size(), 0, newMeta, creationTime);
			childRecord = creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects, EMPTY_MEMO);
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

	@SuppressWarnings("unused")
	protected Bytes computeAssociateToken(final Bytes input, final MessageFrame messageFrame) {
		final Bytes address = Address.wrap(input.slice(16, 20));
		final Bytes tokenAddress = Address.wrap(input.slice(48, 20));

		final var accountID = EntityIdUtils.accountParsedFromSolidityAddress(address.toArrayUnsafe());
		var account = accountStore.loadAccount(Id.fromGrpcAccount(accountID));
		final var tokenID = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArrayUnsafe());
		var token = tokenStore.loadToken(Id.fromGrpcToken(tokenID));
		tokenStore.commitTokenRelationships(List.of(token.newRelationshipWith(account, false)));

		try {
			account.associateWith(List.of(token), dynamicProperties.maxTokensPerAccount(), false);
			accountStore.commitAccount(account); // this is bad, no easy rollback
			return UInt256.valueOf(ResponseCodeEnum.SUCCESS_VALUE);
		} catch (InvalidTransactionException ite) {
			return UInt256.valueOf(ite.getResponseCode().getNumber());
		} catch (Exception e) {
			return UInt256.valueOf(ResponseCodeEnum.UNKNOWN_VALUE);
		}
	}

	@SuppressWarnings("unused")
	protected Bytes computeDissociateTokens(final Bytes input, final MessageFrame messageFrame) {
		return null;
	}

	@SuppressWarnings("unused")
	protected Bytes computeDissociateToken(final Bytes input, final MessageFrame messageFrame) {
		final Bytes address = Address.wrap(input.slice(16, 20));
		final Bytes tokenAddress = Address.wrap(input.slice(48, 20));
		final var accountID = EntityIdUtils.accountParsedFromSolidityAddress(address.toArrayUnsafe());
		var account = accountStore.loadAccount(Id.fromGrpcAccount(accountID));
		final var tokenID =
				Id.fromGrpcToken(EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArrayUnsafe()));
//		var token = tokenStore.loadToken(Id.fromGrpcToken(tokenID));


		final List<Dissociation> dissociations = List.of(Dissociation.loadFrom(tokenStore, account, tokenID));

		try {
			/* --- Do the business logic --- */
			account.dissociateUsing(dissociations, validator);

			/* --- Persist the updated models --- */
			accountStore.commitAccount(account);
			final List<TokenRelationship> allUpdatedRels = new ArrayList<>();
			for (var dissociation : dissociations) {
				dissociation.addUpdatedModelRelsTo(allUpdatedRels);
			}
			tokenStore.commitTokenRelationships(allUpdatedRels);
			return UInt256.valueOf(ResponseCodeEnum.SUCCESS_VALUE);
		} catch (InvalidTransactionException ite) {
			return UInt256.valueOf(ite.getResponseCode().getNumber());
		} catch (Exception e) {
			return UInt256.valueOf(ResponseCodeEnum.UNKNOWN_VALUE);
		}
	}

	/* --- Constructor functional interfaces for mocking --- */
	@FunctionalInterface
	interface MintLogicFactory {
		MintLogic newLogic(OptionValidator validator, TypedTokenStore tokenStore, AccountStore accountStore);
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

	void setTokenStoreFactory(final TokenStoreFactory tokenStoreFactory) {
		this.tokenStoreFactory = tokenStoreFactory;
	}

	void setAccountStoreFactory(final AccountStoreFactory accountStoreFactory) {
		this.accountStoreFactory = accountStoreFactory;
	}

	void setSideEffectsFactory(final Supplier<SideEffectsTracker> sideEffectsFactory) {
		this.sideEffectsFactory = sideEffectsFactory;
	}
}