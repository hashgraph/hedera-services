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
import com.hedera.services.contracts.sources.TxnAwareSoliditySigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.tokens.views.UniqTokenViewsManager;
import com.hedera.services.txns.token.AssociateLogic;
import com.hedera.services.txns.token.DissociateLogic;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static java.util.Collections.singletonList;

@Singleton
public class HTSPrecompiledContract extends AbstractPrecompiledContract {
	private static final Logger LOG = LogManager.getLogger(HTSPrecompiledContract.class);
	private final OptionValidator validator;
	private final GlobalDynamicProperties dynamicProperties;
	private final UniqTokenViewsManager uniqTokenViewsManager;
	private final TypedTokenStore.LegacyTreasuryAdder addKnownTreasury;
	private final TypedTokenStore.LegacyTreasuryRemover delegate;
	private final DissociationFactory dissociationFactory;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final TxnAwareSoliditySigsVerifier sigsVerifier;
	private final ExpiringCreations creator;
	private final AccountRecordsHistorian recordsHistorian;

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
	public HTSPrecompiledContract(final GasCalculator gasCalculator,
								  final OptionValidator validator,
								  final GlobalDynamicProperties dynamicProperties,
								  final UniqTokenViewsManager uniqTokenViewsManager,
								  final TypedTokenStore.LegacyTreasuryAdder legacyStoreDelegate,
								  final TypedTokenStore.LegacyTreasuryRemover delegate,
								  final DissociationFactory dissociationFactory,
								  final SyntheticTxnFactory syntheticTxnFactory,
								  final TxnAwareSoliditySigsVerifier sigsVerifier,
								  final ExpiringCreations creator,
								  final AccountRecordsHistorian recordsHistorian) {
		super("HTS", gasCalculator);
		this.validator = validator;
		this.dynamicProperties = dynamicProperties;
		this.uniqTokenViewsManager = uniqTokenViewsManager;
		this.addKnownTreasury = legacyStoreDelegate;
		this.delegate = delegate;
		this.dissociationFactory = dissociationFactory;
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.sigsVerifier = sigsVerifier;
		this.creator = creator;
		this.recordsHistorian = recordsHistorian;
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
	protected Bytes computeMintToken(final Bytes input, final MessageFrame messageFrame) {
		return null;
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
		/* Get context from the Message frame */
		final var updater = (AbstractLedgerWorldUpdater) messageFrame.getWorldUpdater();
		final var ledgers = updater.wrappedTrackingLedgers();
		final var contract = messageFrame.getContractAddress();
		final var recipient = messageFrame.getRecipientAddress();

		/* Parse Bytes input as typed arguments */
		final var accountAddress = Address.wrap(input.slice(16, 20));
		final var tokenAddress = Address.wrap(input.slice(48, 20));

		/* Step 2 */
//		TODO: Initialize synthetic transaction
		ExpirableTxnRecord.Builder childRecord;
		Bytes result;

		try {
			/* Translate to gRPC types */
			final var accountID = EntityIdUtils.accountParsedFromSolidityAddress(accountAddress.toArrayUnsafe());
			final var tokenID = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArrayUnsafe());

			/* Perform validations */
			final var hasRequiredSigs = sigsVerifier.hasActiveKey(Id.fromGrpcAccount(accountID), recipient, contract);
			validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

			/* Initialize the stores */
			final var sideEffectsTracker = new SideEffectsTracker();
			final var accountStore = createAccountStore(ledgers);
			final var tokenStore = createTokenStore(ledgers, accountStore, sideEffectsTracker);

			/* Do the business logic */
			final var logic = new AssociateLogic(tokenStore, accountStore, dynamicProperties);
			logic.associate(Id.fromGrpcAccount(accountID), singletonList(tokenID));
			ledgers.commit();

			/* Summarize the happy results of the execution */
//			childRecord = creator.createSuccessfulSyntheticRecord(syntheticTxn, emptyList(), sideEffectsTracker);
			result = UInt256.valueOf(ResponseCodeEnum.SUCCESS_VALUE);
		} catch (InvalidTransactionException e) {
			/* Summarize the unhappy results of the execution */
//			childRecord = creator.createFailedSyntheticRecord(syntheticTxn, e.getResponseCode());
			result = UInt256.valueOf(e.getResponseCode().getNumber());
		}

		/* Track the child record and return */
//		updater.manageInProgressRecord(recordsHistorian, childRecord, syntheticTxn);
		return result;
	}

	@SuppressWarnings("unused")
	protected Bytes computeDissociateTokens(final Bytes input, final MessageFrame messageFrame) {
		return null;
	}

	@SuppressWarnings("unused")
	protected Bytes computeDissociateToken(final Bytes input, final MessageFrame messageFrame) {
		/* Get context from the Message frame */
		final var updater = (AbstractLedgerWorldUpdater) messageFrame.getWorldUpdater();
		final var ledgers = updater.wrappedTrackingLedgers();

		/* Parse Bytes input as typed arguments */
		final Bytes address = Address.wrap(input.slice(16, 20));
		final Bytes tokenAddress = Address.wrap(input.slice(48, 20));

		/* Step 2 */
//		TODO: Initialize synthetic transaction
		ExpirableTxnRecord.Builder childRecord;
		Bytes result;

		try {
			/* Translate to gRPC types */
			final var accountID = Id.fromGrpcAccount(EntityIdUtils.accountParsedFromSolidityAddress(address.toArrayUnsafe()));
			final var tokenID = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArrayUnsafe());

			/* Initialize the stores */
			final var sideEffects = new SideEffectsTracker();
			final var accountStore = createAccountStore(ledgers);
			final var tokenStore = createTokenStore(ledgers, accountStore, sideEffects);

			/* Do the business logic */
			DissociateLogic dissociateLogic = new DissociateLogic(validator, tokenStore, accountStore, dissociationFactory);
			dissociateLogic.dissociate(accountID, singletonList(tokenID));
			ledgers.commit();

			/* Summarize the happy results of the execution */
//			childRecord = creator.createSuccessfulSyntheticRecord(syntheticTxn, emptyList(), sideEffects);
			result = UInt256.valueOf(ResponseCodeEnum.SUCCESS_VALUE);
		} catch (InvalidTransactionException e) {
			/* Summarize the unhappy results of the execution */
//			childRecord = creator.createFailedSyntheticRecord(syntheticTxn, e.getResponseCode());
			result = UInt256.valueOf(e.getResponseCode().getNumber());
		}
		/* Track the child record and return */
//		updater.manageInProgressRecord(recordsHistorian, childRecord, syntheticTxn);

		return result;
	}

	/* Helpers */
	private AccountStore createAccountStore(final WorldLedgers ledgers) {
		return new AccountStore(validator, dynamicProperties, ledgers.accounts());
	}

	private TypedTokenStore createTokenStore(
			final WorldLedgers ledgers,
			final AccountStore accountStore,
			final SideEffectsTracker sideEffectsTracker) {
		return new TypedTokenStore(accountStore, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels(),
				uniqTokenViewsManager, addKnownTreasury, delegate, sideEffectsTracker);
	}
}