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
package com.hedera.services.sigs.order;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_DELEGATING_SPENDER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;

/**
 * Implements a {@link SigningOrderResultFactory} that reports errors via instances of {@link
 * ResponseCodeEnum}.
 */
public enum CodeOrderResultFactory implements SigningOrderResultFactory<ResponseCodeEnum> {
    CODE_ORDER_RESULT_FACTORY;

    @Override
    public SigningOrderResult<ResponseCodeEnum> forValidOrder(List<JKey> keys) {
        return new SigningOrderResult<>(keys);
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forGeneralError() {
        return GENERAL_ERROR_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forInvalidAccount() {
        return INVALID_ACCOUNT_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forInvalidContract() {
        return MISSING_CONTRACT_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forImmutableContract() {
        return IMMUTABLE_CONTRACT_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forMissingFile() {
        return MISSING_FILE_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forMissingAccount() {
        return MISSING_ACCOUNT_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forMissingToken() {
        return MISSING_TOKEN_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forMissingSchedule() {
        return MISSING_SCHEDULE_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forGeneralPayerError() {
        return GENERAL_PAYER_ERROR_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forMissingTopic() {
        return MISSING_TOPIC_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forInvalidAutoRenewAccount() {
        return INVALID_AUTORENEW_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forUnresolvableRequiredSigners() {
        return UNRESOLVABLE_SIGNERS_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forUnschedulableTxn() {
        return UNSCHEDULABLE_TRANSACTION_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forInvalidFeeCollector() {
        return INVALID_FEE_COLLECTOR_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forInvalidAllowanceOwner() {
        return MISSING_ALLOWANCE_OWNER_RESULT;
    }

    @Override
    public SigningOrderResult<ResponseCodeEnum> forInvalidDelegatingSpender() {
        return MISSING_DELEGATING_SPENDER_RESULT;
    }

    static final SigningOrderResult<ResponseCodeEnum> INVALID_ACCOUNT_RESULT =
            new SigningOrderResult<>(INVALID_ACCOUNT_ID);
    static final SigningOrderResult<ResponseCodeEnum> GENERAL_ERROR_RESULT =
            new SigningOrderResult<>(INVALID_SIGNATURE);
    static final SigningOrderResult<ResponseCodeEnum> GENERAL_PAYER_ERROR_RESULT =
            new SigningOrderResult<>(INVALID_SIGNATURE);
    static final SigningOrderResult<ResponseCodeEnum> MISSING_ACCOUNT_RESULT =
            new SigningOrderResult<>(ACCOUNT_ID_DOES_NOT_EXIST);
    static final SigningOrderResult<ResponseCodeEnum> MISSING_FILE_RESULT =
            new SigningOrderResult<>(INVALID_FILE_ID);
    static final SigningOrderResult<ResponseCodeEnum> MISSING_CONTRACT_RESULT =
            new SigningOrderResult<>(INVALID_CONTRACT_ID);
    static final SigningOrderResult<ResponseCodeEnum> IMMUTABLE_CONTRACT_RESULT =
            new SigningOrderResult<>(MODIFYING_IMMUTABLE_CONTRACT);
    static final SigningOrderResult<ResponseCodeEnum> MISSING_TOPIC_RESULT =
            new SigningOrderResult<>(INVALID_TOPIC_ID);
    static final SigningOrderResult<ResponseCodeEnum> INVALID_AUTORENEW_RESULT =
            new SigningOrderResult<>(INVALID_AUTORENEW_ACCOUNT);
    static final SigningOrderResult<ResponseCodeEnum> MISSING_TOKEN_RESULT =
            new SigningOrderResult<>(INVALID_TOKEN_ID);
    static final SigningOrderResult<ResponseCodeEnum> MISSING_SCHEDULE_RESULT =
            new SigningOrderResult<>(INVALID_SCHEDULE_ID);
    static final SigningOrderResult<ResponseCodeEnum> UNRESOLVABLE_SIGNERS_RESULT =
            new SigningOrderResult<>(UNRESOLVABLE_REQUIRED_SIGNERS);
    static final SigningOrderResult<ResponseCodeEnum> UNSCHEDULABLE_TRANSACTION_RESULT =
            new SigningOrderResult<>(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
    static final SigningOrderResult<ResponseCodeEnum> INVALID_FEE_COLLECTOR_RESULT =
            new SigningOrderResult<>(INVALID_CUSTOM_FEE_COLLECTOR);
    static final SigningOrderResult<ResponseCodeEnum> MISSING_ALLOWANCE_OWNER_RESULT =
            new SigningOrderResult<>(INVALID_ALLOWANCE_OWNER_ID);
    static final SigningOrderResult<ResponseCodeEnum> MISSING_DELEGATING_SPENDER_RESULT =
            new SigningOrderResult<>(INVALID_DELEGATING_SPENDER);
}
