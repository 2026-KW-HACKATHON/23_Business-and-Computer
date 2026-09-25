# Git Commit Handoff

## When to Apply

Apply this rule when the user asks to commit changes or to prepare commits.

## Rules

- The user performs staging and commits. Never run `git add`, `git commit`, or another command that stages or creates a commit on the user's behalf.
- Inspect the working tree with read-only Git commands before suggesting commands. Include only changes intended for the requested commits; do not stage unrelated or user-owned changes.
- Split changes into coherent, reasonably fine-grained commits. Keep code and its required tests together when separating them would leave an incomplete change. Use explicit paths in each `git add -- ...` command instead of `git add .` or `git add -A`.
- For each commit, provide the `git add -- ...` command followed by the `git commit -m "타입: 한국어 메시지"` command, in execution order. The user runs the commands.
- The commit type must be one of `feat`, `chore`, or `test`. Use `feat` for behavior changes, `chore` for maintenance, configuration, or documentation, and `test` for test-only changes. Write the message after the colon in Korean.
- When the user asks for commit commands, provide the commands only. Do not execute them.
