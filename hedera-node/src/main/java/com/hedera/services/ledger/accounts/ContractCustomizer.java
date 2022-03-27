package com.hedera.services.ledger.accounts;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;

import javax.annotation.Nullable;
import java.time.Instant;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static com.hedera.services.ledger.properties.AccountProperty.PROXY;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;

/**
 * Encapsulates a set of customizations to a smart contract. Primarily delegates to an {@link HederaAccountCustomizer},
 * but with a bit of extra logic to deal with {@link com.hedera.services.legacy.core.jproto.JContractIDKey} management.
 */
public class ContractCustomizer {
	// Null if the contract is immutable; then its key derives from its entity id
	private final JKey cryptoAdminKey;
	private final HederaAccountCustomizer accountCustomizer;

	public ContractCustomizer(final HederaAccountCustomizer accountCustomizer) {
		this(null, accountCustomizer);
	}

	public ContractCustomizer(final @Nullable JKey cryptoAdminKey, final HederaAccountCustomizer accountCustomizer) {
		this.cryptoAdminKey = cryptoAdminKey;
		this.accountCustomizer = accountCustomizer;
	}

	/**
	 * Given a {@link ContractCreateTransactionBody}, a decoded admin key, and the current consensus time,
	 * returns a customizer appropriate for the contract created from this HAPI operation.
	 *
	 * @param decodedKey
	 * 		the key implied by the HAPI operation
	 * @param consensusTime
	 * 		the consensus time of the ContractCreate
	 * @param op
	 * 		the details of the HAPI operation
	 * @return an appropriate top-level customizer
	 */
	public static ContractCustomizer fromHapiCreation(
			final JKey decodedKey,
			final Instant consensusTime,
			final ContractCreateTransactionBody op
	) {
		final var autoRenewPeriod = op.getAutoRenewPeriod().getSeconds();
		final var expiry = consensusTime.getEpochSecond() + autoRenewPeriod;
		final var proxyId = op.hasProxyAccountID()
				? EntityId.fromGrpcAccountId(op.getProxyAccountID())
				: MISSING_ENTITY_ID;
		final var key = (decodedKey instanceof JContractIDKey) ? null : decodedKey;
		final var customizer = new HederaAccountCustomizer()
				.memo(op.getMemo())
				.proxy(proxyId)
				.expiry(expiry)
				.autoRenewPeriod(op.getAutoRenewPeriod().getSeconds())
				.isSmartContract(true);
		System.out.println("Created a customizer from HAPI creation (key=" + decodedKey + ")");
		return new ContractCustomizer(key, customizer);
	}

	/**
	 * Given a {@link TransactionalLedger} containing the sponsor contract, returns a customizer appropriate
	 * to use for contracts created by the sponsor via internal {@code CONTRACT_CREATION} message calls.
	 *
	 * @param sponsor
	 * 		the sending contract
	 * @param ledger
	 * 		the containing ledger
	 * @return an appropriate child customizer
	 */
	public static ContractCustomizer fromSponsorContract(
			final AccountID sponsor,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger
	) {
		var key = (JKey) ledger.get(sponsor, KEY);
		if (key instanceof JContractIDKey) {
			key = null;
		}
		final var customizer = new HederaAccountCustomizer()
				.memo((String) ledger.get(sponsor, MEMO))
				.expiry((long) ledger.get(sponsor, EXPIRY))
				.proxy((EntityId) ledger.get(sponsor, PROXY))
				.autoRenewPeriod((long) ledger.get(sponsor, AUTO_RENEW_PERIOD))
				.isSmartContract(true);
		System.out.println("Created a customizer from parent contract (key=" + key + ")");
		return new ContractCustomizer(key, customizer);
	}

	/**
	 * Given a target contract account id and the containing ledger, makes various calls to
	 * {@link TransactionalLedger#set(Object, Enum, Object)} to initialize the contract's properties.
	 *
	 * @param id
	 * 		the id of the target contract
	 * @param ledger
	 * 		its containing ledger
	 */
	public void customize(
			final AccountID id,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger
	) {
		accountCustomizer.customize(id, ledger);
		final var newKey = (cryptoAdminKey == null)
				? new JContractIDKey(id.getShardNum(), id.getRealmNum(), id.getAccountNum())
				: cryptoAdminKey;
		ledger.set(id, KEY, newKey);
		System.out.println("Customized " + EntityIdUtils.readableId(id) + " with key " + newKey);
	}

	/**
	 * Updates a synthetic {@link ContractCreateTransactionBody} to represent the creation
	 * of a contract with this customizer's properties.
	 *
	 * @param op
	 * 		the synthetic creation to customize
	 */
	public void customizeSynthetic(final ContractCreateTransactionBody.Builder op) {
		if (cryptoAdminKey != null) {
			op.setAdminKey(asKeyUnchecked(cryptoAdminKey));
		}
		accountCustomizer.customizeSynthetic(op);
	}
}
