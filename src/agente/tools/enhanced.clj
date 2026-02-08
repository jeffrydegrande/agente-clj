(ns agente.tools.enhanced
  "Enhanced tools system that combines native and MCP tools"
  (:require
    [agente.tools.core :as core]
    [agente.mcp.core :as mcp]))

(defn get-all-tools
  "Get all available tools (native + MCP)"
  [native-tools]
  (let [mcp-tools (vals (mcp/get-mcp-tools))]
    (concat native-tools mcp-tools)))

(defn get-tool-by-name
  "Find a tool by name in both native and MCP tools"
  [tool-name native-tools]
  (or (some #(when (= (:name %) tool-name) %) native-tools)
      (mcp/get-mcp-tool (keyword tool-name))))

(defn enhanced-tool-schema
  "Generate OpenAI tool schema for enhanced tools"
  [tool]
  {:type "function"
   :function {:name (:name tool)
              :description (:description tool)
              :parameters (:parameters tool)}})

(defn call-enhanced-tool
  "Call either native or MCP tool"
  [tool-name args native-tools]
  (if-let [tool (get-tool-by-name tool-name native-tools)]
    ((:handler tool) args)
    (throw (ex-info "Tool not found" {:tool tool-name}))))

(defmacro def-enhanced-agent
  "Define an agent with both native and MCP tools"
  [agent-name agent-config]
  `(let [native-tools# (:tools ~agent-config [])
         base-config# (dissoc ~agent-config :tools)
         enhanced-config# (assoc base-config# 
                                 :tools (get-all-tools native-tools#)
                                 :native-tools native-tools#)]
     (def ~agent-name enhanced-config#)))

(defn mcp-tool-info
  "Get information about available MCP tools"
  []
  (let [mcp-tools (mcp/get-mcp-tools)
        mcp-status (mcp/get-mcp-status)]
    {:mcp-status mcp-status
     :tools (into {} 
                  (map (fn [[k v]] 
                         [k {:name (:name v)
                             :description (:description v)
                             :mcp-tool (:mcp-tool v)}])
                       mcp-tools))}))

(defn refresh-agent-tools
  "Refresh MCP tools for an agent"
  [agent]
  (let [refreshed-count (mcp/refresh-mcp-tools)
        native-tools (:native-tools agent [])
        enhanced-tools (get-all-tools native-tools)]
    (println "[Enhanced Tools] Refreshed" refreshed-count "MCP tools")
    (assoc agent :tools enhanced-tools)))