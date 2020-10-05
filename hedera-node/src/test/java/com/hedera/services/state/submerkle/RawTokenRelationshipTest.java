package com.hedera.services.state.submerkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class RawTokenRelationshipTest {
	long num = 123;
	long balance = 234;
	boolean frozen = true;
	boolean kyc = false;

	MerkleToken token;
	RawTokenRelationship subject = new RawTokenRelationship(balance, 0, 0, num, frozen, kyc);

	@BeforeEach
	void setUp() {
		token = mock(MerkleToken.class);
		given(token.symbol()).willReturn("HEYMA");
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals(
				"RawTokenRelationship{token=0.0.123, balance=234, frozen=true, kycGranted=false}",
				subject.toString());
	}

	@Test
	public void objectContractMet() {
		// given:
		var identicalSubject = new RawTokenRelationship(balance, 0, 0, num, frozen, kyc);
		// and:
		var otherSubject = new RawTokenRelationship(balance * 2, 0, 0, num - 1, !frozen, !kyc);

		// expect:
		assertNotEquals(subject, null);
		assertNotEquals(subject, otherSubject);
		assertEquals(subject, identicalSubject);
		// and:
		assertNotEquals(subject.hashCode(), otherSubject.hashCode());
		assertEquals(subject.hashCode(), identicalSubject.hashCode());
	}

	@Test
	public void grpcConversionRecognizesInapplicable() {
		// when:
		var desc = subject.asGrpcFor(token);

		// then:
		assertEquals(balance, desc.getBalance());
		assertEquals(IdUtils.tokenWith(num), desc.getTokenId());
		assertEquals(TokenFreezeStatus.FreezeNotApplicable, desc.getFreezeStatus());
		assertEquals(TokenKycStatus.KycNotApplicable, desc.getKycStatus());
	}

	@Test
	public void grpcConversionRecognizesApplicableFrozen() {
		given(token.hasFreezeKey()).willReturn(true);

		// when:
		var desc = subject.asGrpcFor(token);

		// then:
		assertEquals(balance, desc.getBalance());
		assertEquals(IdUtils.tokenWith(num), desc.getTokenId());
		assertEquals(TokenFreezeStatus.Frozen, desc.getFreezeStatus());
		assertEquals(TokenKycStatus.KycNotApplicable, desc.getKycStatus());
	}

	@Test
	public void grpcConversionRecognizesApplicableUnfozen() {
		// setup:
		subject = new RawTokenRelationship(subject.getBalance(), 0, 0, subject.getTokenNum(), false, false);

		given(token.hasFreezeKey()).willReturn(true);

		// when:
		var desc = subject.asGrpcFor(token);

		// then:
		assertEquals(balance, desc.getBalance());
		assertEquals(IdUtils.tokenWith(num), desc.getTokenId());
		assertEquals(TokenFreezeStatus.Unfrozen, desc.getFreezeStatus());
		assertEquals(TokenKycStatus.KycNotApplicable, desc.getKycStatus());
	}

	@Test
	public void grpcConversionRecognizesApplicableKycRevoked() {
		given(token.hasKycKey()).willReturn(true);

		// when:
		var desc = subject.asGrpcFor(token);

		// then:
		assertEquals(balance, desc.getBalance());
		assertEquals(IdUtils.tokenWith(num), desc.getTokenId());
		assertEquals(TokenFreezeStatus.FreezeNotApplicable, desc.getFreezeStatus());
		assertEquals(TokenKycStatus.Revoked, desc.getKycStatus());
	}

	@Test
	public void grpcConversionRecognizesApplicableGranted() {
		// setup:
		subject = new RawTokenRelationship(subject.getBalance(), 0, 0, subject.getTokenNum(), false, true);

		given(token.hasKycKey()).willReturn(true);

		// when:
		var desc = subject.asGrpcFor(token);

		// then:
		assertEquals(balance, desc.getBalance());
		assertEquals(IdUtils.tokenWith(num), desc.getTokenId());
		assertEquals(TokenFreezeStatus.FreezeNotApplicable, desc.getFreezeStatus());
		assertEquals(TokenKycStatus.Granted, desc.getKycStatus());
		assertEquals("HEYMA", desc.getSymbol());
	}

	@Test
	public void getsId() {
		// expect:
		assertEquals(IdUtils.tokenWith(num), subject.id());
	}
}