# Services CLI

## Purpose

The Services CLI is a developer command-line tool for performing miscellaneous out-of-band tasks, specifically 
tasks related to user transaction processing. Examples include signing account balance or record files,
reading block files, or outputting subsets of a signed state.

## Building
The SERVICES CLI is a jar file, and can be assembled using the following command:

```shell
# From the hedera services project root: 
./gradlew clean :services-cli:assemble
``` 

Following a successful build, the jar file should in the `build/libs` directory with a name similar to 
`services-cli-0.X.0.jar`. 

## Usage
The services CLI is organized into commands and subcommands, with each command having its own set of options.
The jar file can be run directly, but it's more convenient to use the [`services-cli.sh`](services-cli.sh) 
shell script. Inspired by the platform's [`pcli.sh`](../../platform-sdk/pcli.sh) script, `services-cli.sh` 
likewise provides a convenient wrapper for the jar file, particularly for computing the jar's needed classpath.

Using the shell script, the format of a command is as follows:
```shell
./services-cli.sh [command] [subcommand] [options]
```

All arguments for `services-cli.sh` are optional. Running `services.cli.sh` with no arguments will print the 
help message, listing all available commands and options. Of course, the output is much more interesting when
a command/subcommand is provided. A help message for all available subcommands can also be printed by running
`services-cli.sh [command]` with no subcommand. Note that executing a command without a subcommand will 
normally print that command's help message regardless of which options are present. 

Typically, the `[command]` portion is merely a phrase to group the appropriate subcommands. These parent command 
classes will be annotated with picocli's `@CommandLine.Command`, for example 
[`AccountBalanceCommand`](src/main/java/com/hedera/services/cli/sign/AccountBalanceCommand.java). The subcommands, 
on the other hand, are normally the real workhorses of the CLI, providing implementations for the actual tasks in
class files annotated with _both_ `@CommandLine.Command` _and_ `@SubcommandOf`. To continue the 
`AccountBalanceCommand` example, the account balance command has a `SignBalance` subcommand, which is implemented 
in the [`SignBalanceCommand`](src/main/java/com/hedera/services/cli/sign/SignBalanceCommand.java) class. 

The `[options]` portion is a list of flags and arguments that are specific to the subcommand. There are some
options applicable to the jar as a whole, but by and large the majority of arguments used by the script are
intended for each subcommand. The options for the script as a whole are discussed in the 
[next section](#script-options). Options for each subcommand are specified in the documentation for that 
subcommand, beginning in the ['Commands'](#commands) section.

Notably, the `services-cli.sh` script is meant to be used from the same directory as the jar file. From the
script itself:
```shell
#############################################################################################################
#
# TO USE: Place this script in the SAME DIRECTORY as `services-cli-*.jar` and INVOKE it from that directory
# (where `services-cli-*.jar` is shorthand for `services-cli-0.xx.y-SNAPSHOT.jar` or whatever the build
# system produces for you in `hedera-node/cli-clients/build/libs`.
#
#############################################################################################################
```

### Script Options

## Commands

The following commands are available in the services CLI:

* [ContractCommand](#ContractCommand)
* [AccountBalanceCommand](#AccountBalanceCommand)
* [RecordStreamCommand](#RecordStreamCommand)
* [DumpStateCommand](#DumpStateCommand)
* [SignedStateCommand](#SignedStateCommand)
* [SummarizeSignedStateFileCommand](#SummarizeSignedStateFileCommand)

Each is discussed in more detail below.

### ContractCommand

TODO

### AccountBalanceCommand

TODO

### RecordStreamCommand

TODO

### DumpStateCommand

TODO

### SignedStateCommand

TODO

### SummarizeSignedStateFileCommand

TODO

## Examples

TODO