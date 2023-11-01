package com.hedera.services.bdd.suites.hip796;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.Hip796Verbs;
import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;

public class GeneralSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(GeneralSuite.class);
    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(

        );
    }


    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
