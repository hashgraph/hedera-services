package com.hedera.services.state.org;

import com.hedera.services.context.ServicesContext;
import com.swirlds.fchashmap.FCOneToManyRelation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StateMetadataTest {
	@Mock
	private ServicesContext ctx;
	@Mock
	private FCOneToManyRelation<Integer, Long> uniqueTokenAssociations;
	@Mock
	private FCOneToManyRelation<Integer, Long> uniqueOwnershipAssociations;
	@Mock
	private FCOneToManyRelation<Integer, Long> uniqueTreasuryOwnershipAssociations;

	private StateMetadata subject;

	@BeforeEach
	void setUp() {
		subject = new StateMetadata(ctx);
	}

	@Test
	void copyAsExpected() {
		setupWithMockFcotmr();

		given(uniqueTokenAssociations.copy()).willReturn(uniqueTokenAssociations);
		given(uniqueOwnershipAssociations.copy()).willReturn(uniqueOwnershipAssociations);
		given(uniqueTreasuryOwnershipAssociations.copy()).willReturn(uniqueTreasuryOwnershipAssociations);

		// when:
		final var copy = subject.copy();

		// then:
		assertSame(ctx, copy.getCtx());
		// and:
		assertSame(uniqueTokenAssociations, copy.getUniqueTokenAssociations());
		verify(uniqueTokenAssociations).copy();
		assertSame(uniqueOwnershipAssociations, copy.getUniqueOwnershipAssociations());
		verify(uniqueOwnershipAssociations).copy();
		assertSame(uniqueTreasuryOwnershipAssociations, copy.getUniqueTreasuryOwnershipAssociations());
		verify(uniqueTreasuryOwnershipAssociations).copy();
	}

	@Test
	void releaseOnArchival() {
		setupWithMockFcotmr();

		// when:
		subject.archive();

		// then:
		verify(uniqueTokenAssociations).release();
		verify(uniqueOwnershipAssociations).release();
		verify(uniqueTreasuryOwnershipAssociations).release();
	}

	@Test
	void releaseOnRelease() {
		setupWithMockFcotmr();

		// when:
		subject.release();

		// then:
		verify(uniqueTokenAssociations).release();
		verify(uniqueOwnershipAssociations).release();
		verify(uniqueTreasuryOwnershipAssociations).release();
	}

	private void setupWithMockFcotmr() {
		subject.setUniqueTokenAssociations(uniqueTokenAssociations);
		subject.setUniqueOwnershipAssociations(uniqueOwnershipAssociations);
		subject.setUniqueTreasuryOwnershipAssociations(uniqueTreasuryOwnershipAssociations);
	}

	@Test
	void gettersWork() {
		// expect:
		assertSame(ctx, subject.getCtx());
		assertNotNull(subject.getUniqueTokenAssociations());
		assertNotNull(subject.getUniqueOwnershipAssociations());
		assertNotNull(subject.getUniqueTreasuryOwnershipAssociations());
	}
}