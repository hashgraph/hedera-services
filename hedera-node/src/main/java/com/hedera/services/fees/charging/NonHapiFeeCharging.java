package com.hedera.services.fees.charging;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.interceptors.AccountsCommitInterceptor;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;

import javax.inject.Inject;

import static com.hedera.services.exceptions.ValidationUtils.validateResourceLimit;
import static com.hedera.services.ledger.TransactionalLedger.activeLedgerWrapping;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_STORAGE_RENT;

/**
 * Responsible for charging the auto-renewal fee from the payer or auto-renew account if present on
 * contracts and accounts. Distributes the charged fee between 0.0.800, 0.0.801 and the funding
 * account
 */
public class NonHapiFeeCharging {
    // Used to distribute charged rent to collection accounts in correct percentages
    final FeeDistribution feeDistribution;

    @Inject
    public NonHapiFeeCharging(final FeeDistribution feeDistribution) {
        this.feeDistribution = feeDistribution;
    }

    public void chargeNonHapiFee(
            final AccountID payer,
            final long amount,
            TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts) {
        chargeFee(payer, amount, accounts);
    }

    private void chargeFee(
            final AccountID payer,
            final long fee,
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts) {
        var leftToPay = fee;
        final var autoRenewId = (EntityId) accounts.get(payer, AUTO_RENEW_ACCOUNT_ID);
        if (autoRenewId != null && !MISSING_ENTITY_ID.equals(autoRenewId)) {
            final var grpcId = autoRenewId.toGrpcAccountId();
            if (accounts.contains(grpcId) && !(boolean) accounts.get(grpcId, IS_DELETED)) {
                final var debited =
                        charge(autoRenewId.toGrpcAccountId(), leftToPay, false, accounts);
                leftToPay -= debited;
            }
        }
        if (leftToPay > 0) {
            charge(payer, leftToPay, true, accounts);
        }
    }

    private long charge(
            final AccountID payer,
            final long amount,
            final boolean isLastResort,
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts) {
        long paid;
        final var balance = (long) accounts.get(payer, BALANCE);
        if (amount > balance) {
            validateResourceLimit(!isLastResort, INSUFFICIENT_BALANCES_FOR_STORAGE_RENT);
            accounts.set(payer, BALANCE, 0L);
            paid = balance;
        } else {
            accounts.set(payer, BALANCE, balance - amount);
            paid = amount;
        }
        feeDistribution.distributeChargedFee(paid, accounts);
        return paid;
    }
}
