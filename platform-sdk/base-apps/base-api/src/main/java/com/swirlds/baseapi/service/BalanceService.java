package com.swirlds.baseapi.service;

import com.swirlds.baseapi.domain.Balance;
import com.swirlds.baseapi.persistence.BalanceDao;
import edu.umd.cs.findbugs.annotations.NonNull;

public class BalanceService extends Service<Balance> {
    private final BalanceDao dao;

    public BalanceService() {
        super(Balance.class);
        this.dao = BalanceDao.getInstance();
    }

    @NonNull
    @Override
    public Balance retrieve(@NonNull final String key) {
        return dao.findById(key);
    }


}
