package com.hedera.services.ledger.accounts;

import com.hedera.services.utils.EntityNum;
import com.swirlds.common.CommonUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class StackedContractAliasesTest {
	private static final EntityNum num = EntityNum.fromLong(1234L);
	private static final byte[] rawNonMirrorAddress = CommonUtils.unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbb");
	private static final Address nonMirrorAddress = Address.wrap(Bytes.wrap(rawNonMirrorAddress));
	private static final Address mirrorAddress = num.toEvmAddress();

	@Mock
	private ContractAliases wrappedAliases;

	private StackedContractAliases subject;

	@BeforeEach
	void setUp() {
		subject = new StackedContractAliases(wrappedAliases);
	}

	@Test
	void refusesToLinkToNonMirrorAddress() {
		assertThrows(IllegalArgumentException.class, () -> subject.linkIfUnused(nonMirrorAddress, nonMirrorAddress));
	}

	@Test
	void linksUnusedAsExpected() {
		subject.linkIfUnused(nonMirrorAddress, mirrorAddress);
	}

	@Test
	void resolvesMirrorIdToSelf() {
		assertSame(mirrorAddress, subject.resolveForEvm(mirrorAddress));
	}
}