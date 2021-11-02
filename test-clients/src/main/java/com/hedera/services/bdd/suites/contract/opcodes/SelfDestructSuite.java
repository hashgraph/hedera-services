package com.hedera.services.bdd.suites.contract.opcodes;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.FACTORY_SELF_DESTRUCT_CONSTRUCTOR_CONTRACT;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class SelfDestructSuite extends HapiApiSuite {
    private final Logger LOGGER = LogManager.getLogger(SelfDestructSuite.class);

    public static void main(String... args) {
        new SelfDestructSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return LOGGER;
    }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
            HSCS_EVM_008_SelfDesctructUpdatesHederaAccount()
        );
    }
    private HapiApiSpec HSCS_EVM_008_SelfDesctructUpdatesHederaAccount() {
        return defaultHapiSpec("HSCS_EVM_008_SelfDestructUpdatesHederaAccount")
                .given(
                        fileCreate("bytecode")
                                .path(FACTORY_SELF_DESTRUCT_CONSTRUCTOR_CONTRACT)
                )
                .when(
                        contractCreate("selfDestroying")
                                .bytecode("bytecode")
                                .via("contractCreate")
                                .hasKnownStatus(SUCCESS)
                )
                .then(
                        getAccountInfo("selfDestroying")
                                .hasCostAnswerPrecheck(ACCOUNT_DELETED),
                        getContractInfo("selfDestroying")
                                .hasCostAnswerPrecheck(CONTRACT_DELETED)
                );
    }
}
