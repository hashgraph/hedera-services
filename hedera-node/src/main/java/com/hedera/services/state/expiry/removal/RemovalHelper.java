package com.hedera.services.state.expiry.removal;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.expiry.EntityProcessResult;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.renewal.RenewalRecordsHelper;
import com.hedera.services.utils.EntityNum;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.services.state.expiry.EntityProcessResult.DONE;
import static com.hedera.services.state.expiry.EntityProcessResult.NOTHING_TO_DO;
import static com.hedera.services.state.expiry.EntityProcessResult.STILL_MORE_TO_DO;

@Singleton
public class RemovalHelper implements RemovalWork{
	private final ClassificationWork classifier;
	private final GlobalDynamicProperties properties;
	private final ContractGC contractGC;
	private final AccountGC accountGC;
	private final RenewalRecordsHelper recordsHelper;

	@Inject
	public RemovalHelper(final ClassificationWork classifier,
			final GlobalDynamicProperties properties,
			final ContractGC contractGC,
			final AccountGC accountGC,
			final RenewalRecordsHelper recordsHelper) {
		this.classifier = classifier;
		this.properties = properties;
		this.contractGC = contractGC;
		this.accountGC = accountGC;
		this.recordsHelper = recordsHelper;
	}

	@Override
	public EntityProcessResult tryToRemoveAccount(final EntityNum account) {
		if ( !properties.shouldAutoRenewAccounts()) {
			return NOTHING_TO_DO;
		}
		return remove(account, false);
	}

	@Override
	public EntityProcessResult tryToRemoveContract(final EntityNum contract) {
		if ( !properties.shouldAutoRenewContracts()) {
			return NOTHING_TO_DO;
		}
		return remove(contract, true);
	}

	private EntityProcessResult remove(final EntityNum contractNum, final boolean isContract) {
		final var lastClassified = classifier.getLastClassified();
		if (isContract && !contractGC.expireBestEffort(contractNum, lastClassified)) {
			return STILL_MORE_TO_DO;
		}
		final var treasuryReturns = accountGC.expireBestEffort(contractNum, lastClassified);
		recordsHelper.streamCryptoRemoval(
				contractNum, treasuryReturns.tokenTypes(), treasuryReturns.transfers());
		return treasuryReturns.finished() ? DONE : STILL_MORE_TO_DO;
	}
}
