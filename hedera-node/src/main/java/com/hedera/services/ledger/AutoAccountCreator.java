package com.hedera.services.ledger;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.AutoAccountCreationsManager;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class AutoAccountCreator {
	private final AccountRecordsHistorian recordsHistorian;
	private final EntityIdSource ids;
	private final Supplier<SideEffectsTracker> sideEffectsFactory;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final EntityCreator creator;
	private final HashMap<ByteString, AccountID> tempCreations = new HashMap<>();

	public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
	public static final String AUTO_CREATED_ACCOUNT_MEMO = "auto-created account";

	@Inject
	public AutoAccountCreator(
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

	public ResponseCodeEnum createAutoAccounts(final List<ByteString> accountsToBeCreated,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
		final var sideEffects = sideEffectsFactory.get();
		for (ByteString alias : accountsToBeCreated) {
			try {
				var syntheticCreateTxn = syntheticTxnFactory.cryptoCreate(alias, 0L);
				var newAccountId = ids.newAccountId(syntheticCreateTxn.getTransactionID().getAccountID());
				accountsLedger.create(newAccountId);
				sideEffects.trackAutoCreatedAccount(newAccountId);

				var childRecord = creator.createSuccessfulSyntheticRecord(null, sideEffects, AUTO_CREATED_ACCOUNT_MEMO);
				var sourceId = recordsHistorian.nextChildRecordSourceId();
				recordsHistorian.trackPrecedingChildRecord(sourceId, syntheticCreateTxn, childRecord);

				/* add auto created accounts changes to the rebuilt data structure */
				AutoAccountCreationsManager.getInstance()
						.getAutoAccountsMap()
						.put(alias, EntityNum.fromAccountId(newAccountId));

				tempCreations.put(alias, newAccountId);

			} catch (InvalidProtocolBufferException ex) {
				return BAD_ENCODING;
			}
		}
		return OK;
	}

	static boolean isPrimitiveKey(ByteString alias) {
		try {
			Key key = Key.parseFrom(alias);
			return !key.getECDSASecp256K1().isEmpty() || !key.getEd25519().isEmpty();
		} catch (InvalidProtocolBufferException ex) {
			return false;
		}
	}

	public void clearTempCreations() {
		tempCreations.clear();
	}

	public HashMap<ByteString, AccountID> getTempCreations() {
		return tempCreations;
	}
}
