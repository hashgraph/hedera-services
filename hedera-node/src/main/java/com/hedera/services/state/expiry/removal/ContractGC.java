package com.hedera.services.state.expiry.removal;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractStorageListRemoval;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MapValueListUtils;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

import static com.hedera.services.state.virtual.VirtualBlobKey.Type.CONTRACT_BYTECODE;

@Singleton
public class ContractGC {
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> contracts;
	private final Supplier<VirtualMap<ContractKey, ContractValue>> storage;
	private final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode;

	private ContractGC.RemovalFacilitation removalFacilitation = MapValueListUtils::removeFromMapValueList;

	@Inject
	public ContractGC(
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> contracts,
			final Supplier<VirtualMap<ContractKey, ContractValue>> storage,
			final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode
	) {
		this.contracts = contracts;
		this.storage = storage;
		this.bytecode = bytecode;
		this.dynamicProperties = dynamicProperties;
	}

	public boolean expireBestEffort(final EntityNum expiredContractNum, final MerkleAccount contract) {
		removeBytecodeIfPresent(expiredContractNum);
		final var numKvPairs = contract.getNumContractKvPairs();
		if (numKvPairs > 0) {
			final var maxPairs = Math.min(numKvPairs, dynamicProperties.getMaxPurgedKvPairsPerTouch());
			final var removalMeta = removeKvPairs(
					maxPairs, expiredContractNum, contract.getFirstContractStorageKey(), storage.get());
			final var numRemoved = removalMeta.getKey();
			if (numRemoved < numKvPairs) {
				final var mutableContract = contracts.get().getForModify(expiredContractNum);
				mutableContract.setNumContractKvPairs(numKvPairs - numRemoved);
				mutableContract.setFirstUint256StorageKey(removalMeta.getValue().getKey());
				return false;
			}
		}
		return true;
	}

	private Pair<Integer, ContractKey> removeKvPairs(
			final int maxKvPairs,
			final EntityNum contractNum,
			final ContractKey rootKey,
			final VirtualMap<ContractKey, ContractValue> storage
	) {
		final var listRemoval = new ContractStorageListRemoval(contractNum.longValue(), storage);
		var i = maxKvPairs;
		var n = 0;
		var contractKey = rootKey;
		while (contractKey != null && i-- > 0) {
			// We are always removing the root, hence receiving the new root
			contractKey = removalFacilitation.removeNext(contractKey, contractKey, listRemoval);
			n++;
		}
		return Pair.of(n, contractKey);
	}

	private void removeBytecodeIfPresent(final EntityNum expiredContractNum) {
		final var bytecodeKey = new VirtualBlobKey(CONTRACT_BYTECODE, expiredContractNum.intValue());
		final var curBytecode = bytecode.get();
		if (curBytecode.containsKey(bytecodeKey)) {
			curBytecode.remove(bytecodeKey);
		}
	}

	@FunctionalInterface
	interface RemovalFacilitation {
		ContractKey removeNext(ContractKey key, ContractKey root, ContractStorageListRemoval listRemoval);
	}

	@VisibleForTesting
	void setRemovalFacilitation(final RemovalFacilitation removalFacilitation) {
		this.removalFacilitation = removalFacilitation;
	}
}
