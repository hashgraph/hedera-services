package com.hedera.node.app.service.contract.impl.exec.systemcontracts.burn;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

public class BurnTranslator extends AbstractHtsCallTranslator {

    @Inject
    public BurnTranslator() {
        //@TODO to be implemented
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        //@TODO to be implemented
        return false;
    }

    @Override
    public HtsCall callFrom(@NonNull HtsCallAttempt attempt) {
        //@TODO to be implemented
        return null;
    }
}
