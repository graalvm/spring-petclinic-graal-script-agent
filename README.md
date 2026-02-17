# Spring PetClinic Demo Enhanced with Graal Script Agent

This project extends the Spring PetClinic sample application with an interactive Script Agent for working with PetClinic owner data. The extension is built on the Graal Script Agent library.

The original Spring PetClinic application is still present: you can browse owners, pets, vets, and visits through the normal UI. This demo adds a **Script Agent** tab where users can describe data tasks in a chat-like interface and have the application generate scripts for them.

## Run

### JVM

Use GraalVM 25.0.3 to run the application. Set `JAVA_HOME` to the GraalVM 25.0.3 installation directory before running Maven commands.

### In-Memory Storage

The default configuration uses an in-memory H2 database. It is populated at startup and does not require Docker or an external database.

```bash
./mvnw spring-boot:run
```

Then open <http://localhost:8080>.

Because the database is in memory, changes made through the normal UI or through Script Agent scripts are lost when the application stops.

### Persistent Oracle Storage

Use the `oracle` Spring profile when you want owner data and saved query scripts to survive application restarts.

Start Oracle Database Free with Docker Compose:

```bash
sudo docker compose up oracle
```

Wait until the database is ready, then start the application with the Oracle profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=oracle
```

The default Oracle connection settings are:

```text
URL:      jdbc:oracle:thin:@localhost:1521/FREEPDB1
User:     petclinic
Password: petclinic
```

You can override them with `ORACLE_URL`, `ORACLE_USER`, and `ORACLE_PASS`.

The Oracle profile initializes the schema and default PetClinic data on startup. The seed data is written to avoid recreating default rows after those rows have been edited.

Additional Oracle setup notes are available in [src/main/resources/db/oracle/petclinic_db_setup_oracle.txt](src/main/resources/db/oracle/petclinic_db_setup_oracle.txt).

### LLM Configuration

For now, the demo supports only OpenAI-compatible models. Script generation is enabled when `MODEL_API_KEY` is set. Without it, the application can still start, but the Script Agent cannot generate scripts.

The Script Agent uses these environment variables:

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `MODEL_API_KEY` | Yes | None | API key passed to the LangChain4j OpenAI-compatible responses model. |
| `MODEL_NAME` | No | `gpt-5.5` | Model name passed to the LLM provider. |
| `MODEL_REASONING_EFFORT` | No | `low` | Reasoning effort value passed to the responses model. |
| `MODEL_BASE_URL` | No | Provider default | Optional base URL for an OpenAI-compatible endpoint. |

## Script Agent

The Script Agent page accepts natural-language prompts and responds with generated scripts plus a preview of what those scripts would do.

Generated scripts can query owner data or modify it. Scripts that modify data first show a preview of the planned changes. The user can inspect that preview and then choose whether to execute the script for real.

Query scripts that return owner lists can be saved and added to the **Find Owners** page. Once saved, those scripts become reusable owner searches that can be executed directly from the normal owner search workflow.

The Script Agent supports sessions, so a user can continue a conversation while refining prompts and generated scripts.

### Implementation Notes

The Script Agent page uses the `script-agent` library to generate scripts with an LLM. To use `ScriptAgent`, the application must define a schema for generated scripts. This schema is defined in `PetClinicScriptExtensions`.

The schema lets generated scripts choose one extension type through `ExtensionSelector`: `OwnerQueryHierarchyResultExtension` for owner hierarchy query results, `OwnerQueryJsonResultExtension` for JSON query results, or `ModificationExtension` for scripts that modify owners, pets, or visits. Query extensions receive only `OwnersApi`; modification scripts receive `OwnersApi` and `ModificationApi` and do not return a result.

`ScriptGenerationService` uses LangChain4j to connect to the LLM model configured through the `MODEL_*` environment variables described below. The configured model is passed to the `ScriptAgent` builder.

For each chat, `ScriptGenerationService` creates or reuses a `ScriptAgent` session in `getOrCreateSession`. The session is created with the PetClinic script schema, and `generateScript` calls `generate` on that session to obtain the generated script.

Generated scripts are executed by `ScriptService` in a GraalVM polyglot `Context`. The context is created from a `Sandbox` configured with the sandbox options used by this demo before the script is evaluated.

## Original Spring PetClinic README

The original upstream Spring PetClinic README is preserved in [README_ORIG.md](README_ORIG.md).

Some information in that file may not apply to this demo. In particular, use this README for the persistent database setup because this project uses the Oracle profile and Docker Compose configuration from this repository.
