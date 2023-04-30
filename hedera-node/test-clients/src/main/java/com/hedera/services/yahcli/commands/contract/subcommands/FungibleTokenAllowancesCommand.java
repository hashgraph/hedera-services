/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.yahcli.commands.contract.subcommands;

import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.services.yahcli.commands.contract.ContractCommand;
import com.hedera.services.yahcli.commands.contract.utils.SignedStateHolder;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.Callable;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "fungibleallowances",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Dump information about allowances to stdout")
public class FungibleTokenAllowancesCommand implements Callable<Integer> {

    @ParentCommand
    private ContractCommand contractCommand;

    @Option(
            names = {"-f", "--file"},
            arity = "1",
            description = "Input signed state file")
    Path inputFile;

    static final HexFormat hexer = HexFormat.of().withPrefix("").withUpperCase();

    enum Type {
        EOA,
        CONTRACT;

        public static Type getIsSmartContract(final boolean isSmartContract) {
            return isSmartContract ? CONTRACT : EOA;
        }
    }

    enum Alive {
        YES,
        NO;

        public static Alive getIsDeleted(final boolean isDeleted) {
            return isDeleted ? NO : YES;
        }
    }

    record AnAccount(
            EntityNum accountEntityNum,
            String address,
            Type accountType,
            long accountBalance,
            Alive alive,
            Set<AnAllowance> allowances) {
        AnAccount(
                final EntityNum accountEntityNum,
                final String address,
                final Type accountType,
                final long accountBalance,
                final Alive alive) {

            this(accountEntityNum, address, accountType, accountBalance, alive, new HashSet<>(0 /*WAG*/));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj == this) {
                return true;
            }
            if (obj.getClass() != getClass()) {
                return false;
            }
            final var rhs = (AnAccount) obj;
            return this.accountEntityNum == rhs.accountEntityNum
                    && this.address.equals(rhs.address)
                    && this.accountType == rhs.accountType
                    && this.accountBalance == rhs.accountBalance
                    && this.alive == rhs.alive
                    && this.allowances == rhs.allowances;
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(accountEntityNum)
                    .append(address)
                    .append(accountType)
                    .append(accountBalance)
                    .append(alive)
                    .toHashCode();
        }
    }

    record AnAllowance(AnAccount account, EntityNum tokenNum, EntityNum spenderNum, long amount) {}

    @Override
    public Integer call() throws Exception {

        contractCommand.setupLogging();

        try (final var signedState = new SignedStateHolder(inputFile)) {

            final var allAccountsSeen = new HashSet<AnAccount>(10_000 /*WAG*/);
            final var allAllowancesSeen = new HashSet<AnAllowance>(10_000 /*WAG*/);

            signedState.getAccounts().forEach((accountNum, account) -> {
                final var isSmartContract = account.isSmartContract();
                final var balance = account.getBalance();
                final var isDeleted = account.isDeleted();
                final var addressAsBytes = account.getAlias().toByteArray();
                final var address =
                        (null != addressAsBytes && 0 < addressAsBytes.length) ? hexer.formatHex(addressAsBytes) : "";

                final var fungibleTokenAllowances = account.getFungibleTokenAllowances();
                if (fungibleTokenAllowances.isEmpty())
                    return; // short circuit: not interested if there are no allowances

                final var thisAccount = new AnAccount(
                        accountNum,
                        address,
                        Type.getIsSmartContract(isSmartContract),
                        balance,
                        Alive.getIsDeleted(isDeleted));
                allAccountsSeen.add(thisAccount);

                account.getFungibleTokenAllowances().forEach((allowanceId, amount) -> {
                    final var tokenNum = allowanceId.getTokenNum();
                    final var spenderNum = allowanceId.getSpenderNum();

                    final var thisAllowance = new AnAllowance(thisAccount, tokenNum, spenderNum, amount);
                    allAllowancesSeen.add(thisAllowance);
                    thisAccount.allowances().add(thisAllowance);
                });
            });

            // A pipeline to arrange the output nicely:
            //    ./yahcli contract fungibleallowances --suppress-logs -f <signed-state-directory>/SignedState.swh |
            // sort -n
            // -t','
            System.out.println("# %d allowances to fungible tokens found, %d accounts with such allowances found"
                    .formatted(allAllowancesSeen.size(), allAccountsSeen.size()));
            System.out.println("account#,alias,type,balance,isAlive,token#,spender#,amount");
            allAllowancesSeen.forEach(aa -> {
                final var account = aa.account();
                final var accountNum = account.accountEntityNum().intValue();
                final var tokenNum = aa.tokenNum().intValue();
                final var spenderNum = aa.spenderNum().intValue();

                System.out.printf("%d,%s,%s,%d,%s,%d,%d,%d%n"
                        .formatted(
                                accountNum,
                                account.address(),
                                account.accountType.name(),
                                account.accountBalance(),
                                account.alive().name(),
                                tokenNum,
                                spenderNum,
                                aa.amount));
            });
        }
        return 0;
    }
}
