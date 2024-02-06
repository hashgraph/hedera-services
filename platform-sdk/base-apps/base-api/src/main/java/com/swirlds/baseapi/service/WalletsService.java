package com.swirlds.baseapi.service;

import com.swirlds.baseapi.domain.Balance;
import com.swirlds.baseapi.domain.Wallet;
import com.swirlds.baseapi.metrics.ApplicationMetrics;
import com.swirlds.baseapi.persistence.BalanceDao;
import com.swirlds.baseapi.persistence.Version;
import com.swirlds.baseapi.persistence.WalletDao;
import com.swirlds.common.context.PlatformContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class WalletsService extends Service<Wallet> {

    private final @NonNull WalletDao dao;
    private final @NonNull BalanceDao balanceDao;
    private final @NonNull PlatformContext context;

    public WalletsService(final @NonNull PlatformContext context) {
        super(Wallet.class);
        this.context =
                Objects.requireNonNull(context, "transaction cannot be null");
        this.dao = WalletDao.getInstance();
        this.balanceDao = BalanceDao.getInstance();
    }

    @Override
    public void delete(@NonNull final String key) {
        dao.deleteById(key);
    }

    @NonNull
    @Override
    public Wallet create(@NonNull final Wallet body) {
        final Wallet save = dao.save(new Wallet(UUID.randomUUID().toString()));
        balanceDao.save(new Balance(save, BigDecimal.ZERO, new Version(0)));
        context.getMetrics().getOrCreate(ApplicationMetrics.WALLETS_COUNT).increment();
        return save;
    }

    @NonNull
    @Override
    public Wallet retrieve(@NonNull final String key) {
        return dao.findById(key);
    }

    @NonNull
    @Override
    public List<Wallet> retrieveAll(@NonNull final Map<String, String> params) {
        return dao.findAll();
    }
}
