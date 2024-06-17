# Platform Design Proposal Process

This document describes the process for creating and reviewing platform design proposals.

The status of all active design proposals can be viewed
at [Platform Design Proposals](https://github.com/orgs/hashgraph/projects/73/views/1) project board.

---

## Design Proposal Process Flow

![](designProposalFlow.drawio)

## Creating A Proposal

All proposals have an owner who is responsible for taking it through the process to completion. Proposals must be
presented in the form of a PR containing the proposal in a subdirectory of `platform-sdk/docs/proposals/`. Proposals may
contain diagrams and supporting materials. Diagrams must be in DrawIO (.drawio) format and all content
must be in an editable format (i.e. no images).

Any source code that was developed as part of the design process may be included in the subdirectory of the proposal
but not merged into the source code of the platform.

While in this draft phase the proposal may be updated and modified as needed. It is best practice to solicit review from
stakeholders to surface any potential change requests while the proposal is in the draft phase. All grammar and spelling
errors should be caught during this phase.

### Creating a Draft Proposal

1. Create an issue in the github repository for the creation of the proposal if it is for a project prioritized by
   SwirldsLabs. Other proposals do not require a ticket.
2. Create a new branch for the proposal.
3. Copy the template `template.md` into a subdirectory of `platform-sdk/docs/proposals/` with the name of the
   proposal.
4. Fill out the template with the details of the proposal, removing any irrelevant sections. Diagrams and other files
   may be included in the proposal directory and linked from the proposal.
5. Create a draft PR with the proposal documents.
6. Put the draft PR in the [Platform Design Proposals](https://github.com/orgs/hashgraph/projects/73/views/1) board in
   the `Draft` status.
7. Solicit feedback from stakeholders and other design collaborators.
8. Iterate on the proposal until it is ready for voting. All open comments should be resolved while in the draft stage.

### Submitting a Proposal for Voting

When the proposal is ready for voting, take the following steps:

1. Ensure all comments are resolved.
2. Mark the PR as ready for review
3. Update the status of the PR in
   the [Platform Design Proposals](https://github.com/orgs/hashgraph/projects/73/views/1) board to `Voting`

Requests for inmaterial changes can be addressed by pushing additional commits to the existing proposal
PR while in `Voting`. Examples of inmaterial changes are spelling or grammar errors, wording changes that do not modify
meaning or intent, and formatting changes. Examples of material changes include changes to behaviors or APIs, addition
or removal of diagrams, etc. If any material changes are needed, the PR must be closed and moved to the `Superseded`
status. A new proposal PR is prepared and voting is restarted. The `Superseded` PR must reference the new PR to
create an auditable history.

---

## Voting on A Proposal

Votes follow the [Apache Voting](https://www.apache.org/foundation/voting.html#expressing-votes-1-0-1-and-fractions)
scheme. Votes are cast in comments on the PR on a range between -1 to -0, +0 to +1 with the following semantics:

* -1: is a veto.
* -0 >= x > -1: is a vote generally against the proposal.
* +0 <= x < +1: is a vote generally in favor of the proposal.
* +1: is a vote in favor of the proposal.

The magnitude of the numeric value of the vote indicates the strength of the sentiment behind the vote.

+1 votes can also be expressed by approving the PR. -1 votes can be expressed by "Requesting Changes" on the PR and
must be accompanied by a comment explaining the reason for the veto.

---

## Acceptance of A Proposal

A proposal becomes `Accepted` when the following criteria have been fulfilled:

1. The proposal has been in the Voting state for at least 3 business days. Business days are defined as SwirldsLabs
   working days and excludes weekends and company holidays.
2. The proposal must have at least three +1 votes from platform code owners.
3. The proposal must not have any -1 votes from platform code owners or architects.

Once accepted, the proposal must be moved to the `Accepted` status in
the [Platform Design Proposals](https://github.com/orgs/hashgraph/projects/73/views/1) board and implementation of the
proposal may begin.

---

## Superseding A Proposal

A proposal is superseded by a new proposal when material changes are needed once voting has started. The old proposal
must link to the new proposal and indicate that it is being superseded. After the proposal has been updated, the PR's
status in the [Platform Design Proposals](https://github.com/orgs/hashgraph/projects/73/views/1) project should be
changed to `Superseded`.

---

## Withdrawing A Proposal

A proposal may be withdrawn after voting occurs. The proposal must be updated with a reason for the withdrawal. After
the proposal has been updated, the PR's status in
the [Platform Design Proposals](https://github.com/orgs/hashgraph/projects/73/views/1) project should be changed
to `Withdrawn`.

---

## Delivery of A Proposal

Once an accepted proposal has been completely implemented, tested, the code merged into `develop`, and the feature is
planned to be enabled for production, the proposal's content should be merged with the documentation of the platform
in `platform-sdk/docs` (or other relevant location such as `module-info.java`), as applicable, and removed
from `platform-sdk/docs/proposals`. Once the feature is live on mainnet, the status of the proposal PR in the proposal
project should be changed to `Delivered`.

## Design Proposal State Machine

![](designProposalStateMachine.drawio)