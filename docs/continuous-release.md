# Continuous Release Process

## Background

The Release Process within Hedera-Services will be changing beginning with Release 0.59. This will be the first step in
migrating to a fully automated release process with CITR.

### Historical Process

The release process within Hedera-Services has been using a release branching strategy that follows
a set development cycle

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

Each release takes approximately one calendar quarter to complete (from planning to retrospective)

### Relevant CITR Background

The continuous integration test and release process has been being executed within Hedera-Services since Q3-2023.
As part of this process there is a minimal acceptable test suite (MATS) that runs against every pull request. Further,
there is an extended test suite (XTS) that runs on each commit to the `default branch` in Hedera-Services (`main`).
After MATS and XTS complete successfully the commit is tagged with a **build** tag (`build-XXXXX`). This tag is the
crux of the new release process.

## CITR Release Process

The release process will be changing in the following areas beginning with `Release 0.59`

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

The release managers will run `zxf-trigger-semantic-release`, specifying the build number, in order to create the
release tagged commit (v0.59.0 or similar)

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
  - `zxf-trigger-semantic-release` - Generate a release tag given an XTS passing build id
  - `zxf-version-roll` - Roll the version specified in `version.txt` for the current development cycle on the default
    branch.
- Release management will need to choose a build candidate from a list of associated builds (recommendation will be the
  latest `build-xxxxx` tag)
