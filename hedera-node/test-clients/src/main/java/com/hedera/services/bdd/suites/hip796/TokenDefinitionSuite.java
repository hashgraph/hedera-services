package com.hedera.services.bdd.suites.hip796;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.Hip796Verbs;
import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;

public class TokenDefinitionSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TokenDefinitionSuite.class);
    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(

        );
    }

    @HapiTest
    private HapiSpec manageLockKeyCapabilities() {
        return defaultHapiSpec("ManageLockKeyCapabilities")
                .given(
                        // Create two tokens "RotateToken" and "RemoveToken" with lock capability
                        Hip796Verbs.fungibleTokenDefinition("RotateToken")
                                .lockable()
                                .administeredBy("token-administrator"),

                        Hip796Verbs.fungibleTokenDefinition("RemoveToken")
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
