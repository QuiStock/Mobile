# Pop-ups and chatbot menu

**Status:** Complete  
**Issue:** none  
**Product decision owner:** feature requester

## Problem and goal

The app needs consistent feedback for successful and failed actions, confirmation before order and promotion submissions, a recoverable offline state in the chatbot, and the chatbot conversation menu shown in the supplied designs.

## Scope

**In scope:** Shared success/error feedback; confirmation dialogs and basic required-field checks for order and promotion forms; manager-specific order/promotion approval confirmations using the same dialog style; chatbot offline feedback and menu overlay.  
**Out of scope:** Backend submission, persisted chat history, actual alternate chat sessions, and server-driven rejection states.

## Expected behavior

Status feedback uses `ic_error` with `#CA2424` for errors and `ic_success` with `#89B751` for success. Order and promotion submission first validates required inputs, then asks for confirmation; cancel leaves the form intact, confirm shows a success message. Manager approval prompts use the same dialog layout with pre-defined order/promotion approval wording. Chatbot network failures show the offline dialog; retry resubmits the last message. The menu icon opens a dismissible panel with a new-chat action and recent-chat labels. New chat clears the visible transcript and input.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Login and chatbot errors show consistent red feedback; successful actions show green feedback. | Manual review of rendered screens and state handling |
| AC-02 | Empty order/promotion fields show a validation error; valid forms ask for confirmation; manager approval prompts show the correct approval copy; Yes and No have distinct outcomes. | Manual form interaction |
| AC-03 | Chatbot network failure offers retry and close; retry resends the last prompt. | Manual offline/online check |
| AC-04 | The chatbot menu opens, dismisses, and starts a clean visible conversation. | Manual interaction |

## Technical impact

Adds reusable presentation components and updates login, chatbot, order, and promotion UI. No data persistence or API contract changes are intended.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Displaying actual saved conversations in the menu | Open | Backend/product; current API does not expose chat history here |
| Rejection, image-upload, FEFO, and contact outcomes | Open | Those flows do not currently exist in this branch |

## Implementation outcome

Delivered shared error/success snackbars, input validation feedback, order and promotion confirmation flows, reusable manager approval confirmations, chatbot offline retry dialog, and the chatbot menu overlay. Added ChatbotViewModel unit tests and updated login UI assertions for the popup. `git diff --check` passed. Gradle assembly and formatter execution could not complete in this environment because Gradle failed with `Unable to establish loopback connection`; manual device review remains outstanding. The manager approval prompts are ready to be called from the manager screens when those destinations are implemented.
