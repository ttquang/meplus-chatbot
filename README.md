# Meplus Chatbot

A process-driven chatbot built on Spring Boot and Spring AI (DeepSeek). Conversations follow
YAML-defined processes (loan application, loan status, lead capture, medical-supply inquiry),
with an LLM generating each turn and a catalog of APIs the processes can call. It also provides
a product catalog with embedding-based product and category search.

## Stack

- Java 25, Spring Boot, Spring AI (DeepSeek chat model)
- PostgreSQL (JPA, `ddl-auto: update`); H2 for tests
- Ollama for product embeddings (`qwen3-embedding:4b`)

## Getting started

Prerequisites: JDK 25, Docker.

```sh
docker compose up -d postgres        # database on localhost:5432
export DEEPSEEK_API_KEY=<your key>   # required, no default
./mvnw spring-boot:run               # http://localhost:8080
```

On Windows use `mvnw.cmd` and `set DEEPSEEK_API_KEY=...`. Make sure `JAVA_HOME` points to JDK 25.

Run the tests with `./mvnw test`.

## Configuration

Set in `src/main/resources/application.yaml`; most values can be overridden by environment variables.

| Variable | Purpose | Default |
|---|---|---|
| `DEEPSEEK_API_KEY` | DeepSeek API key | none (required) |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection | `localhost`, `5432`, `chatbot`, `chatbot`, `chatbot` |
| `API_BASE_URL` | Base URL for catalog API calls | `http://localhost:8080` |
| `API_ALLOWED_HOSTS` | Hosts catalog APIs may call | `localhost,127.0.0.1` |
| `OLLAMA_BASE_URL` | Ollama server for embeddings | `http://172.24.1.81:11434` |
| `EMBEDDING_REINDEX_ON_STARTUP` | Embed new/changed products at startup | `false` |
| `OTP_MOCK_CODE` | Fixed OTP value for demos | random |

## Project layout

- `src/main/resources/processes/` – conversation process definitions (YAML)
- `src/main/resources/apis/` – API catalog the processes can call
- `src/main/resources/static/` – web UIs (`index.html`, `loan.html`, `console/`)
- `src/main/java/com/ttq/` – engine, LLM, actions, API executor, product catalog, embeddings, mock CRM/OTP
- `db/` – SQL update scripts
- `design/landing/` – landing page designs

## API overview

All endpoints are under `/api`:

- `/conversations`, `/processes` – start conversations and post messages
- `/products`, `/product-categories`, `/brands`, `/product-attributes`, `/product-tags`,
  `/product-variants`, `/sellable-units` – catalog
- `/product-search`, `/product-embeddings` – search and reindexing
- `/leads`, `/loan-purposes` – loan leads and reference data
- `/otp`, `/mock-crm` – mock OTP and CRM services
