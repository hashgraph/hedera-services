package com.hedera.services.bdd.suites.lambda;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

import java.util.stream.Stream;

import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.lambda.LambdaInstaller.lambdaBytecode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;

@Tag(TOKEN)
public class HipExamplesTest {
    @HapiTest
    final Stream<DynamicTest> canUpdateExpiryOnlyOpWithoutAdminKey(
            @NonFungibleToken(numPreMints = 1) SpecNonFungibleToken cleverCoin) {
        return hapiTest(
                cryptoCreate("sphinx")
                        .maxAutomaticTokenAssociations(1)
                        .installing(lambdaBytecode("OneTimeCodeTransferAllowance").atIndex(123)),
                cryptoTransfer(movingUnique(cleverCoin.name(), 1L)
                        .between(cleverCoin.treasury().name(), "sphinx"))
        );
    }
}
