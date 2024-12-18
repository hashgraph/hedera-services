// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.cli;

import com.swirlds.cli.commands.StateCommand;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.config.DefaultConfiguration;
import com.swirlds.platform.roster.RosterRetriever;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.snapshot.DeserializedSignedState;
import com.swirlds.platform.state.snapshot.SignedStateFileReader;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.address.AddressBookUtils;
import com.swirlds.platform.system.address.AddressBookValidator;
import com.swirlds.platform.util.BootstrapUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.concurrent.ExecutionException;
import picocli.CommandLine;

@CommandLine.Command(
        name = "validateAddressBook",
        mixinStandardHelpOptions = true,
        description = "Validates the given address book as a successor to the address book in the given state.")
@SubcommandOf(StateCommand.class)
public class ValidateAddressBookStateCommand extends AbstractCommand {
    private Path statePath;
    private Path addressBookPath;

    /**
     * The path to state to edit
     */
    @CommandLine.Parameters(description = "The path to the state", index = "0")
    private void setStatePath(final Path statePath) {
        this.statePath = pathMustExist(statePath.toAbsolutePath());
    }

    /**
     * The path to the address book to validate
     */
    @CommandLine.Parameters(description = "The path to the address book to validate as a successor", index = "1")
    private void setAddressBookPath(final Path addressBookPath) {
        this.addressBookPath = pathMustExist(addressBookPath.toAbsolutePath());
    }

    @Override
    public Integer call() throws IOException, ExecutionException, InterruptedException, ParseException {
        final Configuration configuration = DefaultConfiguration.buildBasicConfiguration(ConfigurationBuilder.create());
        BootstrapUtils.setupConstructableRegistry();

        System.out.printf("Reading state from %s %n", statePath.toAbsolutePath());
        final DeserializedSignedState deserializedSignedState =
                SignedStateFileReader.readStateFile(configuration, statePath);

        System.out.printf("Reading address book from %s %n", addressBookPath.toAbsolutePath());
        final String addressBookString = Files.readString(addressBookPath);
        final AddressBook addressBook = AddressBookUtils.parseAddressBookText(addressBookString);

        final AddressBook stateAddressBook;
        try (final ReservedSignedState reservedSignedState = deserializedSignedState.reservedSignedState()) {
            System.out.printf("Extracting the state address book for comparison %n");
            stateAddressBook = RosterUtils.buildAddressBook(RosterRetriever.retrieveActiveOrGenesisRoster(
                    reservedSignedState.get().getState()));
        }

        System.out.printf("Validating address book %n");
        // if the address book is not valid an exception will be thrown which will propagate up to the CLI
        AddressBookValidator.validateNewAddressBook(stateAddressBook, addressBook);

        System.out.printf("PASS: The address book is valid as a successor to the state's address book %n");
        return 0;
    }
}
