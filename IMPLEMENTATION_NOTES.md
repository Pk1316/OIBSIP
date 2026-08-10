
Roles are read from `localStorage` and use lowercase-hyphenated strings: `"mission-planner"`, `"mission-reviewer"`, `"mission-approver"`. For local development, `src/App.jsx` seeds this directly:

```js
// src/App.jsx
localStorage.setItem("user", JSON.stringify({
  userId: "32",
  username: "superuser@udga.com",
  platform: "LCA_MK1",
  isFirstLogin: "False",
  roles: ["mission-approver"],
  modules: [...],
  permissions: [...],
}));
```

Swap the `roles` array here to test the UI as a different role during development.

---

## 1. Role-based permissions — `useIsMissionPlanner` and sibling hooks

Each role has its own one-line hook in `src/hooks/`, all following the same shape — read `user` from `localStorage`, check `roles` for the role string:

**`src/hooks/useIsMissionPlanner.js`**
```js
export function useIsMissionPlanner() {
  const user = JSON.parse(localStorage.getItem("user") || "{}");
  return user?.roles?.includes("mission-planner");
}
```

**`src/hooks/useIsMissionReviewer.js`**
```js
export function useIsMissionReviewer() {
  const user = JSON.parse(localStorage.getItem("user") || "{}");
  return user?.roles?.includes("mission-reviewer");
}
```

**`src/hooks/useIsMissionApprover.js`**
```js
export function useIsMissionApprover() {
  const user = JSON.parse(localStorage.getItem("user") || "{}");
  return user?.roles?.includes("mission-approver");
}
```

`useIsMissionPlanner` used to be re-implemented inline (`const user = JSON.parse(...); const isMissionPlanner = user?.roles?.includes(...)`) at the top of six page files. That duplication is gone — every page and sub-component now gets the flag with a single import:

```js
import { useIsMissionPlanner } from '@/hooks/useIsMissionPlanner';
// ...
const isMissionPlanner = useIsMissionPlanner();
```

Consumers of all three hooks:
- `src/pages/MissionSpecificParams.jsx`
- `src/pages/SearchRegime.jsx`
- `src/pages/BlankingChannels.jsx`
- `src/pages/ProgrammbleThreashold.jsx`
- `src/pages/OnboardTxInterference.jsx`
- `src/pages/pfmFileDetailTanStackUpdated.jsx`
- `src/components/layout/Headers/DataModifierHeader.jsx` (all three hooks — this is where the role actually branches the button row, see §2)

---

## 2. Approve / Reject / Send-for-Approval buttons

Everything lives in **`src/components/layout/Headers/DataModifierHeader.jsx`**, which already owned the Save / Generate Binary buttons and now branches its entire button row on role:

```js
const isMissionPlanner  = useIsMissionPlanner();
const isMissionReviewer = useIsMissionReviewer();
const isMissionApprover = useIsMissionApprover();
```

```jsx
{(isMissionReviewer || isMissionApprover) ? (
  <>
    {isMissionReviewer && <SendForApprovalButton onClick={handleSendForApproval} />}
    {isMissionApprover && <ApproveButton onClick={handleApprove} loading={isApproving} />}
    <RejectButton onClick={() => setIsRejectModalOpen(true)} />
  </>
) : (
  <>
    <GenerateBinaryButton .../>
    <SaveButton .../>
    {isMissionPlanner && <SendForApprovalButton onClick={handleSendForApproval} />}
  </>
)}
```

**Visibility by role:**

| Role | Buttons shown |
|---|---|
| `mission-planner` | Save, Generate Binary, **Send for Approval** |
| `mission-reviewer` | **Send for Approval**, **Reject** |
| `mission-approver` | **Approve**, **Reject** |
| none of the above | Save, Generate Binary only |

### Reject flow

Clicking **Reject** (reviewer or approver) opens a modal (`isRejectModalOpen` state) with a `<textarea>` bound to `rejectComment`. The **OK** button is disabled until the trimmed comment is non-empty:

```jsx
<button
  onClick={handleConfirmReject}
  disabled={!rejectComment.trim() || isRejecting}
>
  {isRejecting ? 'Submitting...' : 'OK'}
</button>
```

`handleConfirmReject` assembles notes from all 8 pages (see §3) and posts:

```js
const handleConfirmReject = async () => {
  if (!rejectComment.trim()) return;
  const notes = NOTES_PAGE_NAMES.map((pageName) => ({
    pageName,
    notes: pageNotes[pageName] ?? '',
  }));
  const rejectPayload = {
    PFMGID: pfmModifierId,
    comment: rejectComment,
    notes,
  };
  const result = await postRejected(rejectPayload);
  // ...
};
```

`PFMGID` is populated from the store's `pfmModifierId` (`useStore((state) => state.pfmModifierId)`) — the same id the Save flow sets via `setPfmModifierId` after a successful `savePfmFile` call.

### API endpoints integrated

Added to **`src/services/pmdaService.js`** (base URL: `https://192.168.1.55:9050/api/PMDA`):

```js
// POST {API_BASE_URL}/PostRejected
// body: { PFMGID: number, comment: string, notes: [{ pageName, notes }] }
export const postRejected = async (payload) => {
  const url = `${API_BASE_URL}/PostRejected`;
  const response = await axios.post(url, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  return response.data;
};

// GET {API_BASE_URL}/ApprovePFM?PFMGID={pfmgid}
export const approvePfm = async (pfmgid) => {
  const url = `${API_BASE_URL}/ApprovePFM`;
  const response = await axios.get(url, { params: { PFMGID: pfmgid } });
  return response.data;
};
```

`handleApprove` calls `approvePfm(pfmModifierId)` directly. **Send for Approval** (`handleSendForApproval`) currently only shows a confirmation modal — it is not wired to a backend endpoint yet; there's a comment in the header component flagging this:

```js
// Shown to MissionPlanner (next to Save) and mission-reviewer (forwarding
// to mission-approver). Routing/visibility across roles based on approval
// status is a future improvement — intentionally not wired up yet.
```

---

## 3. Notes field at the bottom of each page

**Component:** `src/components/PageNotesButton.jsx` — despite the name (kept for consistency with the original task naming), it renders a `<textarea>`, not a button:

```jsx
export default function PageNotesButton({ pageName }) {
  const note = useStore((state) => state.pageNotes[pageName] ?? '');
  const setPageNote = useStore((state) => state.setPageNote);

  return (
    <div className="border-t border-gray-200 bg-white p-3">
      <label className="mb-1 block text-sm font-medium text-gray-700">Notes</label>
      <textarea
        value={note}
        onChange={(e) => setPageNote(pageName, e.target.value)}
        rows={2}
        placeholder="Enter your notes for this page..."
        className="w-full resize-none rounded-[4px] border border-gray-300 p-2 text-sm focus:border-[#5F1562] focus:outline-none"
      />
    </div>
  );
}
```

**Why it lives in `Layout.jsx`, not on each page individually:** all 8 target pages (`pfm-file-detail-updated`, `analaysis-ambiguity`, `search-regime`, `cmds-params`, `onboard-tx`, `blanking-channels`, `mission-specific-params`, `programmable-thresold`) render through the same `<Layout />` route wrapper (see `src/components/Routing/Router.jsx`). Rendering `PageNotesButton` once in the shared layout — keyed by the current route — means each page needed zero changes, instead of duplicating the same markup 8 times across 8 files.

**`src/components/layout/Layout.jsx`:**
```jsx
const PAGE_NAME_BY_PATH = {
  "/pfm-file-detail-updated": "pfm-file-detail-updated",
  "/pfm-file-detail": "pfm-file-detail-updated",
  "/search-regime": "search-regime",
  "/mission-specific-params": "mission-specific-params",
  "/analaysis-ambiguity": "analaysis-ambiguity",
  "/cmds-params": "cmds-params",
  "/programmable-threshold": "programmable-thresold",
  "/onboard-tx": "onboard-tx",
  "/blanking-channels": "blanking-channels",
};

const Layout = () => {
  const { pathname } = useLocation();
  const notesPageName = PAGE_NAME_BY_PATH[pathname];
  return (
    <div className="h-screen w-full flex flex-col">
      <DataModifierHeader />
      <div className="flex flex-1 overflow-hidden">
        <PFMDMDetailsSidebar />
        <div className="flex flex-1 flex-col overflow-hidden">
          <main className="flex-1 p-4 overflow-y-auto"><Outlet /></main>
          {notesPageName && <PageNotesButton pageName={notesPageName} />}
        </div>
      </div>
    </div>
  );
};
```

It's rendered as a **fixed sibling stacked below `<main>`**, not an absolutely-positioned overlay — that way it's always visible and never covers scrollable page content.

**Note text is not thrown away on navigation.** It's written straight into the Zustand store, keyed by page name, so it survives moving between the 8 pages:

**`src/store/MapDataSlice.jsx`:**
```js
pageNotes: {},
// ...
setPageNote: (pageName, text) =>
  set((s) => ({ pageNotes: { ...s.pageNotes, [pageName]: text } })),
```



```js
const NOTES_PAGE_NAMES = [
  'pfm-file-detail-updated', 'analaysis-ambiguity', 'search-regime', 'cmds-params',
  'onboard-tx', 'blanking-channels', 'mission-specific-params', 'programmable-thresold',
];
// ...
const notes = NOTES_PAGE_NAMES.map((pageName) => ({
  pageName,
  notes: pageNotes[pageName] ?? '',
}));
```

---


