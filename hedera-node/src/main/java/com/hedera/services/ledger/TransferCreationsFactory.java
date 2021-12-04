package com.hedera.services.ledger;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.txns.crypto.CreateLogic;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import java.util.List;
import java.util.function.Supplier;

public class TransferCreationsFactory {
	private final EntityIdSource seqNos;
	private final CreateLogic createLogic;
	private AccountRecordsHistorian recordsHistorian = null;
	private Supplier<SideEffectsTracker> sideEffectsFactory = SideEffectsTracker::new;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final EntityCreator creator;

	public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
	public static final String AUTO_CREATED_ACCOUNT_MEMO = "auto-created account";

	@Inject
	public TransferCreationsFactory(EntityIdSource seqNos,
			CreateLogic createLogic,
			SyntheticTxnFactory syntheticTxnFactory,
			EntityCreator creator) {
		this.seqNos = seqNos;
		this.createLogic = createLogic;
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.creator = creator;
	}

	public void createAutoAccounts(final List<Key> accountsToBeCreated) throws InvalidProtocolBufferException {
		final var sideEffects = sideEffectsFactory.get();
		for (Key alias : accountsToBeCreated) {
			// Fees to be added here
			var syntheticCreateTxn = syntheticTxnFactory.autoAccountCreate(alias, 0L);
			var childRecord = creator.createSuccessfulSyntheticRecord(null, sideEffects, AUTO_CREATED_ACCOUNT_MEMO);
			var sourceId = recordsHistorian.nextChildRecordSourceId();

			recordsHistorian.trackPrecedingChildRecord(sourceId, syntheticCreateTxn, childRecord);
		}
	}
}
