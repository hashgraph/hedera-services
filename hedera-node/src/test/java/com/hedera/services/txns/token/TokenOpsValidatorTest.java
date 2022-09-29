/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.txns.token;

import static com.hedera.services.txns.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.METADATA_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenOpsValidatorTest {
    @Mock private OptionValidator validator;

    @Test
    void validateNftTokenMintHappyPath() {
        given(validator.maxBatchSizeMintCheck(1)).willReturn(OK);
        given(validator.nftMetadataCheck(any())).willReturn(OK);

        assertEquals(OK, forMintWith(1, 0, true));
    }

    @Test
    void validateFungibleTokenMintHappyPath() {
        assertEquals(OK, forMintWith(0, 1, false));
    }

    @Test
    void tokenMintFailsWithInvalidTokenCounts() {
        assertEquals(OK, forMintWith(0, 0, true));

        assertEquals(INVALID_TRANSACTION_BODY, forMintWith(1, 1, true));
        assertEquals(INVALID_TOKEN_MINT_AMOUNT, forMintWith(1, -1, true));
        assertEquals(INVALID_TOKEN_MINT_AMOUNT, forMintWith(0, -1, true));
    }

    @Test
    void nftTokenMintFailsWhenNftsDisabled() {
        assertEquals(NOT_SUPPORTED, forMintWith(1, 0, false));
    }

    @Test
    void nftTokenMintFailsWhenInvalidMetaData() {
        given(validator.maxBatchSizeMintCheck(1)).willReturn(OK);
        given(validator.nftMetadataCheck(any())).willReturn(METADATA_TOO_LONG);

        assertEquals(METADATA_TOO_LONG, forMintWith(1, 0, true));
    }

    @Test
    void nftTokenMintFailsWhenInvalidBatchSize() {
        given(validator.maxBatchSizeMintCheck(1)).willReturn(BATCH_SIZE_LIMIT_EXCEEDED);

        assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, forMintWith(1, 0, true));
    }

    @Test
    void nftTokenBurnHappyPath() {
        given(validator.maxBatchSizeBurnCheck(4)).willReturn(OK);

        assertEquals(
                OK,
                validateTokenOpsWith(
                        4,
                        0,
                        true,
                        INVALID_TOKEN_BURN_AMOUNT,
                        new ArrayList<>(List.of(1L, 2L, 3L, 4L)),
                        validator::maxBatchSizeBurnCheck));
    }

    @Test
    void nftTokenBurnFailsWithInvalidSerialNum() {
        given(validator.maxBatchSizeBurnCheck(4)).willReturn(OK);

        assertEquals(
                INVALID_NFT_ID,
                validateTokenOpsWith(
                        4,
                        0,
                        true,
                        INVALID_TOKEN_BURN_AMOUNT,
                        new ArrayList<>(List.of(1L, -2L, 3L, 4L)),
                        validator::maxBatchSizeBurnCheck));
    }

    private ResponseCodeEnum forMintWith(int nftCount, long fungibleCount, boolean areNftEnabled) {
        return validateTokenOpsWith(
                nftCount,
                fungibleCount,
                areNftEnabled,
                INVALID_TOKEN_MINT_AMOUNT,
                new ArrayList<>(List.of(ByteString.copyFromUtf8("memo"))),
                validator::maxBatchSizeMintCheck,
                validator::nftMetadataCheck);
    }
}
