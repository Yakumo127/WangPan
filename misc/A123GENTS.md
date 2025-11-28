# Repository Guidelines

## Project Structure & Modules
- `backend/` — Spring Boot app (`src/main/java/com/filemanager/...`, resources in `src/main/resources`).
- `frontend/` — Vue 3 app (`src`, builds to `dist`, config in `vue.config.js`).
- `scripts/` — Local dev, startup, release helpers (e.g., `scripts/local_dev_setup.sh`, `start-all.sh`).
- `config/` — Dockerfiles, Nginx/MySQL/Redis configs used by `docker-compose.yml`.
- `database/` — Initialization/backup SQL.  
- `docs/` — Architecture notes and UML; export via `scripts/export-diagrams.sh`.

## Build, Run, and Development
- Backend
  - Build: `mvn clean package -DskipTests` (Java 17).
  - Dev run: `mvn spring-boot:run` (default port 8080).
  - Tests: `mvn test`.
- Frontend
  - Install: `npm ci` (or `npm install`).
  - Dev run: `npm run serve` (port 3000).
  - Build: `npm run build`; Lint: `npm run lint`.
- Full stack
  - Local bootstrap: `scripts/local_dev_setup.sh` (interactive).
  - Containers: `docker-compose up -d` (backend, frontend, MySQL, Redis, Nginx).

## Coding Style & Naming
- Java: 4-space indent; packages under `com.filemanager.*`; use Lombok where present; transactional boundaries in services; controllers return meaningful HTTP codes.
- Vue/JS: 2-space indent; ESLint + `eslint-plugin-vue` enforced via `npm run lint`. Components in `src/components`/`src/views` with PascalCase filenames; camelCase for vars/functions; route paths kebab-case.
- API modules live in `frontend/src/api`; prefer small, cohesive services on backend.

## Testing Guidelines
- Backend: JUnit 5 with Spring Boot Test. Place tests in `backend/src/test/java/...` named `*Test.java`. Favor `@WebMvcTest` for controllers and `@SpringBootTest` for integration. Run `mvn test` locally and in CI.
- Frontend: No unit test runner is bundled; at minimum run `npm run lint`. If adding tests, prefer `@vue/test-utils` + Jest and locate them near components or under `tests/`.

## Commit & PR Guidelines
- Use Conventional Commits: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:` (scope optional, English/中文均可)。
- PRs include: clear description, linked issues, test plan (commands), screenshots for UI, and notes for DB/config changes (`config/init.sql`, `.env`, Nginx).
- Don’t commit secrets; avoid committing build artifacts (e.g., `frontend/dist/`) except for tagged release assets in `release-artifacts/`.

## Security & Configuration
- `.env` holds local defaults; never store real secrets. MySQL/Redis credentials are set in `docker-compose.yml`—change via environment, not code.
- For diagrams, run `scripts/export-diagrams.sh` to regenerate PNG/SVG from PlantUML.

