# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Clojure-based AI agent framework that implements a multi-agent system with OpenAI integration. The architecture supports multiple specialized agents that can delegate work to each other through a manager agent.

## Key Commands

### Running the Application
```bash
# Start the interactive CLI chat
clojure -M -m agente.core

# Run tests
clojure -T:test
```

### Development Workflow
```bash
# Start a REPL for interactive development
clojure -M:repl

# Run tests
clojure -T:test
```

## Architecture

### Agent System
The codebase implements a multi-agent architecture where:
- **Manager Agent** (`agents/manager.clj`): Orchestrates and delegates to other agents
- **Specialized Agents**: Currently includes Marketeer and SEO agents
- Each agent has: system prompt, memory, tools, and LLM configuration

### Tool System
- Tools are defined using the `def-tool` macro in `tools/core.clj`
- Tools generate OpenAI function schemas automatically
- Agents can have multiple tools attached for function calling

### LLM Integration
- Multi-method dispatch pattern for different LLM providers in `agents/core.clj`
- Currently only OpenAI is implemented via `wkok/openai-clojure`
- Configuration in `config.edn` sets default GPT-4o parameters

### Input Processing
- Multi-method engine in `engine.clj` handles different input sources
- CLI interface in `core.clj` supports:
  - Agent routing: `@agent-name message`
  - File injection: `@filename` to include file contents
  - Default routing to manager agent

## Important Notes

- API keys are stored in `secrets.edn` (not committed)
- The file `core.clj` at root appears to be legacy code - the active main is in `src/agente/core.clj`
- When adding new agents: implement in `agents/` directory and register in the manager's agent list
- When adding new tools: use `def-tool` macro and attach to relevant agents