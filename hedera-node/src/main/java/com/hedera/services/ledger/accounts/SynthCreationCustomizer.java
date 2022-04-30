package com.hedera.services.ledger.accounts;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SynthCreationCustomizer {
	private final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

	@Inject
	public SynthCreationCustomizer(final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
		this.accountsLedger = accountsLedger;
	}

	public TransactionBody customize(final TransactionBody synthCreate, final AccountID callerId) {
		final var customizer = ContractCustomizer.fromSponsorContract(callerId, accountsLedger);
		final var customBuilder = synthCreate.getContractCreateInstance().toBuilder();
		customizer.customizeSynthetic(customBuilder);
		return synthCreate.toBuilder().setContractCreateInstance(customBuilder).build();
	}
}
