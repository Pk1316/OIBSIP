# Implementation notes — role-based approval workflow

This documents every file created or changed while implementing the 5 tasks from
`TASKLIST_PROMPT.md`, using the answers you gave to its open questions:
role strings are lowercase-hyphenated (`mission-reviewer`, `mission-approver`) except
the pre-existing `MissionPlanner` check, which was left untouched; reviewer gets
"Send for Approval" + "Reject"; approver gets "Approve" + "Reject"; POST endpoint is
`PostRejected`; the per-note key is `notes`; every one of the 8 pages is always
included in the notes array (empty string if nothing was typed); and the
approve/reject "who sees what next" workflow routing is explicitly **not** built yet
(you called that a future improvement).

**Not yet done:** the build/lint check was interrupted before I could run it, so
none of this has been verified to compile yet.

---

## New files

### `src/hooks/useIsMissionReviewer.js`
```js
export function useIsMissionReviewer() {
  const user = JSON.parse(localStorage.getItem("user") || "{}");
  return user?.roles?.includes("mission-reviewer");
}
```
**Why:** Same shape as the `useIsMissionPlanner` hook from the earlier centralization
work — one hook per role, each doing its own `localStorage` read. Keeps every role
check following the same pattern instead of inventing a different mechanism for the
two new roles.

### `src/hooks/useIsMissionApprover.js`
```js
export function useIsMissionApprover() {
  const user = JSON.parse(localStorage.getItem("user") || "{}");
  return user?.roles?.includes("mission-approver");
}
```
**Why:** Same as above, for the approver role.

### `src/components/PageNotesButton.jsx`
```jsx
import { useEffect, useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { useStore } from '@/store/store';

export default function PageNotesButton({ pageName }) {
  const [isOpen, setIsOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const savedNote = useStore((state) => state.pageNotes[pageName] ?? '');
  const setPageNote = useStore((state) => state.setPageNote);

  useEffect(() => {
    if (isOpen) setDraft(savedNote);
  }, [isOpen, savedNote]);

  const handleSave = () => {
    setPageNote(pageName, draft);
    setIsOpen(false);
  };

  return (
    <>
      <button
        onClick={() => setIsOpen(true)}
        title="Notes"
        className="fixed bottom-6 right-6 z-40 flex h-12 w-12 items-center justify-center rounded-full bg-[#5F1562] text-white shadow-lg transition-colors hover:bg-[#4a1050]"
      >
        {/* pencil/note icon */}
      </button>

      <Dialog open={isOpen} onOpenChange={setIsOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>Notes</DialogTitle></DialogHeader>
          <textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            rows={6}
            placeholder="Enter your notes for this page..."
            className="w-full rounded-[4px] border border-gray-300 p-2 text-sm focus:border-[#5F1562] focus:outline-none"
          />
          <DialogFooter>
            <button onClick={handleSave} className="rounded-[4px] bg-[#5F1562] px-5 py-2 text-sm font-medium uppercase text-white transition-colors hover:bg-[#4a1050]">
              Save
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
```
**Why one shared component instead of 8 copies:** every page needs the identical
button + dialog, differing only by which page's note it reads/writes. A single
`pageName`-driven component means each page adds one line instead of duplicating
~50 lines of markup 8 times — directly serves the "minimal changes" ground rule and
makes future edits (styling, behavior) a one-file change instead of an 8-file hunt.

**Why it reads/writes the zustand store instead of local `useState`:** the Reject
modal (in `DataModifierHeader.jsx`) has to assemble notes from potentially all 8
pages when the reviewer clicks Reject — but the reviewer may not have visited every
page in the current session. Component-local state would be destroyed the moment
React unmounts that page, so the only place the data can still exist when Reject
fires is a store that persists across route changes. It uses the existing
`@/components/ui/dialog` (Radix) primitives already used elsewhere in the codebase
(e.g. `MissionSpecificParams.jsx`'s modals) rather than hand-rolling a new overlay,
for consistency with the shared UI kit.

---

## Modified files

### `src/store/MapDataSlice.jsx`
Three small additions:

1. New state field, next to `hasUnsavedChanges`:
   ```js
   // ── Per-page reviewer/approver notes ────────────────────────────────────
   // Keyed by pageName (route slug). Populated by the Notes button on each of
   // the 8 approval-workflow pages; read back when building the Reject payload
   // so notes survive navigating between pages before Reject is clicked.
   pageNotes: {},
   ```
2. New setter, next to `setHasUnsavedChanges`:
   ```js
   setPageNote: (pageName, text) =>
     set((s) => ({ pageNotes: { ...s.pageNotes, [pageName]: text } })),
   ```
3. Added `pageNotes: {}` to the `resetPfmData` reset object (the function called
   when a new PFM file session starts), so notes from one PFM record don't leak
   into the next one's Reject payload.

**Why here and not local component state:** explained above under
`PageNotesButton.jsx`. This is the single source of truth both the button
(writer) and the header's Reject handler (reader) share.

### `src/store/store.jsx`
Added one line to the `partialize` allowlist:
```js
pageNotes: state.pageNotes,
```
**Why:** the store uses `zustand/persist` with an explicit allowlist of which
fields survive a full page reload (`pfmDraftData`, `pfmTreeData`, etc. are already
in that list). Without adding `pageNotes` here, a reviewer's typed-but-unsaved
workflow would silently lose all notes on an accidental browser refresh.

### `src/services/pmdaService.js`
Added two functions at the end of the file, following the exact
try/catch/console.error shape every other function in this file already uses:

```js
export const postRejected = async (payload) => {
  try {
    const url = `${API_BASE_URL}/PostRejected`;
    const response = await axios.post(url, payload, {
      headers: { 'Content-Type': 'application/json' }
    });
    return response.data;
  } catch (error) {
    console.error('Error posting rejection:', error);
    if (error.response) { /* ... */ } else if (error.request) { /* ... */ }
    throw error;
  }
};

export const approvePfm = async (pfmgid) => {
  try {
    const url = `${API_BASE_URL}/ApprovePFM`; // Adjust endpoint name as needed
    const response = await axios.get(url, { params: { PFMGID: pfmgid } });
    return response.data;
  } catch (error) {
    console.error('Error approving PFM:', error);
    if (error.response) { /* ... */ } else if (error.request) { /* ... */ }
    throw error;
  }
};
```
**Why `PostRejected` casing:** you confirmed this as the POST endpoint name; kept
PascalCase to match every other endpoint in this file (`SaveBlankingChannels`,
`SaveSearchRegime`, `SavePFM`, etc.).

**⚠️ Still unconfirmed:** you never gave me the real GET endpoint path for Approve
— `/ApprovePFM` is a placeholder I invented to match the naming convention. This
needs a one-line change once you have the real path.

### `src/components/layout/Headers/DataModifierHeader.jsx`
This is the file with the most changes, since it's the single shared header
rendered on every page (mounted once in `Layout.jsx`) that already held the
Save/Generate Binary buttons — so the role-based swap belongs here, not
duplicated into each page.

**1. New imports** (top of file):
```js
import { generateBinaryFile, saveSearchRegime, savePfmFile, postRejected, approvePfm } from '@/services/pmdaService';
import { useStore } from '@/store/store';
import { useIsMissionPlanner } from '@/hooks/useIsMissionPlanner';
import { useIsMissionReviewer } from '@/hooks/useIsMissionReviewer';
import { useIsMissionApprover } from '@/hooks/useIsMissionApprover';

const NOTES_PAGE_NAMES = [
    'pfm-file-detail-updated', 'analaysis-ambiguity', 'search-regime', 'cmds-params',
    'onboard-tx', 'blanking-channels', 'mission-specific-params', 'programmable-thresold',
];
```
**Why `NOTES_PAGE_NAMES` exists:** the Reject payload always needs one entry per
page (per your answer to open question 6 — empty string for pages with no note),
so this constant is the canonical, ordered list the notes array is built from. It
must exactly match the `pageName` prop values passed to `<PageNotesButton>` on each
page (see below) — that's the only thing linking the writer (each page) to the
reader (this file).

**2. New component state** (alongside existing `resultModal` etc. state):
```js
const [isRejectModalOpen, setIsRejectModalOpen] = useState(false);
const [rejectComment,     setRejectComment]     = useState('');
const [isRejecting,       setIsRejecting]       = useState(false);
const [isApproving,       setIsApproving]       = useState(false);

const isMissionPlanner  = useIsMissionPlanner();
const isMissionReviewer = useIsMissionReviewer();
const isMissionApprover = useIsMissionApprover();
const pageNotes = useStore((state) => state.pageNotes);
```

**3. New handlers**, added right after the existing `handleGenerateBinaryFile`:

- `handleSendForApproval` — shows the existing `resultModal` success dialog with a
  "Sent for approval" message. **Deliberately calls no API.** Per your answer to
  open question 7 ("don't include any functionality for now... may be these are
  future improvements"), I did not build the cross-role visibility/routing logic
  that would make an item show up for the approver after this click — only the
  button and a visual acknowledgment exist right now.

- `handleApprove` — calls `approvePfm(pfmModifierId)` and shows the result via the
  existing `resultModal` pattern (success or failure).
  **Assumption baked in here:** `PFMGID` is sourced from the store's
  `pfmModifierId`, since that's the only record identifier that exists anywhere in
  this codebase (it's the same value already sent with every Save/Generate Binary
  call). This was flagged as unconfirmed in `TASKLIST_PROMPT.md` and your answer
  ("3. PFMGID") didn't explicitly resolve whether they're the same value — I went
  with `pfmModifierId` as the only reasonable candidate. Worth double-checking.

- `handleConfirmReject` — guards on `rejectComment.trim()`, builds the notes array
  from `NOTES_PAGE_NAMES` + the store's `pageNotes` (defaulting missing pages to
  `""`), then calls `postRejected`:
  ```js
  const notes = NOTES_PAGE_NAMES.map((pageName) => ({
      pageName,
      notes: pageNotes[pageName] ?? '',
  }));
  const result = await postRejected({
      PFMGID: pfmModifierId,
      comment: rejectComment,
      notes,
  });
  ```
  This matches your confirmed payload shape exactly: `PFMGID`, `comment`, and a
  `notes` array of `{ pageName, notes }` objects (you confirmed `notes`, plural, as
  the per-entry key — not `note`).

**4. Button JSX restructured.** The block that used to unconditionally render
Generate Binary + Save now branches on role:

```jsx
{(isMissionReviewer || isMissionApprover) ? (
    <>
        {isMissionReviewer && <SendForApprovalButton onClick={handleSendForApproval} />}
        {isMissionApprover && <ApproveButton onClick={handleApprove} loading={isApproving} />}
        <RejectButton onClick={() => setIsRejectModalOpen(true)} />
    </>
) : (
    <>
        <GenerateBinaryButton />  {/* unchanged */}
        <SaveButton />            {/* unchanged */}
        {isMissionPlanner && <SendForApprovalButton onClick={handleSendForApproval} />}
    </>
)}
```
(Actual code uses the existing inline `<div>` button markup, not extracted
components — I kept every existing Generate Binary/Save JSX byte-for-byte, just
wrapped in the `else` branch, so nothing about their existing look, disabled
states, or click handlers changed for users who aren't reviewer/approver.)

**Why this shape:**
- MissionReviewer and MissionApprover **never** see Save/Generate Binary — per
  task 2, those buttons are replaced outright for these two roles, not just
  hidden-and-disabled.
- MissionReviewer sees "Send for Approval" + "Reject" (your answer to open
  question 2).
- MissionApprover sees "Approve" + "Reject" (same answer).
- Everyone else (including MissionPlanner) keeps the original Save/Generate
  Binary exactly as before; MissionPlanner additionally gets "Send for Approval"
  to the right of Save (task 1).
- Reject uses the identical button/handler regardless of which of the two roles
  clicks it — nothing in your answers described a different Reject behavior per
  role, so one code path serves both.

**5. New Reject modal JSX**, added right after the existing `resultModal` block
(same file, same visual language — fixed dark overlay + centered white card, like
the result modal already there):
```jsx
{isRejectModalOpen && (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" ...>
        <div role="dialog" ...>
            <h3>Reject</h3>
            <textarea value={rejectComment} onChange={...} placeholder="Enter a comment..." />
            <div className="mt-6 flex justify-end gap-3">
                <button onClick={cancel}>Cancel</button>
                <button
                    onClick={handleConfirmReject}
                    disabled={!rejectComment.trim() || isRejecting}
                >
                    {isRejecting ? 'Submitting...' : 'OK'}
                </button>
            </div>
        </div>
    </div>
)}
```
**Why `disabled={!rejectComment.trim() || isRejecting}`:** matches your spec
exactly — OK is disabled until the comment has real (non-whitespace-only) content,
and is also disabled while the request is in flight to prevent double-submits.

---

## The 8 page files

Each got exactly two lines added: one import, and one
`<PageNotesButton pageName="..." />` placed as the last child right before that
page's outermost closing tag (safe because the button is `position: fixed`, so its
position in the JSX tree doesn't affect where it renders on screen). No other line
in any of these 8 files was touched.

| File | `pageName` value used | Import path used |
|---|---|---|
| `pfmFileDetailTanStackUpdated.jsx` | `pfm-file-detail-updated` | `@/components/PageNotesButton` |
| `AnalaysisAmbiguity.jsx` | `analaysis-ambiguity` | `@/components/PageNotesButton` |
| `SearchRegime.jsx` | `search-regime` | `@/components/PageNotesButton` |
| `CMDAParams.jsx` | `cmds-params` | `@/components/PageNotesButton` |
| `OnboardTxInterference.jsx` | `onboard-tx` | `@/components/PageNotesButton` |
| `BlankingChannels.jsx` | `blanking-channels` | `@/components/PageNotesButton` |
| `MissionSpecificParams.jsx` | `mission-specific-params` | `../components/PageNotesButton` (this file uses relative imports elsewhere) |
| `ProgrammbleThreashold.jsx` | `programmable-thresold` | `@/components/PageNotesButton` |

The `pageName` values are exactly the route slugs from your original URL list —
and exactly match `NOTES_PAGE_NAMES` in `DataModifierHeader.jsx`, which is what
lets the Reject handler correctly attribute each saved note back to its page.

---

## Known gaps / things to verify next
1. **Build not yet run** — I was interrupted before verifying these changes
   actually compile. Should be done before you rely on any of this.
2. **`ApprovePFM` endpoint path is a placeholder** — needs the real path.
3. **`PFMGID` = `pfmModifierId` is an assumption**, not an explicit confirmation.
4. **"Send for Approval" has no backend call** — by design, per your instruction
   to skip the routing/visibility logic for now. If you did want *some* API call
   here (just not the full routing logic), let me know and I'll wire it in.
