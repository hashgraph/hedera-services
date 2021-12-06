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
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AutoAccountsManager;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Responsible for creating accounts from cryptoTransfer, when hbar is transferred from an account to an alias.
 */
public class AutoAccountCreateLogic {
	private final AccountRecordsHistorian recordsHistorian;
	private final EntityIdSource ids;
	private final Supplier<SideEffectsTracker> sideEffectsFactory;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final EntityCreator creator;
	private final Map<ByteString, AccountID> tempCreations = new HashMap<>();

	public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
	public static final String AUTO_CREATED_ACCOUNT_MEMO = "auto-created account";

	@Inject
	public AutoAccountCreateLogic(
			SyntheticTxnFactory syntheticTxnFactory,
			EntityCreator creator,
			EntityIdSource ids,
			AccountRecordsHistorian recordsHistorian) {
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.creator = creator;
		this.ids = ids;
		this.recordsHistorian = recordsHistorian;
		sideEffectsFactory = SideEffectsTracker::new;
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
	public ResponseCodeEnum createAutoAccounts(BalanceChange changeWithOnlyAlias,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
		final var sideEffects = sideEffectsFactory.get();
		try {
			/* create a crypto create synthetic transaction */
			var alias = changeWithOnlyAlias.alias();
			var syntheticCreateTxn = syntheticTxnFactory.cryptoCreate(alias, 0L);
			/* TODO calculate the cryptoCreate Fee and update the amount in the balanceChange accordingly and set the validity */
			var feeForSyntheticCreateTxn = 0;
			// adjust fee and return if insufficient balance.
			if (feeForSyntheticCreateTxn < changeWithOnlyAlias.units()) {
				return changeWithOnlyAlias.codeForInsufficientBalance();
			} else {
				changeWithOnlyAlias.adjustUnits(-feeForSyntheticCreateTxn);
			}
			var newAccountId = ids.newAccountId(syntheticCreateTxn.getTransactionID().getAccountID());
			accountsLedger.create(newAccountId);
			sideEffects.trackAutoCreatedAccount(newAccountId);

			/* create and track a synthetic record for crypto create synthetic transaction */
			var childRecord = creator.createSuccessfulSyntheticRecord(null, sideEffects, AUTO_CREATED_ACCOUNT_MEMO);
			var sourceId = recordsHistorian.nextChildRecordSourceId();
			recordsHistorian.trackPrecedingChildRecord(sourceId, syntheticCreateTxn, childRecord);

			/* add auto created accounts changes to the rebuilt data structure */
			AutoAccountsManager.getInstance()
					.getAutoAccountsMap()
					.put(alias, EntityNum.fromAccountId(newAccountId));

			tempCreations.put(alias, newAccountId);

		} catch (InvalidProtocolBufferException ex) {
			return BAD_ENCODING;
		}
		return OK;
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
}
