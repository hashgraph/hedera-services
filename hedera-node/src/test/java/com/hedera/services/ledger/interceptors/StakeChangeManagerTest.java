package com.hedera.services.ledger.interceptors;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.properties.AccountProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class StakeChangeManagerTest {
	private StakeChangeManager subject;

	@Test
	void validatesIfAnyStakedFieldChanges() {
		assertTrue(subject.hasStakeFieldChanges(randomStakeFieldChanges(100L)));
		assertFalse(subject.hasStakeFieldChanges(randomNotStakeFieldChanges(100L)));
	}


	private Map<AccountProperty, Object> randomStakeFieldChanges(final long newBalance) {
		return Map.of(
				AccountProperty.BALANCE, newBalance,
				AccountProperty.STAKED_ID, -2L);
	}

	private Map<AccountProperty, Object> randomNotStakeFieldChanges(final long newBalance) {
		return Map.of(
				AccountProperty.ALIAS, ByteString.copyFromUtf8("testing"));
	}
}
