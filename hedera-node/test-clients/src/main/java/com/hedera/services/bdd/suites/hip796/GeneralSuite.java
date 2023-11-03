package com.hedera.services.bdd.suites.hip796;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
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

    /**
     * <b>General-1</b>
     * <p>As a `token-issuer`, I want to create a fungible token definition with locking and/or partitioning
     * capabilities.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canCreateTokenWithLockingAndPartitioning() {
        final String tokenIssuer = "TokenIssuer";
        final String theToken = "TheToken";

        return defaultHapiSpec("CanCreateTokenWithLockingAndPartitioning")
                .given(
                        cryptoCreate(tokenIssuer).balance(10_000_000_000L)
                ).when(
                        tokenCreate(theToken)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(1_000_000)
                                .decimals(2)
                                .withPartitioning()  // Assuming we have a DSL command to enable partitioning
                                .withLocking()       // Assuming we have a DSL command to enable locking feature
                                .treasury(tokenIssuer)
                                .signedBy(tokenIssuer)
                                .via("tokenCreateTxn")
                ).then(
                        validateToken(theToken)
                                .hasLockingEnabled()       // Assuming we have a DSL method to check if locking is enabled
                                .hasPartitioningEnabled(), // Assuming we have a DSL method to check if partitioning is enabled
                        assertStatus(SUCCESS, "tokenCreateTxn")
                );
    }

    /**
     * <b>General-2</b>
     * <p>As a `token-issuer`, I want to create a non-fungible token definition with locking and/or partitioning
     * capabilities.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    public HapiSpec canCreateNFTWithLockingAndPartitioning() {
        final String tokenIssuer = "TokenIssuer";
        final String nftToken = "NFTToken";

        return defaultHapiSpec("CanCreateNFTWithLockingAndPartitioning")
                .given(
                        cryptoCreate(tokenIssuer).balance(10_000_000_000L)
                ).when(
                        tokenCreate(nftToken)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0) // NFTs have initial supply set to zero
                                .withPartitioning()  // Assuming we have a DSL command to enable partitioning
                                .withLocking()       // Assuming we have a DSL command to enable locking feature
                                .treasury(tokenIssuer)
                                .signedBy(tokenIssuer)
                                .via("tokenCreateTxn")
                ).then(
                        validateToken(nftToken)
                                .hasLockingEnabled()       // Assuming we have a DSL method to check if locking is enabled
                                .hasPartitioningEnabled(), // Assuming we have a DSL method to check if partitioning is enabled
                        assertStatus(SUCCESS, "tokenCreateTxn")
                );
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
