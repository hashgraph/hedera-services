package com.hedera.services.sigs.metadata;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.state.submerkle.FixedFeeSpec;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenMetaUtilsTest {
	@Test
	void classifiesRoyaltyWithFallback() {
		// setup:
		final var treasury = new EntityId(1, 2, 4);
		var royaltyFeeWithFallbackToken = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"ZPHYR", "West Wind Art", false, true,
				treasury);
		royaltyFeeWithFallbackToken.setTokenType(NON_FUNGIBLE_UNIQUE);
		royaltyFeeWithFallbackToken.setFeeSchedule(List.of(
				FcCustomFee.royaltyFee(
						1, 2,
						new FixedFeeSpec(1, null),
						new EntityId(1, 2, 5))));

		// given:
		final var meta = TokenMetaUtils.signingMetaFrom(royaltyFeeWithFallbackToken);

		// expect:
		assertTrue(meta.hasRoyaltyWithFallback());
		assertSame(treasury, meta.treasury());
	}

	@Test
	void classifiesRoyaltyWithNoFallback() {
		// setup:
		final var treasury = new EntityId(1, 2, 4);
		var royaltyFeeNoFallbackToken = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"ZPHYR", "West Wind Art", false, true,
				treasury);
		royaltyFeeNoFallbackToken.setTokenType(NON_FUNGIBLE_UNIQUE);
		royaltyFeeNoFallbackToken.setFeeSchedule(List.of(
				FcCustomFee.royaltyFee(
						1, 2,
						null,
						new EntityId(1, 2, 5))));

		// given:
		final var meta = TokenMetaUtils.signingMetaFrom(royaltyFeeNoFallbackToken);

		// expect:
		assertFalse(meta.hasRoyaltyWithFallback());
		assertSame(treasury, meta.treasury());
	}

	@Test
	void hmmm() throws TextFormat.InvalidEscapeSequenceException, InvalidProtocolBufferException {
		final var enc = "\\nt\\n\\020\\n\\t\\b\\337\\202\\303\\216\\006\\020\\341$\\022\\003\\030\\315\\t\\022\\002" +
				"\\030\\003\\030\\373\\225\\366\\024\\\"\\002\\bx2 " +
				"\\303\\203\\302\\256\\303\\202\\302\\267\\303\\203\\302\\271tF8\\303\\202\\302\\256J\\303\\203\\302" +
				"\\213\\303\\203\\302\\220\\303\\203\\302\\216Z1\\n\\\"\\022 " +
				"(\\240\\311a\\373\\306\\342\\200p\\255\\260\\253\\023Z\\337\\202\\246\\235\\202d\\222u\\305JK\\270" +
				"\\001`\\345\\316b~\\020\\200\\224\\353\\334\\003J\\005\\b\\200\\316\\332\\003\\022\\216\\001\\nE\\n" +
				"\\001\\030\\032@\\237yV\\263\\200\\364\\t\\003\\v\\277\\311\\333\\247V\\317\\330\\020\\325\\nR\\003N8o" +
				"\\030\\2041\\000\\317\\332$\\337U\\300\\241e\\004\\225\\300C%:\\327y\\320 \\t " +
				"\\3518R\\'\\035J\\305v\\205\\225S\\032\\'{\\v\\003\\nE\\n\\001" +
				"(\\032@ld\\357wG\\335m\\364\\366\\306\\205\\303\\223\\250\\265\\0361\\342\\2114\\3478\\3703\\335\\213" +
				"&\\212\\277\\035\\370H\\023>\\020\\265\\n\\330\\2538\\354v\\254Y\\232\\306{G\\213Ox\\316\\r\\376\\323" +
				"\\252\\203\\316k\\217\\314&\\'\\017";

		final var raw = TextFormat.unescapeBytes(enc);
		final var signedTxn = SignedTransaction.parseFrom(raw);
		final var txn = TransactionBody.parseFrom(signedTxn.getBodyBytes());
		System.out.println(txn);
	}
}
