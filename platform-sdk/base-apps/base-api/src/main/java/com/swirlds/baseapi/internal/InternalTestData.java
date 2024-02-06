package com.swirlds.baseapi.internal;

import com.swirlds.baseapi.domain.Balance;
import com.swirlds.baseapi.domain.Wallet;
import com.swirlds.baseapi.persistence.BalanceDao;
import com.swirlds.baseapi.persistence.Version;
import com.swirlds.baseapi.persistence.WalletDao;
import java.math.BigDecimal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InternalTestData {

    private static final Logger log = LogManager.getLogger(InternalTestData.class);

    public static void create() {
        WalletDao.getInstance().save(new Wallet("0"));
        BalanceDao.getInstance().save(new Balance(new Wallet("0"), BigDecimal.valueOf(Long.MAX_VALUE), new Version(0)));
        log.debug("Created internal test data");
    }
}
