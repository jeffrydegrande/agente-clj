(ns agente.core
  (:require
    [agente.cli :as cli]
    [agente.mcp.init :as mcp-init]))

(defn -main []
  (println "🚀 Starting agente-clj with MCP support...")
  
  ;; Initialize MCP servers if configured
  (mcp-init/auto-initialize-mcp)
  
  ;; Start the CLI
  (cli/chat-loop))