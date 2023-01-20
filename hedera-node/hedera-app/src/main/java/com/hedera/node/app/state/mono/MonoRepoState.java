package com.hedera.node.app.state.mono;

import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Map;

import static com.hedera.node.app.state.mono.MonoReadableStates.MerkleKind.*;

/**
 * This implementation of {@link HederaState} uses the mono-repo's {@link ServicesState} for the state
 * implementation, and attempts to do so in such a way that it reuses existing state structures (assuming
 * the services implementation code uses the same merkle-map / virtual-map as used by the mono-repo
 * service implementations).
 */
public class MonoRepoState implements HederaState {
    /*
     * TokenService:
     *      "Accounts" -> "accounts" merkle map (child 0)
     *      "Tokens" -> "tokens" merkle map (child 3)
     *      "TokenAssociations" -> "tokenAssociations" merkle map (child 4)
     *      "UniqueTokens" -> "uniqueTokens" merkle map (child 9)
     *      "StakingInfo" -> "stakingInfo" merkle map (child 12)
     *
     * ConsensusService:
     *      "Topics" -> "topics" merkle map (child 2)
     *
     * FileService:
     *      "Storage" -> Virtual Map (child 1)
     *      "SpecialFiles" -> "specialFiles" merkle node (child 7)
     *
     * ScheduleService:
     *      "MerkleScheduledTransactions" -> n-ary (child 5)
     *          "MerkleScheduledTransactionsState" -> merkle node (child 0)
     *          "ById" -> "byId" merkle map child 1
     *          "ByExpirationSecond" -> "byExpirationSecond" merkle map child 2
     *          "ByEquality" -> "byEquality" merkle map child 3
     *
     * ContractService:
     *      "ContractStorage" -> "contractStorage" virtual map (child 10)
     *
     * NetworkService:
     *      "NetworkContext" -> "networkCtx" merkle node (child 11)
     *      "AddressBook" -> "addressBook" merkle node (child 6)
     *      "RunningHashLeaf" -> "runningHashLeaf" merkle node (child 8)
     *
     */

    @NonNull
    @Override
    public ReadableStates createReadableStates(@NonNull String serviceName) {
        // This implementation has a hard-coded list of services names and which entities in the
        // tree (virtual map, merkle map, or raw node) are associated with that service and just
        // wraps them in a ReadableStates capable of creating the ReadableKVState for the data.
        return switch (serviceName) {
            case TokenService.NAME -> new MonoReadableStates(Map.of(
                    "Accounts", md(MERKLE_MAP, 0),
                    "Tokens", md(MERKLE_MAP, 3),
                    "TokenAssociations", md(MERKLE_MAP, 4),
                    "UniqueTokens", md(MERKLE_MAP, 9),
                    "StakingInfo", md(MERKLE_MAP, 12)));
            case ConsensusService.NAME -> new MonoReadableStates(Map.of(
                    "Topics", md(MERKLE_MAP, 2)));
            case FileService.NAME -> new MonoReadableStates(Map.of(
                    "Storage", md(VIRTUAL_MAP, 1),
                    "SpecialFiles", md(MERKLE_MAP, 7)));
            case ScheduleService.NAME -> new MonoReadableStates(Map.of(
                    "MerkleScheduledTransactions", new MonoReadableStates.Metadata(5, Map.of(
                            "MerkleScheduledTransactionsState", md(MERKLE_MAP, 0),
                            "ById", md(MERKLE_MAP, 1),
                            "ByExpirationSecond", md(MERKLE_MAP, 2),
                            "ByEquality", md(MERKLE_MAP, 3)))));
            case ContractService.NAME -> new MonoReadableStates(Map.of(
                    "ContractStorage", md(VIRTUAL_MAP, 10)));
            case NetworkService.NAME -> new MonoReadableStates(Map.of(
                    "NetworkContext", md(MERKLE_NODE, 11),
                    "AddressBook", md(MERKLE_NODE, 6),
                    "RunningHashLeaf", md(MERKLE_NODE, 8)));
            default -> throw new AssertionError("The service name " + serviceName + " is unknown");
        };
    }

    @NonNull
    @Override
    public WritableStates createWritableStates(@NonNull String serviceName) {
        // This implementation has a hard-coded list of services names and which entities in the
        // tree (virtual map, merkle map, or raw node) are associated with that service and just
        // wraps them in a WritableStates capable of creating the WritableKVState for the data.
        return switch (serviceName) {
            case TokenService.NAME -> new MonoWritableStates(Map.of(
                    "Accounts", md(MERKLE_MAP, 0),
                    "Tokens", md(MERKLE_MAP, 3),
                    "TokenAssociations", md(MERKLE_MAP, 4),
                    "UniqueTokens", md(MERKLE_MAP, 9),
                    "StakingInfo", md(MERKLE_MAP, 12)));
            case ConsensusService.NAME -> new MonoWritableStates(Map.of(
                    "Topics", md(MERKLE_MAP, 2)));
            case FileService.NAME -> new MonoWritableStates(Map.of(
                    "Storage", md(VIRTUAL_MAP, 1),
                    "SpecialFiles", md(MERKLE_MAP, 7)));
            case ScheduleService.NAME -> new MonoWritableStates(Map.of(
                    "MerkleScheduledTransactions", new MonoReadableStates.Metadata(5, Map.of(
                            "MerkleScheduledTransactionsState", md(MERKLE_MAP, 0),
                            "ById", md(MERKLE_MAP, 1),
                            "ByExpirationSecond", md(MERKLE_MAP, 2),
                            "ByEquality", md(MERKLE_MAP, 3)))));
            case ContractService.NAME -> new MonoWritableStates(Map.of(
                    "ContractStorage", md(VIRTUAL_MAP, 10)));
            case NetworkService.NAME -> new MonoWritableStates(Map.of(
                    "NetworkContext", md(MERKLE_NODE, 11),
                    "AddressBook", md(MERKLE_NODE, 6),
                    "RunningHashLeaf", md(MERKLE_NODE, 8)));
            default -> throw new AssertionError("The service name " + serviceName + " is unknown");
        };
    }

    private MonoReadableStates.Metadata md(MonoReadableStates.MerkleKind kind, int index) {
        return new MonoReadableStates.Metadata(kind, index);
    }
}
