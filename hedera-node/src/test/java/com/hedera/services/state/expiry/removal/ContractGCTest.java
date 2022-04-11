package com.hedera.services.state.expiry.removal;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractStorageListRemoval;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.state.virtual.VirtualBlobKey.Type.CONTRACT_BYTECODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContractGCTest {
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> contracts;
	@Mock
	private VirtualMap<ContractKey, ContractValue> storage;
	@Mock
	private VirtualMap<VirtualBlobKey, VirtualBlobValue> bytecode;
	@Mock
	private ContractGC.RemovalFacilitation removalFacilitation;

	private ContractGC subject;

	@BeforeEach
	void setUp() {
		subject = new ContractGC(dynamicProperties, () -> contracts, () -> storage, () -> bytecode);
		subject.setRemovalFacilitation(removalFacilitation);
	}

	@Test
	void removesBytecodeIfPresent() {
		given(bytecode.containsKey(bytecodeKey)).willReturn(true);

		subject.expireBestEffort(contractNum, contractNoKvPairs);

		verify(bytecode).remove(bytecodeKey);
	}

	@Test
	void justRemovesAllKvPairsIfWithinMaxToPurge() {
		given(dynamicProperties.getMaxPurgedKvPairsPerTouch()).willReturn(10);
		given(removalFacilitation.removeNext(
				eq(rootKey),
				eq(rootKey),
				any(ContractStorageListRemoval.class))).willReturn(interKey);
		given(removalFacilitation.removeNext(
				eq(interKey),
				eq(interKey),
				any(ContractStorageListRemoval.class))).willReturn(tailKey);
		given(removalFacilitation.removeNext(
				eq(tailKey),
				eq(tailKey),
				any(ContractStorageListRemoval.class))).willReturn(null);

		final var done = subject.expireBestEffort(contractNum, contractSomeKvPairs);

		assertTrue(done);
	}

	@Test
	void removesNoMoreThanAllowedKvPairs() {
		given(dynamicProperties.getMaxPurgedKvPairsPerTouch()).willReturn(1);
		givenMutableContract(contractSomeKvPairs);
		given(removalFacilitation.removeNext(
				eq(rootKey),
				eq(rootKey),
				any(ContractStorageListRemoval.class))).willReturn(interKey);

		final var done = subject.expireBestEffort(contractNum, contractSomeKvPairs);

		assertFalse(done);
		assertEquals(2, contractSomeKvPairs.getNumContractKvPairs());
		assertEquals(interKey, contractSomeKvPairs.getFirstContractStorageKey());
	}

	private static ContractKey asKey(final int[] uint256Key) {
		return new ContractKey(BitPackUtils.numFromCode(contractNum.intValue()), uint256Key);
	}

	private void givenMutableContract(final MerkleAccount contract) {
		given(contracts.getForModify(contractNum)).willReturn(contract);
	}

	private static final EntityNum contractNum = EntityNum.fromLong(666);
	private static final VirtualBlobKey bytecodeKey = new VirtualBlobKey(CONTRACT_BYTECODE, contractNum.intValue());
	private static final int[] rootUint256Key = new int[] { 1, 2, 3, 4, 5, 6, 7, 8 };
	private static final int[] interUint256Key = new int[] { 2, 3, 4, 5, 6, 7, 8, 9 };
	private static final int[] tailUint256Key = new int[] { 3, 4, 5, 6, 7, 8, 9, 10 };
	private static final ContractKey rootKey = asKey(rootUint256Key);
	private static final ContractKey interKey = asKey(interUint256Key);
	private static final ContractKey tailKey = asKey(tailUint256Key);
	private final MerkleAccount contractSomeKvPairs = MerkleAccountFactory.newContract()
			.balance(0)
			.number(contractNum)
			.numKvPairs(3)
			.firstContractKey(rootUint256Key)
			.get();
	private final MerkleAccount contractNoKvPairs = MerkleAccountFactory.newContract()
			.number(contractNum)
			.balance(0)
			.get();
}