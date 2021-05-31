package com.hedera.services.utils;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.swirlds.common.crypto.TransactionSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.EXPECTED_SIG;
import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.pk;
import static org.junit.jupiter.api.Assertions.*;

class RationalizedSigMetaTest {
	private final JKey payerKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();
	private final List<JKey> othersKeys = List.of(TxnHandlingScenario.MISC_ADMIN_KT.asJKeyUnchecked());
	private final List<TransactionSignature> rationalizedSigs = List.of(EXPECTED_SIG);

	private RationalizedSigMeta subject;

	@Test
	void noneAvailableHasNoInfo() {
		// given:
		subject = RationalizedSigMeta.noneAvailable();

		// then:
		assertFalse(subject.couldRationalizePayer());
		assertFalse(subject.couldRationalizeOthers());
		// and:
		assertThrows(IllegalStateException.class, subject::verifiedSigs);
		assertThrows(IllegalStateException.class, subject::payerKey);
		assertThrows(IllegalStateException.class, subject::othersReqSigs);
		assertThrows(IllegalStateException.class, subject::pkToVerifiedSigFn);
	}

	@Test
	void payerOnlyHasExpectedInfo() {
		// given:
		subject = RationalizedSigMeta.forPayerOnly(payerKey, rationalizedSigs);

		// then:
		assertTrue(subject.couldRationalizePayer());
		assertFalse(subject.couldRationalizeOthers());
		// and:
		assertSame(payerKey, subject.payerKey());
		assertSame(rationalizedSigs, subject.verifiedSigs());
		assertSame(EXPECTED_SIG, subject.pkToVerifiedSigFn().apply(pk));
	}

	@Test
	void forBothHaveExpectedInfo() {
		// given:
		subject = RationalizedSigMeta.forPayerAndOthers(payerKey, othersKeys, rationalizedSigs);

		// then:
		assertTrue(subject.couldRationalizePayer());
		assertTrue(subject.couldRationalizeOthers());
		// and:
		assertSame(payerKey, subject.payerKey());
		assertSame(othersKeys, subject.othersReqSigs());
		assertSame(rationalizedSigs, subject.verifiedSigs());
		assertSame(EXPECTED_SIG, subject.pkToVerifiedSigFn().apply(pk));
	}
}