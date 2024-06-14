# Platform Design Proposal Process

This document describes the process for creating and reviewing platform design proposals. It is a modified version of
the [ADR process](https://docs.aws.amazon.com/prescriptive-guidance/latest/architectural-decision-records/adr-process.html).

The status of all active design proposals can be viewed
at [Platform Design Proposals](https://github.com/orgs/hashgraph/projects/73/views/1) project board.

---

## Design Proposal Flow

![](designProposalFlow.svg)

## Creating A Proposal

All proposals have an owner who is responsible for taking it through the process to completion. Proposals must be
presented in the form of a PR containing the proposal in a subdirectory of `platform-sdk/docs/proposals/`. Proposals may
contain diagrams and supporting materials. Diagrams must be in SVG format and all content must be in an editable
format (i.e. no images).

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
8. Iterate on the proposal until it is ready for voting. All open comments should be resolved.

### Submitting a Proposal for Voting

When the proposal is ready for voting, take the following steps:

1. Ensure all comments are resolved.
9. Mark the as ready for review
10. Update the status of the PR in
    the [Platform Design Proposals](https://github.com/orgs/hashgraph/projects/73/views/1) board to `Voting`

Requests for inmaterial changes to the proposal can be addressed by pushing additional commits to the existing proposal
PR while in `Voting`. Examples of inmaterial changes are spelling or grammar errors, wording changes that do not modify
meaning or intent, and formatting changes. Examples of material changes include changes to behaviors or APIs, addition
or removal of content, etc. If any material changes are needed, the PR must be closed, and moved to the `Superceded`
status. A new proposal PR is prepared and voting is restarted. The `Superceded` PR must reference the new PR.

---

## Voting on A Proposal

Proposal Acceptance Critera:

1. The proposal has been in the Voting state for at least 3 business days. Business days are defined as SwirldsLabs
   working days and excludes weekends and company holidays.
2. The proposal must have at least three +1 votes from platform code owners.
3. The proposal must not have any -1 votes from platform code owners or architects.

Votes follow the [Apache Voting](https://www.apache.org/foundation/voting.html#expressing-votes-1-0-1-and-fractions)
scheme. Votes are cast in comments on the PR on a range between -1 to -0, +0 to +1 with the following semantics:

* -1: is a veto.
* -0 >= x > -1: is a non-blocking vote against the proposal.
* +0 <= x < +1: is a vote in favor of the proposal but with some level of uncertainty.
* +1: is a vote in favor of the proposal.

The magnitude of the numeric value of the vote indicates the strength of the sentiment behind the vote.

+1 votes can also be expressed by approving the PR. -1 votes can be expressed by "Requesting Changes" on the PR and
should be accompanied by a comment explaining the reason for the veto.

---

## Acceptance of A Proposal

After 3 business days of voting, if the proposal has 3 or more +1 votes in favor with no vetos and the proposal has not
been withdrawn or superseded, then the proposal becomes accepted. Once accepted, implementation may begin.

---

## Superseding A Proposal

A proposal may be superseded by a new proposal. The old proposal must link to the new proposal and indicate that it is
being superseded. After the proposal has been updated, the PR's status in the proposal project should be changed
to `Superseded`.

---

## Withdrawing A Proposal

A proposal may be withdrawn. The proposal must be updated with a reason for the withdrawal. After the proposal has been
updated, the PR's status in the proposal project should be changed to `Withdrawn`.

---

## Delivery of A Proposal

Once an accepted proposal has been completely implemented and the code merged into develop, the proposal's content
should be used to update the documentation of the platform. Once the platform documentation is updated, the status of
the proposal PR in the proposal project should be changed to `Delivered`.