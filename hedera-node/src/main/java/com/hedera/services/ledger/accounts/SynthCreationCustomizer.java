package com.hedera.services.ledger.accounts;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Customizes a HAPI contract create transaction with the "inheritable" properties of a
 * parent account; exists to simplify re-use of {@link ContractCustomizer}.
 */
@Singleton
public class SynthCreationCustomizer {
	private final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

	@Inject
	public SynthCreationCustomizer(final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
		this.accountsLedger = accountsLedger;
	}

	/**
	 * Given a synthetic HAPI contract create transaction, updates it to reflect the inheritable
	 * properties of the given caller account.
	 *
	 * @param synthCreate a HAPI contract creation
	 * @param callerId a known caller account
	 * @return the HAPI transaction customized with the caller's inheritable properties
	 */
	public TransactionBody customize(final TransactionBody synthCreate, final AccountID callerId) {
		final var customizer = ContractCustomizer.fromSponsorContract(callerId, accountsLedger);
		final var customBuilder = synthCreate.getContractCreateInstance().toBuilder();
		customizer.customizeSynthetic(customBuilder);
		return synthCreate.toBuilder().setContractCreateInstance(customBuilder).build();
	}
}
