package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantrevokekyc;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.*;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.hapi.node.transaction.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

/**
 * Translates grantKyc and revokeKyc calls to the HTS system contract. There are no special cases for
 * these calls, so the returned {@link HtsCall} is simply an instance of {@link DispatchForResponseCodeHtsCall}.
 */
@Singleton
public class GrantRevokeKycTranslator extends AbstractHtsCallTranslator {
    public static final Function GRANT_KYC= new Function("grantTokenKyc(address,address)", ReturnTypes.INT_64);
    public static final Function REVOKE_KYC= new Function("revokeTokenKyc(address,address)", ReturnTypes.INT_64);

    private final GrantRevokeKycDecoder decoder;

    @Inject
    public GrantRevokeKycTranslator(@NonNull GrantRevokeKycDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return matchesClassicSelector(attempt.selector());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HtsCall callFrom(@NonNull HtsCallAttempt attempt) {
        return new DispatchForResponseCodeHtsCall<>(
                attempt, bodyForClassic(attempt), SingleTransactionRecordBuilder.class);
    }

    private TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), GRANT_KYC.selector())) {
            return decoder.decodeGrantKyc(attempt);
        } else {
            return decoder.decodeRevokeKyc(attempt);
        }
    }

    private static boolean matchesClassicSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, GRANT_KYC.selector()) || Arrays.equals(selector, REVOKE_KYC.selector());
    }
}
