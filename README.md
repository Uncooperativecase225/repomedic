# RepoMedic

**Local-first AI repository diagnosis and verified repair.** RepoMedic combines deterministic JavaScript/TypeScript diagnostics with an open-weight model running locally through Ollama. It can register a local Git repository, run explicitly selected checks, ask Qwen to explain a finding, generate a constrained full-file repair proposal, validate that proposal in an isolated Git worktree, and apply it to the original working tree only after validation and user approval.

> **Current scope:** This repository is an MVP foundation for JavaScript/TypeScript projects with `package.json` and Git metadata. It is not a general-purpose autonomous coding agent. The model can be wrong, and a passing build is not proof that a project is bug-free.

## Why open AI matters here

- **Local inference:** Prompts and selected source context are sent to the configured Ollama endpoint. With Ollama on the same machine, that endpoint is local and no hosted AI API is required.
- **Model choice:** The configured model is an environment setting (`OLLAMA_MODEL`), so the model can be changed without rewriting the repair workflow, provided the model works with the chosen Ollama integration.
- **No per-token hosted API bill:** Local inference avoids hosted inference charges, although it still consumes hardware, electricity, memory, and time.
- **Evidence-first architecture:** Git, npm scripts, and lint/build tools supply evidence. The LLM explains evidence and proposes a patch; deterministic checks determine whether the proposal passed the configured validation commands.

## Features

- Register local Git repositories containing a `package.json`.
- Detect project type from the package manifest and TypeScript configuration.
- Persist repository, scan, diagnostic, repair, model-run, and validation metadata in MySQL.
- Run manifest inspection without executing project scripts.
- Optionally run `npm run build` and/or `npm run lint` after the user explicitly enables each check.
- Generate an AI explanation using Qwen through Ollama.
- Generate a constrained repair proposal containing complete replacement content for a small number of files.
- Block AI patching of package manifests, lockfiles, environment files, CI workflows, and shell/batch scripts.
- Validate a proposal in a separate Git worktree, then permit application only when validation passes and the original repository is clean and still at the same HEAD commit.
- Display scan status, findings, proposal content, and validation outcome in a dark developer dashboard.
- Expose REST APIs and OpenAPI documentation.

## Architecture

```text
React + TypeScript + Vite
            |
            | REST / JSON
            v
Spring Boot (Java 21)
  |         |             |
  |         |             +--> Git / npm / ESLint / TypeScript tools
  |         |
  |         +--> Ollama HTTP API --> Qwen 3.5 9B (local model)
  |
  +--> Spring Data JPA / Hibernate --> MySQL
                    |
                    +--> Flyway schema migrations
```

### Technology responsibilities

| Technology | Responsibility |
|---|---|
| React + TypeScript + Vite | User interface and client-side state |
| Tailwind/shadcn-style design system | The current MVP uses custom CSS for a lightweight dark interface; components are designed to be reusable |
| Spring Boot + Java | API, orchestration, validation, job coordination, and process control |
| Spring Web | REST endpoints |
| Spring AI Ollama starter | Local model integration |
| Ollama + Qwen 3.5 9B | Diagnostic explanations and repair proposals |
| MySQL | Durable application metadata and history |
| Spring Data JPA / Hibernate | Object-relational persistence |
| Flyway | Versioned database schema |
| Git CLI / worktrees | Repository inspection and isolated repair validation |
| npm scripts | Project-selected build and lint checks |
| JUnit / Mockito / Vitest | Automated tests (test coverage should grow with the product) |

## Prerequisites

Install the following before starting:

1. **Java 21** (JDK) and Maven 3.9+ (or use an IDE with Maven support).
2. **Node.js 20+** and npm.
3. **Git** available on `PATH`.
4. **Docker Desktop** with Docker Compose, or a local MySQL 8.x server.
5. **Ollama** installed and running locally.
6. The Qwen 3.5 9B model artifact supported by your Ollama installation.

Official references:

- Spring Initializr: https://start.spring.io/
- Ollama API: https://docs.ollama.com/api/introduction
- Ollama model library: https://ollama.com/library
- Spring AI: https://docs.spring.io/spring-ai/reference/
- MySQL: https://dev.mysql.com/doc/
- Node.js: https://nodejs.org/
- Git: https://git-scm.com/doc

### Hardware note

A 9B model can be memory-intensive. On a laptop with 16 GB RAM and 6 GB VRAM, performance depends on the model quantization, context length, and CPU/GPU offloading. Begin with a moderate context window and verify inference speed before using large diagnostic logs. The exact model tag may vary; use the tag actually installed in Ollama.

## Quick start

### 1. Clone or extract the project

Open a terminal at the project root (`repomedic/`).

### 2. Configure environment variables

Copy `.env.example` to `.env` and update passwords as appropriate. Spring Boot does not automatically load a root `.env` file in every launch mode, so export the values in your shell or configure them in your IDE/run configuration.

**PowerShell example:**

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/repomedic?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME = "repomedic"
$env:DB_PASSWORD = "repomedic_local_only"
$env:OLLAMA_BASE_URL = "http://localhost:11434"
$env:OLLAMA_MODEL = "qwen3.5:9b"
$env:FRONTEND_ORIGIN = "http://localhost:5173"
```

Use the actual model tag from `ollama list` if it differs from `qwen3.5:9b`.

### 3. Start MySQL with Docker Compose

From the project root:

```bash
docker compose up -d mysql
```

The database is bound to `127.0.0.1:3306`, not all network interfaces. Docker Compose creates the `repomedic` database and user on first initialization. The default credentials are for local development only; change them before using any non-disposable data.

If you already have MySQL installed, create a database and user manually, then configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` accordingly.

### 4. Start Ollama and verify Qwen

```bash
ollama list
ollama run qwen3.5:9b
```

If the exact tag is unavailable, find the correct Qwen 3.5 9B tag in the Ollama library and set `OLLAMA_MODEL` to that tag. Exit the interactive prompt when finished. Ollama should remain running in the background.

Check the API:

```bash
curl http://localhost:11434/api/tags
```

### 5. Start the Spring Boot backend

```bash
cd backend
mvn spring-boot:run
```

Flyway runs the schema migrations at startup. The API binds to `127.0.0.1:8080` by default.

Useful endpoints:

- Health: `http://localhost:8080/api/health`
- Ollama model status: `http://localhost:8080/api/settings/model`
- OpenAPI UI: `http://localhost:8080/swagger-ui/index.html`
- Actuator health: `http://localhost:8080/actuator/health`

### 6. Start the frontend

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

Optional frontend configuration: copy `frontend/.env.example` to `frontend/.env.local` and set `VITE_API_BASE_URL` if the backend URL differs.

### 7. Register a repository

1. Open RepoMedic in the browser.
2. Select **Add repository**.
3. Enter the absolute path to a local Git repository that contains `package.json`.
4. Select the repository from the sidebar.
5. Start a scan. By default, RepoMedic inspects the manifest without executing project scripts.
6. Enable build and/or lint checks only when you trust the repository and understand that its npm scripts will execute code.
7. Open a finding and ask Qwen to explain it or propose a repair.
8. Review the full-file replacement proposal.
9. Validate it in an isolated Git worktree.
10. Apply it only if validation passes and the original repository has a clean working tree and unchanged HEAD. Review the resulting Git diff before committing.

## Windows notes

- Use absolute paths such as `C:\Users\you\source\my-app` in the repository registration dialog.
- Ensure `git`, `npm`, `java`, and `mvn` are available in the terminal's `PATH`.
- Symlink creation for `node_modules` in a temporary worktree may depend on Windows permissions/developer settings. If the symlink cannot be created, validation may report missing dependencies. Run the project in a trusted local environment and install dependencies in the target repository before validation.
- Keep the backend and Ollama bound to localhost. Do not port-forward them to the public internet.

## API overview

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/health` | Backend, database, and Ollama status |
| GET | `/api/settings/model` | Configured model and Ollama model list |
| GET | `/api/repositories` | List registered repositories |
| POST | `/api/repositories` | Register a repository (`{"path":"absolute/path"}`) |
| GET | `/api/scans` | Recent scans |
| POST | `/api/scans` | Start a scan (`repositoryId`, `runBuild`, `runLint`) |
| GET | `/api/scans/{id}` | Get scan status |
| GET | `/api/scans/{id}/diagnostics` | List diagnostics |
| GET | `/api/scans/repository/{repositoryId}` | List scans for a repository |
| POST | `/api/diagnostics/{id}/explain` | Generate AI explanation |
| POST | `/api/repairs` | Generate a proposal from a diagnostic |
| GET | `/api/repairs/{id}` | Get proposal details |
| GET | `/api/scans/{scanId}/repairs` | List proposals for a scan |
| POST | `/api/repairs/{id}/validate` | Validate in an isolated worktree |
| POST | `/api/repairs/{id}/apply` | Apply a validated proposal after safety checks |
| GET | `/api/repairs/{id}/validations` | Get validation history |

## Database model

Flyway migration `V1__initial_schema.sql` creates:

- `repositories`: registered repository paths and detected project types.
- `scans`: scan options, status, summary, and timestamps.
- `diagnostics`: diagnostic type, severity, location, message, and bounded evidence.
- `repairs`: proposal, model, status, and validation summary.
- `validation_results`: command, status, exit code, and bounded output summary.
- `model_runs`: model operation status and timing metadata.

The schema intentionally stores metadata and limited evidence rather than a full repository snapshot. Treat scan logs and generated patches as potentially sensitive. The MVP does not provide multi-user authentication; it is designed for a single local user.

## Safety model and limitations

RepoMedic is designed to reduce risk, not eliminate it.

- **Repository scripts are code execution.** Build/lint checks are opt-in, but still execute scripts defined by the repository. Only scan repositories you trust.
- **A Git worktree is not a sandbox.** It isolates Git state, not operating-system permissions. For untrusted code, use a disposable VM/container with no sensitive host mounts or credentials.
- **AI output is untrusted.** The MVP limits proposal file count and blocks certain high-risk paths, but this is not a complete policy engine. Inspect every proposed change.
- **Validation is scoped.** The MVP runs the configured `build` and `lint` scripts that exist. It does not automatically discover or run every test suite, and a passing build does not guarantee correctness.
- **Clean working tree required.** Validation and application are blocked if the original repository has uncommitted changes or HEAD changes after the proposal is generated.
- **Model output can fail.** Qwen may return invalid JSON, a poor fix, or no patch. RepoMedic reports these cases rather than assuming success.
- **Local inference privacy depends on configuration.** Source context goes to the configured Ollama endpoint. If you point it at a remote host, that host receives the context.
- **Local-only deployment.** The backend binds to loopback. This MVP has no login system and should not be exposed to a network.

## Development and tests

Backend tests:

```bash
cd backend
mvn test
```

Frontend production build:

```bash
cd frontend
npm install
npm run build
```

Frontend tests:

```bash
cd frontend
npm test
```

Before a release, add integration tests using a disposable Git fixture and a mocked Ollama client. Exercise at least: malformed model output, path traversal attempts, dirty working tree, changed HEAD, process timeout, missing npm scripts, validation failure, and rollback behavior.

## Troubleshooting

### Backend cannot connect to MySQL

- Confirm MySQL is running: `docker compose ps`.
- Check `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the backend process environment.
- Confirm port `3306` is available and the database/user exist.
- Review backend logs for Flyway or connection errors.

### Ollama shows offline

- Start Ollama and run `ollama list`.
- Check `OLLAMA_BASE_URL` and `OLLAMA_MODEL`.
- Verify `http://localhost:11434/api/tags` responds.
- The model tag must match an installed model exactly.

### AI response could not be parsed

- Try a smaller diagnostic or shorter evidence output.
- Confirm the model tag points to a Qwen 3.5 9B artifact that works with Ollama.
- Local models may not reliably follow JSON-only instructions; retrying or improving prompts may help, but do not skip response validation.

### Validation fails due to missing dependencies

- Ensure dependencies are installed in the original repository.
- Check whether the operating system permits the temporary `node_modules` symlink.
- Inspect the validation result before attempting any apply operation.
- Do not let RepoMedic install dependencies automatically from untrusted projects.

### Port already in use

- Change `server.port` in `backend/src/main/resources/application.yml` or stop the process using port `8080`.
- Change the Vite port in `frontend/vite.config.ts` and update `FRONTEND_ORIGIN` / frontend API configuration if necessary.

## Roadmap

1. Add stronger integration tests for process execution and patch rollback.
2. Add a sandbox provider for untrusted repositories.
3. Add a robust parser for diagnostic locations across Vite, TypeScript, ESLint, and test output.
4. Add Monaco Diff Editor for line-by-line patch review.
5. Add test-suite discovery and baseline-versus-patch diagnostic comparison.
6. Add persistent job recovery and progress streaming.
7. Add authentication only if RepoMedic becomes a multi-user or networked service.
8. Consider Tauri packaging once the local web workflow is stable.

## License

Choose a license before publishing this repository. Also review the license and distribution terms for the selected Qwen model artifact and every bundled dependency.
