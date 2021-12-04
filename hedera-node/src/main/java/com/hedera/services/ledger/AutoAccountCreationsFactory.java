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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KEY_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class AutoAccountCreationsFactory {
	private AccountRecordsHistorian recordsHistorian = null;
	private EntityIdSource ids;
	private Supplier<SideEffectsTracker> sideEffectsFactory = SideEffectsTracker::new;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final EntityCreator creator;
	private final HashMap<ByteString, AccountID> tempCreations = new HashMap<>();

	public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
	public static final String AUTO_CREATED_ACCOUNT_MEMO = "auto-created account";

	@Inject
	public AutoAccountCreationsFactory(
			SyntheticTxnFactory syntheticTxnFactory,
			EntityCreator creator,
			EntityIdSource ids) {
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.creator = creator;
		this.ids = ids;
	}

	private void createAutoAccounts(final List<Key> accountsToBeCreated,
			TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) throws InvalidProtocolBufferException {
		final var sideEffects = sideEffectsFactory.get();
		for (Key alias : accountsToBeCreated) {
			// Fees to be added here
			var syntheticCreateTxn = syntheticTxnFactory.autoAccountCreate(alias, 0L);
			var newAccountId = ids.newAccountId(syntheticCreateTxn.getTransactionID().getAccountID());
			accountsLedger.create(newAccountId);
			sideEffects.trackAutoCreatedAccount(newAccountId);

			var childRecord = creator.createSuccessfulSyntheticRecord(null, sideEffects, AUTO_CREATED_ACCOUNT_MEMO);
			var sourceId = recordsHistorian.nextChildRecordSourceId();
			recordsHistorian.trackPrecedingChildRecord(sourceId, syntheticCreateTxn, childRecord);

			AutoAccountCreationsManager.getInstance()
					.getAutoAccountsMap()
					.put(alias.toByteString(), EntityNum.fromAccountId(newAccountId));

			tempCreations.put(alias.toByteString(), newAccountId);
		}
	}


	ResponseCodeEnum autoCreateForAliasTransfers(final List<ByteString> autoCreateAliases,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
		try {
			List<Key> aliasKeys = new ArrayList<>();
			for (ByteString alias : autoCreateAliases) {
				final var key = Key.parseFrom(alias);
				aliasKeys.add(key);
				//Need to check if it is primitive key
			}
			createAutoAccounts(aliasKeys, accountsLedger);
		} catch (InvalidProtocolBufferException ex) {
			return INVALID_KEY_ENCODING;
		}
		return OK;
	}


	public void clearTempCreations() {
		tempCreations.clear();
	}
}
