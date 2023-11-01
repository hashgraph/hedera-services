package com.hedera.services.bdd.suites.hip796;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.Hip796Verbs;
import com.hedera.services.bdd.suites.HapiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;

public class PartitionsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(PartitionsSuite.class);
    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(

        );
    }

    /**
     * <b>Partitions-1:</b>
     * As a `partition-administrator`, I want to create new `partition-definition`s for my `token-definition`.
     */
    @HapiTest
    private HapiSpec createNewPartitionDefinitions() {
        return defaultHapiSpec("CreateNewPartitionDefinitions")
                .given(
                        fungibleTokenDefinition("TokenWithPartitions")
                                .administeredBy("token-administrator")
                                .partitionAdministeredBy("partition-administrator")
                )
                .when(
                        createPartitionDefinition("TokenWithPartitions", "NewPartition1")
                                .by("partition-administrator"),
                        createPartitionDefinition("TokenWithPartitions", "NewPartition2")
                                .by("partition-administrator")
                )
                .then(
                        queryPartitionDefinition("TokenWithPartitions", "NewPartition1").exists(),
                        queryPartitionDefinition("TokenWithPartitions", "NewPartition2").exists()
                );
    }

    @HapiTest
    private HapiSpec updatePartitionDefinitionsMemo() {
        return defaultHapiSpec("UpdatePartitionDefinitionsMemo")
                .given(
                        fungibleTokenDefinition("TokenWithPartitionsToUpdate")
                                .administeredBy("token-administrator")
                                .partitionAdministeredBy("partition-administrator"),
                        createPartitionDefinition("TokenWithPartitionsToUpdate", "PartitionToChange")
                                .withMemo("InitialMemo")
                                .by("partition-administrator")
                )
                .when(
                        updatePartitionDefinition("TokenWithPartitionsToUpdate", "PartitionToChange")
                                .withMemo("UpdatedMemo")
                                .by("partition-administrator")
                )
                .then(
                        queryPartitionDefinition("TokenWithPartitionsToUpdate", "PartitionToChange")
                                .hasMemo("UpdatedMemo")
                );
    }


    @HapiTest
    private HapiSpec transferBetweenPartitions() {
        return defaultHapiSpec("TransferBetweenPartitions")
                .given(
                        fungibleTokenDefinition("TokenWithMoveablePartitions")
                                .administeredBy("token-administrator")
                                .partitionMoveManagedBy("partition-move-manager"),
                        createPartitionDefinition("TokenWithMoveablePartitions", "PartitionA")
                                .by("partition-administrator"),
                        createPartitionDefinition("TokenWithMoveablePartitions", "PartitionB")
                                .by("partition-administrator"),
                        mintTokensToPartition("TokenWithMoveablePartitions", "PartitionA", "userAccount", 1000),
                        mintTokensToPartition("TokenWithMoveablePartitions", "PartitionB", "userAccount", 500)
                )
                .when(
                        transferBetweenPartitions("TokenWithMoveablePartitions", "userAccount")
                                .fromPartition("PartitionA").amount(200)
                                .toPartition("PartitionB")
                                .by("partition-move-manager")
                )
                .then(
                        queryPartitionBalance("TokenWithMoveablePartitions", "PartitionA", "userAccount")
                                .hasAmount(800),
                        queryPartitionBalance("TokenWithMoveablePartitions", "PartitionB", "userAccount")
                                .hasAmount(700)
                );
    }


    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
