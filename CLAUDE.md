# NovaPlay — Claude Instructions

## Git Branch Naming Convention

At the start of every session, rename the working branch to follow this pattern before making any commits:

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feature/<kebab-case>` | `feature/add-payment-service` |
| Fix | `fix/<kebab-case>` | `fix/auth-token-expiry` |
| Refactor | `refactor/<kebab-case>` | `refactor/remove-cloud-config` |
| Chore | `chore/<kebab-case>` | `chore/update-dependencies` |

**Steps at session start:**
1. Understand the task from the user's message
2. Rename the current branch: `git branch -m <type>/<short-description>`
3. Push the renamed branch: `git push -u origin <new-branch-name>`
4. Proceed with the work

**Rules:**
- Use lowercase and hyphens only (no underscores, no uppercase)
- Keep the description short (2–5 words max)
- Derive the name from the actual task, not a generic label

## Repositories

- **Backend:** `81quanghuy/NovaPlay`
- **Frontend:** `81quanghuy/NovaPlay_FE`
