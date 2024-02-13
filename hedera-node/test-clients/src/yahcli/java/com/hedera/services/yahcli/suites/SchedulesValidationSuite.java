/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.yahcli.suites;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.persistence.SpecKey;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleCreate;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.yahcli.commands.validation.ValidationCommand;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SchedulesValidationSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(SchedulesValidationSuite.class);

    private final Map<String, String> specConfig;

    public SchedulesValidationSuite(Map<String, String> specConfig) {
        this.specConfig = specConfig;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            validateScheduling(),
        });
    }

    private HapiSpec validateScheduling() {
        String inSpecSchedule = "forImmediateExecution";
        AtomicLong seqNo = new AtomicLong();

        return HapiSpec.customHapiSpec("ValidateScheduling")
                .withProperties(specConfig)
                .given(
                        QueryVerbs.getScheduleInfo(ValidationCommand.MUTABLE_SCHEDULE)
                                .payingWith(ValidationCommand.PAYER)
                                .isNotExecuted()
                                .hasEntityMemo("When love with one another so / Inter-animates two souls")
                                .hasAdminKey(ValidationCommand.MUTABLE_SCHEDULE),
                        QueryVerbs.getScheduleInfo(ValidationCommand.IMMUTABLE_SCHEDULE)
                                .payingWith(ValidationCommand.PAYER)
                                .isNotExecuted()
                                .hasEntityMemo("That abler soul, which thence doth flow / Defects of"
                                        + " loneliness controls"),
                        QueryVerbs.getTopicInfo(ValidationCommand.TOPIC).savingSeqNoTo(seqNo::set),
                        TxnVerbs.scheduleDelete(ValidationCommand.IMMUTABLE_SCHEDULE)
                                .payingWith(ValidationCommand.PAYER)
                                .hasKnownStatus(ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE),
                        UtilVerbs.logIt(ValidationCommand.checkBoxed("Schedule creation looks good")))
                .when(
                        TxnVerbs.scheduleDelete(ValidationCommand.MUTABLE_SCHEDULE)
                                .signedBy(ValidationCommand.PAYER)
                                .payingWith(ValidationCommand.PAYER)
                                .hasKnownStatus(ResponseCodeEnum.INVALID_SIGNATURE),
                        TxnVerbs.scheduleDelete(ValidationCommand.MUTABLE_SCHEDULE)
                                .signedBy(
                                        ValidationCommand.PAYER,
                                        SpecKey.adminKeyFor(ValidationCommand.MUTABLE_SCHEDULE))
                                .payingWith(ValidationCommand.PAYER),
                        TxnVerbs.scheduleSign(ValidationCommand.MUTABLE_SCHEDULE)
                                .alsoSigningWith(ValidationCommand.SENDER)
                                .payingWith(ValidationCommand.PAYER)
                                .hasKnownStatus(ResponseCodeEnum.SCHEDULE_ALREADY_DELETED),
                        TxnVerbs.scheduleSign(ValidationCommand.IMMUTABLE_SCHEDULE)
                                .alsoSigningWith(ValidationCommand.SENDER)
                                .savingScheduledTxnId()
                                .payingWith(ValidationCommand.PAYER),
                        TxnVerbs.scheduleCreate(
                                        inSpecSchedule,
                                        TxnVerbs.submitMessageTo(ValidationCommand.TOPIC)
                                                .message("We then who are this new soul know"))
                                .payingWith(ValidationCommand.PAYER)
                                .adminKey(SpecKey.adminKeyFor(ValidationCommand.MUTABLE_SCHEDULE))
                                .alsoSigningWith(SpecKey.submitKeyFor(ValidationCommand.TOPIC))
                                .withEntityMemo("Of what we are composed and made")
                                .savingExpectedScheduledTxnId(),
                        TxnVerbs.scheduleCreate(
                                        inSpecSchedule,
                                        TxnVerbs.submitMessageTo(ValidationCommand.TOPIC)
                                                .message("We then who are this new soul know"))
                                .payingWith(ValidationCommand.PAYER)
                                .designatingPayer(ValidationCommand.PAYER)
                                .adminKey(SpecKey.adminKeyFor(ValidationCommand.MUTABLE_SCHEDULE))
                                .alsoSigningWith(SpecKey.submitKeyFor(ValidationCommand.TOPIC))
                                .withEntityMemo("Of what we are composed and made")
                                .hasKnownStatus(IDENTICAL_SCHEDULE_ALREADY_CREATED),
                        TxnVerbs.scheduleDelete(inSpecSchedule)
                                .signedBy(
                                        ValidationCommand.PAYER,
                                        SpecKey.adminKeyFor(ValidationCommand.MUTABLE_SCHEDULE))
                                .payingWith(ValidationCommand.PAYER)
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        UtilVerbs.logIt(ValidationCommand.checkBoxed("Schedule management and execution look good")))
                .then(
                        QueryVerbs.getTopicInfo(ValidationCommand.TOPIC)
                                .payingWith(ValidationCommand.PAYER)
                                .hasSeqNo(() -> seqNo.get() + 1)
                                .logged(),
                        QueryVerbs.getScheduleInfo(inSpecSchedule)
                                .payingWith(ValidationCommand.PAYER)
                                .isExecuted(),
                        QueryVerbs.getTxnRecord(HapiScheduleCreate.correspondingScheduledTxnId(
                                        ValidationCommand.IMMUTABLE_SCHEDULE))
                                .payingWith(ValidationCommand.PAYER)
                                .logged()
                                .assertingNothingAboutHashes(),
                        QueryVerbs.getTxnRecord(HapiScheduleCreate.correspondingScheduledTxnId(inSpecSchedule))
                                .payingWith(ValidationCommand.PAYER)
                                .logged()
                                .assertingNothingAboutHashes(),
                        UtilVerbs.logIt(ValidationCommand.checkBoxed("Schedule records look good")));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
