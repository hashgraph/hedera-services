# Services CLI

## Purpose

The Services CLI is a developer command-line tool for performing miscellaneous out-of-band tasks, specifically
tasks related to user transaction processing. Examples include signing account balance or record files,
reading block files, or outputting subsets of a signed state.

This tool is intended to be used by developers and maintainers of the Hedera Services codebase. We aim to
follow the same patterns as the platform's [PCLI tool](../../platform-sdk/swirlds-cli/src/main/java/com/swirlds/cli/PlatformCli.java)
notably using the `picocli` library for command-line parsing, and structuring the CLI as a series of
logically-grouped commands and subcommands.

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
The jar file can be run directly, but it's more convenient to use the [`services-cli.sh`](services-cli.sh)shell script.
Inspired by the platform's [`pcli.sh`](../../platform-sdk/pcli.sh) script, `services-cli.sh`likewise provides a convenient
wrapper for the jar file, particularly for computing the jar's needed classpath. (Note: these commands should 
also work correctly with the PCLI tool or its scripts, e.g. `pcli.sh`)

Using the shell script, the format of a command is as follows:

```shell
./services-cli.sh [PCLI options] [command] [subcommand] [sub/command options]
```

All arguments for `services-cli.sh` are optional. Running `services.cli.sh` with no arguments will print the
help message, listing all available commands and options. Of course, the output is much more interesting when
a command/subcommand is provided. A help message for all available subcommands can also be printed by running
`services-cli.sh [command]` with no subcommand. Note that executing a command without a subcommand will
normally print that command's help message regardless of which options are present.

Typically, the `[command]` portion is merely a phrase to group the appropriate subcommands. These parent command
classes will be annotated with picocli's `@CommandLine.Command`, for example[`AccountBalanceCommand`](src/main/java/com/hedera/services/cli/sign/AccountBalanceCommand.java).
The subcommands, on the other hand, are normally the real workhorses of the CLI, providing implementations for the
actual tasks in class files annotated with _both_ `@CommandLine.Command` _and_ `@SubcommandOf`. To continue the
`AccountBalanceCommand` example, the account balance command has a `SignBalance` subcommand, which is implemented
in the [`SignBalanceCommand`](src/main/java/com/hedera/services/cli/sign/SignBalanceCommand.java) class.

The `[sub/command options]` portion is a list of flags and arguments that are specific to the subcommand. There 
are some options applicable to the jar as a whole, but by and large the majority of arguments used by the script are
intended for each subcommand. The options for the script as a whole are discussed in the[next section](#script-options).
Options for each subcommand are specified in the documentation for that subcommand, beginning in the
['Commands'](#commands) section. 

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

Since the services CLI jar inherits behavior from the platform's PCLI tool, it also supports the same PCLI
options (see a complete list in the source code [here](../../platform-sdk/swirlds-cli/src/main/java/com/swirlds/cli/PlatformCli.java)).
It's critical to remember that, while perhaps counter-intuitive, _*option placement is not arbitrary*_ with
these cli tools. In particular, all PCLI options must come _before_ the command, while all Services CLI options
or command-specific options must come _after_ the command.

#### The `--load` Option

The most notable PCLI option is `--load`. The `--load` option is used to specify classpath arguments that will
load any jars necessary to run commands. By default, *both PCLI and Services CLI do _not_ load _any_ jars on
the execution classpath, so this option is essentially required.* Both the PCLI and Services CLI scripts accept
directory paths as arguments, and can be specified multiple times. While the easiest way to load any project-
related jars would be to specify the project root directory, unfortunately this doesn't work (reason unknown).
Instead, load the platform jars and the services jars separately, and in said order:

```shell
./services-cli.sh --load "<project-root>/platform-sdk" --load "<project-root>/hedera-node>" <rest of cmd...>
```

Experience has also shown that the order of the `load` options is important. For example, if we load all services
jars before the platform jars, it's possible to get a `NoSuchMethodError`:

```shell
# Presumably because platform jars are dependencies of services code, this ordering of the `--load`
# options can produce errors like `NoSuchMethodError` or `ClassNotFoundException`:
./services-cli.sh --load "<project-root>/hedera-node" --load "<project-root>/platform-sdk" <...>
```

#### The `--jvm` Option

The `--jvm` option can supposedly be used to specify JVM options for the jar. If you figure out how, please
document it here for the rest of us.

#### A Note on Multi-line Commands

If it isn't obvious by now, the PCLI and Services CLI scripts are somewhat temperamental. Sometimes there are
issues with multi-line commands, i.e. commands that utilize '\' to span multiple lines. If you encounter issues
with a multi-line command, try removing all '\' characters and putting the entire command on a single line.
We will use multi-line commands throughout this document, but be aware that they may not work as expected.

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

The account balance command also only has one subcommand, providing a way to sign account balance files.
***Note***: As of this writing, account balance files are either deprecated or soon-to-be-deprecated.

#### Sign

The `sign` subcommand is used to sign an account balance file with a cryptographic key provided by the user.
Along with the key, an input account balance file is given, which the tool then uses to generate a signature
that verifies the authenticity of the account balance file. This signature (or signatures) will be output to
a separate file by the tool.

The syntax of the sign subcommand is as follows:

```shell
./services-cli.sh [PCLI options] \
account-balance sign \
[sub/command options] \           
<keyFilePath> <keyFilePassword> <keyAlias>
```

There are three required arguments for the sign subcommand:
* `keyFilePath` - the path to the key file used to sign the account balance file. This file is expected to
be a pfx private key.
* `keyFilePassword` - the password needed to use the key file
* `keyAlias` - the alias of the key in the key file

There are also some optional arguments:
* `-p, --paths-to-sign` - a list of paths/files to sign. This option can contain single files as well as
directories. If this option is not provided, the tool defaults to searching the current working directory.
_Note:_ this option does _not_ recursively search for files to sign. If a directory is provided, only the
files in that directory will be signed.
* `-d, --destination-directory` - the directory where the signature files will be placed. If this option is
not provided, the tool will output the signature(s) to the corresponding source directories of the input
paths (i.e. the paths from the `-p` option) alongside the inputs. If this option is provided, all signature
files will be placed in the specified directory; _all source files will also be copied to the destination
directory._ Therefore, in either case, the source file(s) will be present in the destination directory.

Finally, note that this command will skip any account balance files that already have a corresponding
`<balance-file-name>_sig` file in an input argument's path.

#### Example Usage

For all examples, assume the following directory structure:

```shell
.
# ├── keys
# │   └── key1.pfx
# ├── accountBalance1.pb
# ├── dir
# │   └── accountBalance2.pb
# │   ├── nestedDir
# │       └── accountBalance3.pb
# │       └── accountBalance4.pb
```

For our hypothetical pfx key, we will assume a password of `dontUseThisPassword` and an alias of `myKey`.

##### Example 1: Signing a single file

Here is an example of signing a single account balance file:

```shell
./services-cli.sh --load "<project-root>/platform-sdk" \
--load "<project-root>/hedera-node" \
account-balance sign \
-p accountBalance1.pb \
keys/key1.pfx dontUseThisPassword myKey
```

The result of this command will be a new signature file is created, `accountBalance1.pb_sig`, in the current
working directory (i.e. `./accountBalance1.pb_sig`).

##### Example 2: Signing multiple files

Let's say we want to sign two files – `accountBalance2.pb` and `accountBalance3.pb` – with a single command.
We would execute the following:

```shell
./services-cli.sh --load "<project-root>/platform-sdk" \
--load "<project-root>/hedera-node" \
account-balance sign \
-p dir/accountBalance2.pb \
-p dir/nestedDir/accountBalance3.pb \
keys/key1.pfx dontUseThisPassword myKey
```

This command creates two new signature files, with each signature file created in the directory of its
corresponding input file. Therefore, running this command should create signature files `dir/accountBalance2.pb_sig`
– since `accountBalance.pb` is in the `dir` directory – and `dir/nestedDir/accountBalance3.pb_sig`, since
`accountBalance3.pb` is in the `dir/nestedDir` directory.

##### Example 3: Signing a directory and a file

The `dir/nestedDir` directory contains two account balance files, `accountBalance3.pb` and `accountBalance4.pb`. We
can sign these files with a single command, along with the `accountBalance1.pb` file, by executing the following:

```shell
./services-cli.sh --load "<project-root>/platform-sdk" \
--load "<project-root>/hedera-node" \
account-balance sign \
-p dir/nestedDir \
-p accountBalance1.pb \
keys/key1.pfx dontUseThisPassword myKey
```

The result of this command will be three new signature files: `dir/nestedDir/accountBalance3.pb_sig`,
`dir/nestedDir/accountBalance4.pb_sig`, and `./accountBalance1.pb_sig`.

##### Example 4: Failure to sign a file that already has a signature

If our working directory (`./`) already contains a file `accountBalance1.pb_sig`, trying to sign the `accountBalance1.pb`
file will result in an informational message, and no new signature file is generated:

```shell
./services-cli.sh --load "<project-root>/platform-sdk" \
--load "<project-root>/hedera-node" \
account-balance sign \
-p accountBalance1.pb \
keys/key1.pfx dontUseThisPassword myKey
```

This command produces output similar to the following:

```shell
SignCommand - Signature file accountBalance1.pb_sig already exists. Skipping file accountBalance1.pb
```

Checking the working directory, we would see that the `accountBalance1.pb_sig` file is unchanged.

##### Example 5: Designating a destination directory

Collecting signature files from multiple paths is inconvenient. To simplify, we can use the `--d option` to
place all output signature files in a single directory. To sign all account balance files in our example, we
would execute the following:

```shell
./services-cli.sh --load "<project-root>/platform-sdk" \
--load "<project-root>/hedera-node" \
account-balance sign \
-p accountBalance1.pb \
-p dir \
-p dir/nestedDir \
-d "signatures" \
keys/key1.pfx dontUseThisPassword myKey
```

This command will create four new signature files, all located in the new `./signatures` directory. The source
files are also copied to the destination directory; therefore, the `signatures` directory will also contain a
copy of each account balance file. The new `signatures` directory would therefore contain a total of 8 new files:

```shell
# (Omitting the pre-existing files for brevity):
# .
# ├── signatures
# │   └── accountBalance1.pb
# │   └── accountBalance1.pb_sig
# │   └── accountBalance2.pb
# │   └── accountBalance2.pb_sig
# │   └── accountBalance3.pb
# │   └── accountBalance3.pb_sig
# │   └── accountBalance4.pb
# │   └── accountBalance4.pb_sig
```

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
