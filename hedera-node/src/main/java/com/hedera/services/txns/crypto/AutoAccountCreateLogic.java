package com.hedera.services.txns.crypto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.calculation.utils.PricedUsageCalculator;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AutoAccountsManager;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.NO_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Responsible for creating accounts from cryptoTransfer, when hbar is transferred from an account to an alias.
 */
public class AutoAccountCreateLogic {
	private static final Logger log = LogManager.getLogger(AutoAccountCreateLogic.class);

	private final AccountRecordsHistorian recordsHistorian;
	private final EntityIdSource ids;
	private final Supplier<SideEffectsTracker> sideEffectsFactory;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final EntityCreator creator;
	private final HbarCentExchange exchange;
	private final UsagePricesProvider usagePrices;
	private final PricedUsageCalculator pricedUsageCalculator;
	private final Map<ByteString, AccountID> tempCreations = new HashMap<>();
	private final AutoAccountsManager autoAccounts;

	public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
	public static final String AUTO_CREATED_ACCOUNT_MEMO = "auto-created account";

	@Inject
	public AutoAccountCreateLogic(
			final SyntheticTxnFactory syntheticTxnFactory,
			final EntityCreator creator,
			final EntityIdSource ids,
			final AccountRecordsHistorian recordsHistorian,
			final AutoAccountsManager autoAccounts,
			final HbarCentExchange exchange,
			final UsagePricesProvider usagePrices,
			final PricedUsageCalculator pricedUsageCalculator) {
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.creator = creator;
		this.ids = ids;
		this.recordsHistorian = recordsHistorian;
		this.exchange = exchange;
		this.usagePrices = usagePrices;
		this.pricedUsageCalculator = pricedUsageCalculator;
		sideEffectsFactory = SideEffectsTracker::new;
		this.autoAccounts = autoAccounts;
	}

	/**
	 * Create accounts corresponding to each alias given in the list of aliases from a cryptoTransfer transaction
	 *
	 * @param changeWithOnlyAlias
	 * 		BalanceChange from cryptoTransfer transaction which has only alias
	 * @param accountsLedger
	 * 		accounts ledger
	 * @return response code for the operation
	 */
	public Pair<ResponseCodeEnum, Long> createAutoAccount(BalanceChange changeWithOnlyAlias,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
		final var sideEffects = sideEffectsFactory.get();
		long feeForSyntheticCreateTxn;
		try {
			/* create a crypto create synthetic transaction */
			var alias = changeWithOnlyAlias.alias();
			Key key = Key.parseFrom(alias);
			var syntheticCreateTxn = syntheticTxnFactory.cryptoCreate(key, 0L);
			feeForSyntheticCreateTxn = feeForAutoAccountCreateTxn(syntheticCreateTxn);
			// adjust fee and return if insufficient balance.
			if (feeForSyntheticCreateTxn > changeWithOnlyAlias.units()) {
				return Pair.of(changeWithOnlyAlias.codeForInsufficientBalance(), 0L);
			} else {
				changeWithOnlyAlias.adjustUnits(-feeForSyntheticCreateTxn);
			}
			var newAccountId = ids.newAccountId(syntheticCreateTxn.getTransactionID().getAccountID());
			accountsLedger.create(newAccountId);
			final var customizer = new HederaAccountCustomizer()
					.key(JKey.mapKey(key))
					.memo(AUTO_CREATED_ACCOUNT_MEMO)
					.autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
					.isReceiverSigRequired(false)
					.isSmartContract(false)
					.alias(key.getEd25519().isEmpty() ? key.getECDSASecp256K1() : key.getEd25519());
			customizer.customize(newAccountId, accountsLedger);

			sideEffects.trackAutoCreatedAccount(newAccountId);

			/* create and track a synthetic record for crypto create synthetic transaction */
			var childRecord = creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects, AUTO_CREATED_ACCOUNT_MEMO);
			childRecord.setAlias(alias);

			var sourceId = recordsHistorian.nextChildRecordSourceId();
			recordsHistorian.trackPrecedingChildRecord(sourceId, syntheticCreateTxn, childRecord);

			/* add auto created accounts changes to the rebuilt data structure */
			autoAccounts.getAutoAccountsMap().put(alias, EntityNum.fromAccountId(newAccountId));

			tempCreations.put(alias, newAccountId);

		} catch (InvalidProtocolBufferException | DecoderException ex) {
			return Pair.of(INVALID_ALIAS, 0L);
		}
		return Pair.of(OK, feeForSyntheticCreateTxn);
	}

	/**
	 * Parses the {@code Key} from given alias  {@code ByteString}. If the Key is of type Ed25519 or ECDSA_SECP256K1
	 * keys, returns true. Returns false if any other Key type.
	 *
	 * @param alias
	 * 		given alias byte string
	 * @return response if it is primitive key
	 */
	public static boolean isPrimitiveKey(ByteString alias) {
		try {
			Key key = Key.parseFrom(alias);
			return !key.getECDSASecp256K1().isEmpty() || !key.getEd25519().isEmpty();
		} catch (InvalidProtocolBufferException ex) {
			return false;
		}
	}

	/**
	 * Clears the temporary creation map, that could be used for rollback
	 */
	public void clearTempCreations() {
		tempCreations.clear();
	}

	public Map<ByteString, AccountID> getTempCreations() {
		return tempCreations;
	}

	private long feeForAutoAccountCreateTxn(TransactionBody.Builder cryptoCreateTxn) {
		SignedTransaction signedTransaction = SignedTransaction.newBuilder()
				.setBodyBytes(cryptoCreateTxn.build().toByteString())
				.setSigMap(SignatureMap.getDefaultInstance())
				.build();

		Transaction txn = Transaction.newBuilder()
				.setSignedTransactionBytes(signedTransaction.toByteString())
				.build();

		try {
			SignedTxnAccessor accessor = new SignedTxnAccessor(txn);
			final var applicablePrices = usagePrices.activePrices(accessor).get(accessor.getSubType());
			final var rate = exchange.activeRate(recordsHistorian.getConsensusTimeFromTxnCtx());
			var feeData = pricedUsageCalculator.inHandleFees(accessor, applicablePrices, rate, recordsHistorian.getActivePayerKeyFromTxnCtx());
			return feeData.getServiceFee() + feeData.getNetworkFee() + feeData.getNodeFee();
		} catch (InvalidProtocolBufferException ex) {
			log.error("Error when parsing synthetic cryptoCreate transaction");
		}
		return 0;
	}
}
