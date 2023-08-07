package com.swirlds.platform.event.validation;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.crypto.SignatureVerifier;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureValidatorTest {
	@Test
	void testMultipleAddressBooks(){
		final AddressBook full = new RandomAddressBookGenerator(RandomUtils.getRandomPrintSeed())
				.setSize(10)
				.build();
		final NodeId removedNodeId = full.getNodeId(0);
		final AddressBook reduced = full.remove(removedNodeId);
		final SignatureVerifier signatureVerifier = (data, signature, publicKey) -> true;
		final SignatureValidator validator = new SignatureValidator(List.of(full, reduced), signatureVerifier);
		for (final AddressBook ab : List.of(full, reduced)) {
			for (final Address address : ab) {
				assertTrue(validator.isEventValid(createEvent(address.getNodeId())),
						"if an event is signed by a node in any address book, it should be valid");
			}
		}
		for (final AddressBook ab : List.of(full, reduced)) {
			assertFalse(validator.isEventValid(createEvent(ab.getNextNodeId())),
					"if an event is signed by a node NOT in any address book, it should be valid");
		}
	}

	private static @NonNull GossipEvent createEvent(@NonNull final NodeId id) {
		final GossipEvent event = Mockito.mock(GossipEvent.class);
		final BaseEventHashedData hd = Mockito.mock(BaseEventHashedData.class);
		final BaseEventUnhashedData ud = Mockito.mock(BaseEventUnhashedData.class);
		final Hash hash = Mockito.mock(Hash.class);
		Mockito.when(hd.getCreatorId()).thenReturn(id);
		Mockito.when(hd.getHash()).thenReturn(hash);
		Mockito.when(event.getHashedData()).thenReturn(hd);
		Mockito.when(event.getUnhashedData()).thenReturn(ud);

		return event;
	}
}