---
name: pocketbase
description: >-
  Open-source real-time backend in a single binary featuring embedded SQLite database, Auth, File storage, REST APIs, and JS/Go hooks. Use this skill when developing PocketBase backends, writing custom PocketBase JS/Go migrations/hooks, configuring collections/schemas, or interacting with PocketBase REST APIs.
---

# PocketBase Backend Skill

Use this skill to develop, configure, and integrate PocketBase backend applications, custom collection schemas, realtime subscriptions, and server side hooks.

## Key Capabilities & Use Cases

- **Collection & Schema Configuration**: Define Base, Auth, and View collection schemas with field validation rules.
- **REST & Realtime API**: Perform CRUD operations, filter query records, handle file uploads, and subscribe to SSE realtime updates.
- **JS/Go Server Hooks**: Write server-side hooks (e.g., `onRecordBeforeCreateRequest`, `onModelAfterUpdate`) to extend backend business logic.
- **Authentication**: Configure OAuth2 providers, email/password auth, and record-level Access Control Rules (API Rules).

## Development Workflow

1. **Schema Design**:
   - Create collections via PocketBase Admin UI or JS/Go migration files.
2. **API Rule Definition**:
   - Set granular permissions for `@request.auth.id != ""`, role-based access, or owner filtering.
3. **Writing Custom Hooks**:
   - Implement event listeners in `pb_hooks/*.pb.js` or Go server files.
4. **Client SDK Integration**:
   - Use `pocketbase` JS SDK or REST HTTP endpoints to connect frontend apps.
