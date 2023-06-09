package com.hedera.services.bdd.suites.crypto;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.misc.PerpetualTransfers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static java.util.concurrent.TimeUnit.SECONDS;

public class CryptoCreateManySuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CryptoCreateManySuite.class);

    private AtomicLong duration = new AtomicLong(30);
    private AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
    private AtomicInteger maxOpsPerSec = new AtomicInteger(1000);

    public static void main(String... args) {
        new CryptoCreateManySuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(canCreateManyAccounts());
    }

    private HapiSpec canCreateManyAccounts() {
        return HapiSpec.defaultHapiSpec("CanCreateManyAccounts")
                .given()
                .when()
                .then(runWithProvider(creationFactory())
                        .lasting(duration::get, unit::get)
                        .maxOpsPerSec(maxOpsPerSec::get));
    }

    private Function<HapiSpec, OpProvider> creationFactory() {
        final var num = new AtomicInteger(0);
        final var random = new SplittableRandom(123456789);
        return spec -> new OpProvider() {
            @Override
            public List<HapiSpecOperation> suggestedInitializers() {
                return List.of();
            }

            @Override
            public Optional<HapiSpecOperation> get() {
                var op = cryptoCreate("account" + num.getAndIncrement())
                        .balance(random.nextLong(1234))
                        .entityMemo(TxnUtils.randomUppercase(8))
                        .payingWith(GENESIS)
                        .noLogging()
                        .deferStatusResolution();

                return Optional.of(op);
            }
        };
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
