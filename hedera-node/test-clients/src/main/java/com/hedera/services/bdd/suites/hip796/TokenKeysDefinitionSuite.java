package com.hedera.services.bdd.suites.hip796;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;

/**
 * A suite for user stories Keys-1 through Keys-4 from HIP-796.
 */
public class TokenKeysDefinitionSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TokenKeysDefinitionSuite.class);
    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(

        );
    }

    /**
     * <b>Keys-1</b>
     * <p>As a `token-administrator`, I want to administer (set, rotate/update, or remove)
     * a `lock-key` on the `token-definition`
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec manageLockKeyCapabilities() {
        return defaultHapiSpec("ManageLockKeyCapabilities")
                .given(
                        // Create two tokens "RotateToken" and "RemoveToken" with lock capability
                        Hip796Verbs.fungibleTokenWithFeatures("RotateToken")
                                .lockable()
                                .administeredBy("token-administrator"),

                        Hip796Verbs.fungibleTokenWithFeatures("RemoveToken")
                                .lockable()
                                .administeredBy("token-administrator")
                )
                .when(
                        // The 'token-administrator' updates (rotates) the implicit 'lock-key' for "RotateToken"
                        rotateLockKey("RotateToken")
                                .by("token-administrator"),

                        // The 'token-administrator' removes the implicit 'lock-key' for "RemoveToken"
                        removeLockKey("RemoveToken")
                                .by("token-administrator")
                )
                .then(
                        // Confirm the 'lock-key' for "RotateToken" has been updated
                        queryLockKey("RotateToken").isUpdated(),

                        // Confirm the 'lock-key' for "RemoveToken" has been removed
                        queryLockKey("RemoveToken").hasNoValue()
                );
    }

    /**
     * <b>Keys-2</b>
     * <p>As a `token-administrator`, I want to administer (set, rotate/update, or remove) a
     * `partition-key` on the `token-definition`
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec managePartitionKeyCapabilities() {
        return defaultHapiSpec("ManagePartitionKeyCapabilities")
                .given(
                        fungibleTokenDefinition("PartitionKeyToken")
                                .partitionable()
                                .administeredBy("token-administrator")
                )
                .when(
                        rotatePartitionKey("PartitionKeyToken")
                                .by("token-administrator"),

                        removePartitionKey("PartitionKeyToken")
                                .by("token-administrator")
                )
                .then(
                        queryPartitionKey("PartitionKeyToken").isUpdated(),

                        queryPartitionKey("PartitionKeyToken").hasNoValue()
                );
    }

    /**
     * <b>Keys-3</b>
     * <p>As a `token-administrator`, I want to administer (set, rotate/update, or remove) a
     * `partition-move-key` on the `token-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec managePartitionMoveKeyCapabilities() {
        return defaultHapiSpec("ManagePartitionMoveKeyCapabilities")
                .given(
                        fungibleTokenDefinition("PartitionMoveKeyToken")
                                .partitionable()
                                .administeredBy("token-administrator")
                )
                .when(
                        rotatePartitionMoveKey("PartitionMoveKeyToken")
                                .by("token-administrator"),

                        removePartitionMoveKey("PartitionMoveKeyToken")
                                .by("token-administrator")
                )
                .then(
                        queryPartitionMoveKey("PartitionMoveKeyToken").isUpdated(),

                        queryPartitionMoveKey("PartitionMoveKeyToken").hasNoValue()
                );
    }

    /**
     * <b>Keys-4</b>
     * <p>As a `token-administrator` smart contract, I want to administer each of the
     * above-mentioned keys.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    @HapiTest
    private HapiSpec manageKeysViaSmartContract() {
        return defaultHapiSpec("ManageKeysViaSmartContract")
                .given(
                        fungibleTokenDefinition("SmartContractToken")
                                .lockable()
                                .partitionable()
                                .administeredBy("smartContractAdministrator"),

                        smartContract("token-administrator")
                                .deployed()
                )
                .when(
                        smartContractCall("token-administrator", "rotateLockKey", "SmartContractToken"),

                        smartContractCall("token-administrator", "rotatePartitionKey", "SmartContractToken"),

                        smartContractCall("token-administrator", "removePartitionMoveKey", "SmartContractToken")
                )
                .then(
                        queryLockKey("SmartContractToken").isUpdated(),

                        queryPartitionKey("SmartContractToken").isUpdated(),

                        queryPartitionMoveKey("SmartContractToken").hasNoValue()
                );
    }


    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
