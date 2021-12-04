package com.hedera.services.ledger;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.AutoAccountCreationsManager;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.txns.crypto.CreateLogic;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.Key;

import javax.inject.Inject;
import java.util.List;
import java.util.function.Supplier;

public class TransferCreationsFactory {
	private AccountRecordsHistorian recordsHistorian = null;
	private EntityIdSource ids;
	private Supplier<SideEffectsTracker> sideEffectsFactory = SideEffectsTracker::new;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final EntityCreator creator;

	public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
	public static final String AUTO_CREATED_ACCOUNT_MEMO = "auto-created account";

	@Inject
	public TransferCreationsFactory(
			SyntheticTxnFactory syntheticTxnFactory,
			EntityCreator creator,
			EntityIdSource ids) {
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.creator = creator;
		this.ids = ids;
	}

	public void createAutoAccounts(final List<Key> accountsToBeCreated) throws InvalidProtocolBufferException {
		final var sideEffects = sideEffectsFactory.get();
		for (Key alias : accountsToBeCreated) {
			// Fees to be added here
			var syntheticCreateTxn = syntheticTxnFactory.autoAccountCreate(alias, 0L);
			var childRecord = creator.createSuccessfulSyntheticRecord(null, sideEffects, AUTO_CREATED_ACCOUNT_MEMO);
			var sourceId = recordsHistorian.nextChildRecordSourceId();
            var newAccountId = ids.newAccountId(syntheticCreateTxn.getTransactionID().getAccountID());

			AutoAccountCreationsManager.getInstance()
					.getAutoAccountsMap()
					.put(alias.toByteString(), EntityNum.fromAccountId(newAccountId));
			sideEffects.trackAutoCreatedAccount(newAccountId);
			recordsHistorian.trackPrecedingChildRecord(sourceId, syntheticCreateTxn, childRecord);
		}
	}
}
