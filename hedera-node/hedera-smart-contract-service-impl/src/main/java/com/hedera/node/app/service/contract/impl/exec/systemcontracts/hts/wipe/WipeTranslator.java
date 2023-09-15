package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class WipeTranslator extends AbstractHtsCallTranslator {

    public static final Function WIPE_FUNGIBLE_V1 = new Function("wipeTokenAccount(address,address,uint32)",
            ReturnTypes.INT);
    public static final Function WIPE_FUNGIBLE_V2 = new Function("wipeTokenAccount(address,address,int64)",
            ReturnTypes.INT);
    public static final Function WIPE_NFT = new Function("wipeTokenAccountNFT(address,address,int64[])",
            ReturnTypes.INT);

    private final WipeDecoder decoder;

    @Inject
    public WipeTranslator(@NonNull final WipeDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return selectorMatches(attempt, WIPE_FUNGIBLE_V1)
                || selectorMatches(attempt, WIPE_FUNGIBLE_V2)
                || selectorMatches(attempt, WIPE_NFT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HtsCall callFrom(@NonNull final HtsCallAttempt attempt) {
        return new DispatchForResponseCodeHtsCall<>(
                attempt, bodyForClassic(attempt), SingleTransactionRecordBuilder.class);
    }

    private TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        if (selectorMatches(attempt, WIPE_FUNGIBLE_V1)) {
            return decoder.decodeWipeFungibleV1(attempt);
        } else if (selectorMatches(attempt, WIPE_FUNGIBLE_V2)) {
            return decoder.decodeWipeFungibleV2(attempt);
        } else {
            return decoder.decodeWipeNonFungible(attempt);
        }
    }

    private boolean selectorMatches(final HtsCallAttempt attempt, final Function function) {
        return Arrays.equals(attempt.selector(), function.selector());
    }
}
