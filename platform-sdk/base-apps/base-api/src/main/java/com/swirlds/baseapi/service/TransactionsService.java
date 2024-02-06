package com.swirlds.baseapi.service;

import com.google.common.base.Preconditions;
import com.swirlds.baseapi.domain.Balance;
import com.swirlds.baseapi.domain.Transaction;
import com.swirlds.baseapi.metrics.ApplicationMetrics;
import com.swirlds.baseapi.persistence.BalanceDao;
import com.swirlds.baseapi.persistence.TransactionDao;
import com.swirlds.baseapi.persistence.Version.VersionMismatchException;
import com.swirlds.common.context.PlatformContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class TransactionsService extends Service<Transaction> {
    private final @NonNull TransactionDao transactionDao;
    private final @NonNull BalanceDao balanceDao;
    private final @NonNull PlatformContext context;

    public TransactionsService(@NonNull final PlatformContext context) {
        super(Transaction.class);
        this.context =
                Objects.requireNonNull(context, "transaction cannot be null");
        this.transactionDao = TransactionDao.getInstance();
        this.balanceDao = BalanceDao.getInstance();
    }

    @NonNull
    @Override
    public Transaction create(@NonNull final Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction cannot be null");
        Preconditions.checkArgument(transaction.from() != null, "transaction#from cannot be null");
        Preconditions.checkArgument(transaction.to() != null, "transaction#to cannot be null");
        Preconditions.checkArgument(transaction.amount() != null, "transaction#amount cannot be null");
        Preconditions.checkArgument(balanceDao.findById(transaction.from()) != null,
                "origin wallet not found");
        Preconditions.checkArgument(balanceDao.findById(transaction.to()) != null,
                "destiny wallet not found");

        boolean done = false;
        while (!done) {
            try {
                final Balance fromBalance = balanceDao.findById(transaction.from());
                Preconditions.checkArgument(fromBalance.amount().compareTo(transaction.amount()) >= 0,
                        "Not enough balance in originating account");
                final Balance afterFromBalance = new Balance(fromBalance.wallet(),
                        fromBalance.amount().subtract(transaction.amount()), fromBalance.version());
                balanceDao.save(afterFromBalance);
                done = true;
            } catch (VersionMismatchException e) {
                //retry
            }
        }
        done = false;
        while (!done) {
            try {
                final Balance toBalance = balanceDao.findById(transaction.to());
                Preconditions.checkArgument(toBalance != null, "destiny wallet not found");

                final Balance afterToBalance = new Balance(toBalance.wallet(),
                        toBalance.amount().add(transaction.amount()), toBalance.version());
                balanceDao.save(afterToBalance);
                done = true;
            } catch (VersionMismatchException e) {
                //retry
            }
        }

        final Transaction save = transactionDao.save(
                new Transaction(UUID.randomUUID().toString(), transaction.from(), transaction.to(),
                        transaction.amount()));
        context.getMetrics().getOrCreate(ApplicationMetrics.TRANSACTION_COUNT).increment();
        return save;
    }

    @NonNull
    @Override
    public Transaction retrieve(@NonNull final String key) {
        return super.retrieve(key);
    }

    @NonNull
    @Override
    public List<Transaction> retrieveAll(@NonNull final Map<String, String> params) {
        Objects.requireNonNull(params, "params must not be null");
        final String walletId = params.get("walletId");
        Preconditions.checkArgument(walletId != null, "walletId must not be null");
        return transactionDao.findByWalletId(walletId);
    }
}
