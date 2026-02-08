# MCP Integration Guide

agente-clj now supports remote MCP (Model Context Protocol) servers! This allows your agents to access external tools and data sources through standardized MCP endpoints.

## What is MCP?

MCP (Model Context Protocol) is an open standard that enables AI models to securely access external data sources and tools. With MCP, your agents can:

- Query databases (Datomic, SQL, etc.)
- Access file systems
- Interact with APIs and web services
- Use specialized tools without custom integrations

## Quick Start

### 1. Configure MCP Servers

Edit `mcp-config.edn` to add your MCP servers:

```clojure
{
 :filesystem {:endpoint "http://localhost:8080/mcp"
              :headers {"Authorization" "Bearer your-token"}
              :timeout 30000}

 :database {:endpoint "https://your-db-mcp.example.com/mcp"
            :headers {"API-Key" "your-api-key"}
            :timeout 45000}
}
```

### 2. Start agente-clj

```bash
clojure -M -m agente.core
```

MCP servers will be automatically connected on startup.

### 3. Use MCP Tools

MCP tools are automatically available to all agents:

```
You> tell me about MCP status
You> what databases are available?
You> list files in the project directory
```

## Architecture

### Seamless Integration

MCP tools appear alongside native tools - agents don't know the difference:

```clojure
;; Native tool
@manager "write a blog post"  ; uses blog tool

;; MCP tool  
@manager "query user database"  ; uses MCP database tool
```

### Tool Discovery

Tools are dynamically discovered from connected MCP servers:

- **Automatic**: Tools loaded on startup
- **Dynamic**: Use `mcp-status` to see available tools
- **Refresh**: Use `refresh-mcp-tools` to reload tools

## Configuration Options

### Server Configuration

Each MCP server supports:

```clojure
{:endpoint "https://your-server.com/mcp"    ; Required: MCP endpoint URL
 :headers {"Auth" "Bearer token"}           ; Optional: HTTP headers
 :timeout 30000}                            ; Optional: timeout in ms
```

### Headers for Authentication

Common authentication patterns:

```clojure
;; Bearer token
:headers {"Authorization" "Bearer your-jwt-token"}

;; API key
:headers {"X-API-Key" "your-api-key"}

;; Custom auth
:headers {"Authorization" "Bearer token"
          "X-Client-ID" "your-client-id"}
```

## Built-in MCP Tools

The manager agent includes these MCP management tools:

- **`mcp-status`**: Check connected servers and available tools
- **`refresh-mcp-tools`**: Reload tools from all servers

## Technical Details

### JSON-RPC 2.0 Protocol

MCP uses JSON-RPC 2.0 over HTTP:

```http
POST /mcp
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "method": "tools/list",
  "params": {},
  "id": 1
}
```

### Error Handling

- Connection failures are logged and gracefully handled
- Tool errors are returned as error messages to the agent
- Servers can be added/removed dynamically

### Transport Layer

agente-clj implements:

- **HTTP Transport**: For remote MCP servers
- **JSON-RPC 2.0**: Standard message format
- **Connection pooling**: Efficient server communication
- **Timeout handling**: Configurable request timeouts

## Examples

### Database Access

```clojure
;; mcp-config.edn
{:datomic {:endpoint "http://localhost:8080/mcp"
           :headers {"Authorization" "Bearer datomic-token"}}}
```

```
You> @manager "How many users do we have?"
Manager: [calls MCP database tool]
Result: "Database shows 1,247 active users"
```

### File System Access

```clojure
{:filesystem {:endpoint "http://localhost:9090/mcp"}}
```

```
You> @manager "What files are in the docs folder?"
Manager: [calls MCP filesystem tool]
Result: "Found 15 files: README.md, API.md, ..."
```

## Troubleshooting

### Check MCP Status

```
You> tell me about MCP status
```

### Refresh Tools

If tools aren't appearing:

```
You> refresh MCP tools
```

### Common Issues

1. **Connection Failed**: Check endpoint URL and network connectivity
2. **Authentication Error**: Verify headers and API keys
3. **Timeout**: Increase timeout in configuration
4. **No Tools**: Server may not implement tools/list correctly

## Advanced Usage

### Programmatic Access

```clojure
(require '[agente.mcp.core :as mcp])

;; Add server at runtime
(mcp/add-mcp-server "my-server" "http://localhost:8080/mcp")

;; List available tools
(mcp/get-mcp-tools)

;; Call tool directly
(mcp/call-mcp-tool :my-tool {:arg1 "value"})
```

### Custom Transport

You can extend the transport layer for custom protocols:

```clojure
(require '[agente.mcp.transport :as transport])

;; Implement MCPTransport protocol for custom transports
```

## Security Considerations

- **HTTPS**: Always use HTTPS for remote servers
- **Authentication**: Use proper API keys or JWT tokens
- **Network**: Consider VPNs for internal servers
- **Validation**: MCP responses are validated before use

## What's Next?

agente-clj's MCP integration makes it one of the first Clojure agent frameworks with native remote MCP support. This opens up powerful possibilities for connecting your agents to existing data sources and tools through the standardized MCP protocol.

Try connecting to your own MCP servers and see what your agents can do!