# Continuous Release Process

## Background

The Release Process within Hedera-Services will be changing beginning with Release 0.59. This will be the first step in
migrating to a fully automated release process with CITR.

### Historical Process

The release process within Hedera-Services has been using a release branching strategy that follows
a set development cycle:

|                Stage                | Duration |
|-------------------------------------|----------|
| Planning                            | 12d      |
| Development                         | 33d      |
| **milestone** Create release branch | 0d       |
| Bugs & Fix                          | 12d      |
| **milestone** Deploy to preview net | 12d      |
| **milestone** Create alpha tag      | 0d       |
| **milestone** Deploy to test net    | 1d       |
| Perform migration testing           | 22d      |
| **milestone** Deploy to main net    | 7d       |
| Release Retrospective               | 1d       |

Each release takes approximately one calendar quarter to complete (from planning to retrospective).

### Relevant CITR Background

The continuous integration test and release (CITR) process has been being executed within Hedera-Services since Q3-2024.
As part of this process there is a minimal acceptable test suite (MATS) that runs against every pull request. Further,
there is an extended test suite (XTS) that runs on each commit to the `default branch` in Hedera-Services (`main`).
After MATS and XTS complete successfully the commit is tagged with a **build** tag (`build-XXXXX`). This tag is the
crux of the new release process.

## CITR Release Process

The release process will be changing in the following areas beginning with `Release 0.59`:

|                          Stage                          | Duration |
|---------------------------------------------------------|----------|
| Planning                                                | 12d      |
| Development                                             | 33d      |
| [NEW] **milestone** Select candidate commit for release | 0d       |
| [NEW] Run the `zxf-trigger-semantic-release` workflow   | 0d       |
| [DEL] ~~**milestone** Create release branch~~           | ~~0d~~   |
| [DEL] ~~Bugs & Fix~~                                    | ~~12d~~  |
| **milestone** Deploy to preview net                     | 12d      |
| [DEL] ~~**milestone** Create alpha tag~~                | ~~0d~~   |
| **milestone** Deploy to test net                        | 1d       |
| Perform migration testing                               | 22d      |
| **milestone** Deploy to main net                        | 7d       |
| Release Retrospective                                   | 1d       |

### Selecting a build candidate

The XTS workflow will tag a commit with a `build-xxxxx` tag upon successful completion.

The release managers will pull a release candidate from this set of tags.

The release managers will run the workflow `zxf-trigger-semantic-release`, specifying the build number, in order to
create the release tagged commit (v0.59.0 or similar)

**Note:** The build number is actual number described in the build tag. The tag `build-00025` has a build number `25`.

This release tag (v0.59.0 or similar) is the tag that will be deployed to preview-net, test-net, and main-net.

If bugs are discovered in the build during release testing we can run `zxf-trigger-semantic-release` on a new build
candidate, as determined by the release managers, and select `create patch` when running the workflow. This will
generate a patch version release with the new build (v0.59.1 or similar) which can then run through the various
test/release networks.

## Impacts for Developers

Beginning with release 0.59:

- The following items will be restricted:
  - Creating release branches in Hedera-Services (like release/0.59)
  - Creating versioned tags like (v0.59.*)
- The following workflows will be added:
  - `zxf-trigger-semantic-release` - Generate a release tag given an XTS passing build id.
  - `zxf-version-roll` - Roll the version specified in `version.txt` for the current development cycle on the default
    branch.
- Release managers will need to choose a build candidate from a list of associated builds (recommendation will be the
  latest `build-xxxxx` tag).

## Creating a patch bump

It is possible to create a patch bump after a build candidate has been released via the `zxf-trigger-semantic-release`
workflow. The release managers will need to select a new build candidate from the list of available builds. The selected
commit would need to match the [semantic release](https://semantic-release.gitbook.io/semantic-release) requirements for a
patch bump.

|                                                 Commit message                                                 |                                                Release Type                                                |
|----------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| fix(pencil): stop graphite breaking when too much pressure applied                                             | Patch/Fix Release                                                                                          |
| feat(pencil): add 'graphiteWidth' option                                                                       | Minor/Feature Release                                                                                      |
| perf(pencil): remove graphiteWidth option<br/><br/>BREAKING CHANGE: The graphiteWidth option has been removed. | Major/Breaking Release<br/><br/>(Note that the BREAKING CHANGE: token must be in the footer of the commit) |
[Sourced from [semantic release gitbook](https://semantic-release.gitbook.io/semantic-release#commit-message-format)]

The CI workflows use [conventional commits](https://www.conventionalcommits.org/en/v1.0.0/) and semantic release to
determine the version to roll to when the `zxf-trigger-semantic-release` workflow is called. What this means is that
the commits on `main` between build-XXXXX (the selected build candidate) and build-YYYYY (the new build) must be `fix:`
commits so that a new patch bump version would be triggered.

(Note: **If any feature or breaking commits have been included in that timeframe, there will be a new minor/major version.**)

There are also commit types that will not affect the version at all. These commit categories are:

- `chore:`
- `docs:`
- `ci:`
