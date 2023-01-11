/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.persistence.SpecKey.adminKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.submitKeyFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleCreate.correspondingScheduledTxnId;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.IMMUTABLE_SCHEDULE;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.MUTABLE_SCHEDULE;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.PAYER;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.SENDER;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.TOPIC;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.checkBoxed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
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
        return List.of(
                new HapiSpec[] {
                    validateScheduling(),
                });
    }

    private HapiSpec validateScheduling() {
        String inSpecSchedule = "forImmediateExecution";
        AtomicLong seqNo = new AtomicLong();

        return customHapiSpec("ValidateScheduling")
                .withProperties(specConfig)
                .given(
                        getScheduleInfo(MUTABLE_SCHEDULE)
                                .payingWith(PAYER)
                                .isNotExecuted()
                                .hasEntityMemo(
                                        "When love with one another so / Inter-animates two souls")
                                .hasAdminKey(MUTABLE_SCHEDULE),
                        getScheduleInfo(IMMUTABLE_SCHEDULE)
                                .payingWith(PAYER)
                                .isNotExecuted()
                                .hasEntityMemo(
                                        "That abler soul, which thence doth flow / Defects of"
                                                + " loneliness controls"),
                        getTopicInfo(TOPIC).savingSeqNoTo(seqNo::set),
                        scheduleDelete(IMMUTABLE_SCHEDULE)
                                .payingWith(PAYER)
                                .hasKnownStatus(SCHEDULE_IS_IMMUTABLE),
                        logIt(checkBoxed("Schedule creation looks good")))
                .when(
                        scheduleDelete(MUTABLE_SCHEDULE)
                                .signedBy(PAYER)
                                .payingWith(PAYER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        scheduleDelete(MUTABLE_SCHEDULE)
                                .signedBy(PAYER, adminKeyFor(MUTABLE_SCHEDULE))
                                .payingWith(PAYER),
                        scheduleSign(MUTABLE_SCHEDULE)
                                .alsoSigningWith(SENDER)
                                .payingWith(PAYER)
                                .hasKnownStatus(SCHEDULE_ALREADY_DELETED),
                        scheduleSign(IMMUTABLE_SCHEDULE)
                                .alsoSigningWith(SENDER)
                                .savingScheduledTxnId()
                                .payingWith(PAYER),
                        scheduleCreate(
                                        inSpecSchedule,
                                        submitMessageTo(TOPIC)
                                                .message("We then who are this new soul know"))
                                .payingWith(PAYER)
                                .adminKey(adminKeyFor(MUTABLE_SCHEDULE))
                                .alsoSigningWith(submitKeyFor(TOPIC))
                                .withEntityMemo("Of what we are composed and made")
                                .savingExpectedScheduledTxnId(),
                        scheduleCreate(
                                        inSpecSchedule,
                                        submitMessageTo(TOPIC)
                                                .message("We then who are this new soul know"))
                                .payingWith(PAYER)
                                .designatingPayer(PAYER)
                                .adminKey(adminKeyFor(MUTABLE_SCHEDULE))
                                .alsoSigningWith(submitKeyFor(TOPIC))
                                .withEntityMemo("Of what we are composed and made")
                                .hasKnownStatus(IDENTICAL_SCHEDULE_ALREADY_CREATED),
                        scheduleDelete(inSpecSchedule)
                                .signedBy(PAYER, adminKeyFor(MUTABLE_SCHEDULE))
                                .payingWith(PAYER)
                                .hasKnownStatus(SCHEDULE_ALREADY_EXECUTED),
                        logIt(checkBoxed("Schedule management and execution look good")))
                .then(
                        getTopicInfo(TOPIC)
                                .payingWith(PAYER)
                                .hasSeqNo(() -> seqNo.get() + 1)
                                .logged(),
                        getScheduleInfo(inSpecSchedule).payingWith(PAYER).isExecuted(),
                        getTxnRecord(correspondingScheduledTxnId(IMMUTABLE_SCHEDULE))
                                .payingWith(PAYER)
                                .logged()
                                .assertingNothingAboutHashes(),
                        getTxnRecord(correspondingScheduledTxnId(inSpecSchedule))
                                .payingWith(PAYER)
                                .logged()
                                .assertingNothingAboutHashes(),
                        logIt(checkBoxed("Schedule records look good")));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
