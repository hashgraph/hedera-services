package com.hedera.services.store.contracts;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class MutableEntityAccessTest {
	@Mock
	private HederaLedger ledger;
	@Mock
	private Supplier<VirtualMap<ContractKey, ContractValue>> supplierContractStorage;
	@Mock
	private Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> supplierBytecode;
	@Mock
	private VirtualMap<ContractKey, ContractValue> contractStorage;
	@Mock
	private VirtualMap<VirtualBlobKey, VirtualBlobValue> bytecodeStorage;

	private MutableEntityAccess subject;

	private final AccountID id = IdUtils.asAccount("0.0.1234");
	private final long balance = 1234L;
	private final MerkleAccount merkleAccount = new MerkleAccount();

	private final UInt256 contractStorageKey = UInt256.ONE;
	private final ContractKey expectedContractKey = new ContractKey(id.getAccountNum(), contractStorageKey.toArray());
	private final UInt256 contractStorageValue = UInt256.MAX_VALUE;
	private final ContractValue expectedContractValue = new ContractValue(contractStorageValue.toArray());

	private final Bytes bytecode = Bytes.of("contract-code".getBytes());
	private final VirtualBlobKey expectedBytecodeKey = new VirtualBlobKey(VirtualBlobKey.Type.CONTRACT_BYTECODE, (int) id.getAccountNum());
	private final VirtualBlobValue expectedBytecodeValue = new VirtualBlobValue(bytecode.toArray());


	@BeforeEach
	void setUp() {
		subject = new MutableEntityAccess(ledger, supplierContractStorage, supplierBytecode);
	}

	@Test
	void spawnsAccount() {
		// when:
		subject.spawn(id, balance, new HederaAccountCustomizer());

		// then:
		verify(ledger).spawn(eq(id), eq(balance), any());
	}

	@Test
	void customizesAccount() {
		// when:
		subject.customize(id, new HederaAccountCustomizer());

		// then:
		verify(ledger).customizePotentiallyDeleted(eq(id), any());
	}

	@Test
	void adjustsBalance() {
		// when:
		subject.adjustBalance(id, balance);

		// then:
		verify(ledger).adjustBalance(id, balance);
	}

	@Test
	void getsBalance() {
		// given:
		given(ledger.getBalance(id)).willReturn(balance);

		// when:
		final var result = subject.getBalance(id);

		//then:
		assertEquals(balance, result);
		// and:
		verify(ledger).getBalance(id);
	}

	@Test
	void checksIfDeleted() {
		// given:
		given(ledger.isDeleted(id)).willReturn(true);

		// when:
		assertTrue(subject.isDeleted(id));

		// and:
		verify(ledger).isDeleted(id);
	}

	@Test
	void checksIfExtant() {
		// given:
		given(ledger.exists(id)).willReturn(true);

		// when:
		assertTrue(subject.isExtant(id));

		// and:
		verify(ledger).exists(id);
	}

	@Test
	void looksUpAccount() {
		// given:
		given(ledger.get(id)).willReturn(merkleAccount);

		// when:
		final var result = subject.lookup(id);

		// then:
		assertEquals(merkleAccount, result);
		// and:
		verify(ledger).get(id);
	}

	@Test
	void putsZeroContractStorageValue() {
		// given:
		given(supplierContractStorage.get()).willReturn(contractStorage);

		// when:
		subject.put(id, contractStorageKey, UInt256.ZERO);

		// then:
		verify(contractStorage).put(expectedContractKey, new ContractValue());
	}

	@Test
	void putsNonZeroContractStorageValue() {
		// given:
		given(supplierContractStorage.get()).willReturn(contractStorage);

		// when:
		subject.put(id, contractStorageKey, contractStorageValue);

		// then:
		verify(contractStorage).put(expectedContractKey, expectedContractValue);
	}

	@Test
	void getsZeroContractStorageValue() {
		// given:
		given(supplierContractStorage.get()).willReturn(contractStorage);

		// when:
		final var result = subject.get(id, contractStorageKey);

		// then:
		assertEquals(UInt256.ZERO, result);
		// and:
		verify(contractStorage).get(expectedContractKey);
	}

	@Test
	void getsNonZeroContractStorageValue() {
		// given:
		given(supplierContractStorage.get()).willReturn(contractStorage);
		// and:
		given(contractStorage.get(expectedContractKey)).willReturn(expectedContractValue);

		// when:
		final var result = subject.get(id, contractStorageKey);

		// then:
		assertEquals(UInt256.MAX_VALUE, result);
		// and:
		verify(contractStorage).get(expectedContractKey);
	}

	@Test
	void storesBlob() {
		// given:
		given(supplierBytecode.get()).willReturn(bytecodeStorage);

		// when:
		subject.store(id, bytecode);

		// then:
		verify(bytecodeStorage).put(expectedBytecodeKey, expectedBytecodeValue);
	}

	@Test
	void fetchesEmptyBytecode() {
		// given:
		given(supplierBytecode.get()).willReturn(bytecodeStorage);

		// when:
		final var result = subject.fetch(id);

		// then:
		assertEquals(Bytes.EMPTY, result);
		// and:
		verify(bytecodeStorage).get(expectedBytecodeKey);
	}

	@Test
	void fetchesBytecode() {
		// given:
		given(supplierBytecode.get()).willReturn(bytecodeStorage);
		// and:
		given(bytecodeStorage.get(expectedBytecodeKey)).willReturn(expectedBytecodeValue);

		// when:
		final var result = subject.fetch(id);

		// then:
		assertEquals(bytecode, result);
		// and:
		verify(bytecodeStorage).get(expectedBytecodeKey);
	}
}