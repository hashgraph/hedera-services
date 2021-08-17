package com.hedera.services.yahcli.commands.system;

/*-
 * ‌
 * Hedera Services Test Clients
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

import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;

import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.suites.FreezeSuite;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
    name = "freeze",
    subcommands = {picocli.CommandLine.HelpCommand.class},
    description = "Freeze system at given start time")
public class SysFreezeCommand implements Callable<Integer> {

  @CommandLine.ParentCommand private Yahcli yahcli;

  @CommandLine.Option(
      names = {"-s", "--start-time"},
      paramLabel = "Freeze start time in UTC, use format 'yyyy-MM-dd.HH:mm:ss'",
      defaultValue = "")
  private String freezeStartTimeStr;

  @Override
  public Integer call() throws Exception {
    final var config = configFrom(yahcli);
    final var freezeStartTime = getFreezeStartTime(freezeStartTimeStr);
    final var delegate = new FreezeSuite(config.asSpecConfig(), freezeStartTime);

    delegate.runSuiteSync();

    return 0;
  }

  private Instant getFreezeStartTime(final String timeStampInStr) {
    final var dtf =
        DateTimeFormatter.ofPattern("yyyy-MM-dd.HH:mm:ss").withZone(ZoneId.of("Etc/UTC"));

    return Instant.from(dtf.parse(timeStampInStr));
  }
}
