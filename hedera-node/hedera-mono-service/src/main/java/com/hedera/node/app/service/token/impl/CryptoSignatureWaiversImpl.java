package com.hedera.node.app.service.token.impl;

import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.NotImplementedException;

/**
 * An implementation of signature waivers needed for transactions in {@link CryptoService}.
 * NOTE: FUTURE - These will be implemented in the coming PRs.
 */
public class CryptoSignatureWaiversImpl implements CryptoSignatureWaivers {
    private HederaAccountNumbers accountNumbers;

    public CryptoSignatureWaiversImpl(final HederaAccountNumbers accountNumbers){
        this.accountNumbers = accountNumbers;
    }

    @Override
    public boolean isTargetAccountSignatureWaived(TransactionBody cryptoUpdateTxn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public boolean isNewKeySignatureWaived(TransactionBody cryptoUpdateTxn, AccountID payer) {
        throw new NotImplementedException();
    }
}
