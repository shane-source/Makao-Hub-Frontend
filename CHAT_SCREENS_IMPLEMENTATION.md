# Protected chat screens implementation — 25 October 2026

## Task and scope

The seven supplied chat references were applied to the existing native Android Renter, Landlord and Sublessor workspaces. The implementation covers searchable inboxes, role-labelled protected conversations, incoming/outgoing message bubbles, server-confirmed contact masking, pagination, reconnect/closed states and guarded message composition.

The references also depict unread counts, names, listing titles, live presence, typing, delivery/read receipts, report/block/end actions, and TEST chat-unlock payment states. Those are not silently simulated because the current Android contracts do not provide them. The chat-unlock scope and price are an explicit product decision marked `TBC` in `AGENTS.md`.

## Assumptions and undecided items

- Existing role-scoped thread and message endpoints remain authoritative. A thread ID is never accepted as proof of ownership; the backend performs the ownership check.
- The existing WebSocket server echo remains the point at which an outgoing message may appear as saved.
- `RECOMMENDED`: add unread counters, participant display names, listing summaries, presence, typing, delivery/read receipts and safety actions only after typed backend contracts and their authorization/audit rules exist.
- `TBC`: whether paid chat access is per listing, application or conversation; what an owner sees before unlock; and the production price. The Android app therefore does not create the KSh 50 mock checkout shown in the reference.
- `TBC`: report categories, block semantics, conversation-close authority and audit/appeal rules. The reference controls are not rendered as non-functional buttons.

## Design

### Data model

No persistence or API model changed. The UI reuses `OwnerMessage`, `OwnerMessagePage`, `SharedMessagePage`, `OwnerThread` and `SharedThread`. The local `RenterThread` wrapper now retains the existing server status instead of discarding it, allowing the composer to close with the thread. Search is local and matches only role/status/reference values already returned by the server; it does not create an unbounded server query or search message contents.

### State transitions

1. Inbox loads role-owned threads through the existing repository.
2. An enabled row opens only the selected role-owned thread. A locked Renter roommate thread stays disabled.
3. Existing messages load through the role-specific ownership-checked endpoint.
4. An open thread connects to the existing authenticated WebSocket channel.
5. Sending is enabled only while connected, with non-blank text, and while no previous message awaits its server echo.
6. The message appears as `Saved` only after the server returns the persisted, contact-filtered message.
7. A disconnected client keeps saved messages visible but disables composition and exposes reconnect. A closed thread remains readable and cannot send.

### API contract

No endpoint was added or changed. The implementation continues to use the existing role-specific thread/message reads and authenticated WebSocket destinations. Contact masking remains a server responsibility; the client only renders `contactMasked` and the already-filtered `content` returned by the server.

### Failure modes

- Disconnected socket: saved history remains visible; sending is disabled; reconnect is offered.
- Pending server echo: editing and duplicate send are disabled.
- Closed thread: history remains visible; composer is removed.
- Locked Renter thread: row states that chat access is locked and that price/scope are `TBC`; no purchase is inferred.
- Malformed timestamp: the metadata omits time rather than crashing or displaying misleading data.
- Empty or unmatched inbox: a role-safe empty state is shown without sample people or listings.

## Files and connections

### `app/src/main/java/com/makaohub/android/ui/ProtectedChat.kt`

- `ProtectedInboxHeader` owns the shared title and bounded local search field. It prevents the three roles from drifting visually. Removing it would return each inbox to inconsistent copy and spacing.
- `ProtectedConversationRow` renders a role/reference/status summary and offers an action only when the caller says the row is available. Removing it would lose the common locked/closed presentation.
- `ProtectedConversation` is the shared protected-message surface. It receives data and callbacks from existing ViewModels and deliberately owns no network or entitlement logic. Removing it would duplicate security-sensitive presentation rules across all three workspaces.
- `protectedChatTime` safely extracts display time without allowing timestamp parsing failures to break chat rendering.
- `StatusNotice` is a private visual helper for masking and connection warnings.

Security-critical rendering, in order:

1. Only the `messages` supplied by the role ViewModel are sorted and rendered; the component does not fetch by a client-provided account or participant.
2. `ownUserId` changes bubble alignment only; it does not grant access or alter message content.
3. `contactMasked` changes warning metadata while `message.content` is the already-masked server value; the client never attempts to recover contact data.
4. `threadOpen`, `connected` and `awaitingEcho` jointly gate the composer and send action, preventing UI claims that a disconnected, closed or still-pending message was sent.
5. Draft input is capped at 2,000 characters, matching the existing send contract and bounding WebSocket frames.

### `app/src/main/java/com/makaohub/android/ui/RenterScreens.kt`

The inbox now searches role/reference metadata, distinguishes verified Landlord and Sublessor conversations, and keeps locked chat access disabled with `TBC` pricing/scope. The conversation route connects the current `RenterThread` to the shared screen and preserves the existing Renter ViewModel's entitlement gate and server-echo send behavior. Removing these changes would restore generic cards and lose visible locked/masking/reconnect states.

### `app/src/main/java/com/makaohub/android/data/RenterRepository.kt`

`RenterThread` now retains the server's existing thread status. This is presentation state, not a new authority decision. Removing it would make closed renter conversations look open until the server rejects a send.

### `app/src/main/java/com/makaohub/android/RenterViewModel.kt`

`loadThreads` maps the existing rental/roommate status into `RenterThread`. `openThread` refuses locally locked access and connects only an open thread; `reconnect` applies the same open-state guard. Backend entitlement, role and ownership checks remain mandatory. Removing these checks would restore a misleading active composer for locked or closed threads.

### `app/src/main/java/com/makaohub/android/ui/LandlordScreens.kt`

The inbox now presents verified Renter rental conversations using owned application references and server status. The selected `OwnerThread` feeds the shared screen, including closed-thread behavior. Removing these changes would restore duplicated message rendering and weaker visual parity with the supplied flow.

### `app/src/main/java/com/makaohub/android/ui/SublessorScreens.kt`

The inbox now presents verified Renter roommate conversations using owned interest references and server status. The selected `SharedThread` feeds the shared screen and retains the existing role-scoped socket connection. Removing these changes would restore the generic roommate cards and duplicated message rendering.

### `app/src/test/java/com/makaohub/android/ui/ProtectedChatTest.kt`

The tests prove valid ISO timestamps render predictably and malformed input fails closed to an empty time label. Removing them would leave the new timestamp boundary without regression coverage.

### `design-qa.md`

The top section records the exact source references, intentional contract differences and the missing native render evidence. Removing it would make a future reviewer unable to distinguish functional completion from visual acceptance.

## Verification

- `./gradlew testDebugUnitTest`: passed.
- The main and unit-test Kotlin compilers used their non-daemon fallback because the sandbox cannot write Kotlin daemon marker files under `~/.local`; compilation and tests still completed successfully.
- A native screenshot comparison is blocked: `adb devices` shows no attached device and no configured AVD is present.

## Remaining risks

- Counterparty names, listing context and unread state are absent from the thread summaries, so the production inbox is less informative than the reference.
- REST/live presence exists outside the current Android presentation contract, but a one-time status snapshot would become stale; it was not mislabeled as live online state.
- Safety controls remain unavailable until backend authorization, retention and audit contracts exist.
- The exact chat-unlock commercial model is unresolved, so locked threads have no checkout action.
- Visual wrapping, keyboard insets, TalkBack order, large text and contrast require an on-device pass.

## What to test next

- On-device inbox search, pagination and Back behavior for all three roles.
- Masked phone, email and social-handle attempts from both participants.
- Socket disconnect/reconnect, duplicate-send prevention and server-echo ordering.
- Closed and unauthorized thread access, including direct navigation attempts.
- TalkBack labels, focus order, 200% font scale and keyboard composer behavior.

## What to confirm externally

- Kenyan lawyer/privacy lead: reporting retention, blocking/appeal behavior and moderation access to flagged conversations.
- Safaricom/payment provider: nothing in this UI change; confirm the eventual chat-unlock payment contract only after scope and price are decided.
- Product owner: chat-unlock unit/scope, pre-unlock owner visibility, unread semantics, presence privacy, safety action rules and whether conversation closing is unilateral.

## Suggested next task

Define and implement the backend chat metadata and safety contract—participant display summary, unread cursor, presence privacy, report/block/end transitions and audit events—before adding the remaining reference states.
