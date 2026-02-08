(ns agente.mcp.core
  "Core MCP integration for agente"
  (:require
   [agente.mcp.client :as client]
   [clojure.string :as str]))

(def ^:private mcp-clients (atom {}))
(def ^:private mcp-tools (atom {}))

(defn add-mcp-server
  "Add a remote MCP server"
  [server-name endpoint & {:keys [headers timeout] :or {timeout 30000}}]
  (try
    (println "[MCP] Connecting to" server-name "at" endpoint)
    (let [client (client/connect-and-initialize endpoint
                                                :headers headers
                                                :timeout timeout)
          tools (client/get-mcp-tools-as-def-tools client)]

      (swap! mcp-clients assoc server-name client)
      (swap! mcp-tools merge tools)

      (println "[MCP] Successfully connected to" server-name)
      (println "[MCP] Added" (count tools) "tools:" (str/join ", " (keys tools)))

      {:status :success
       :server server-name
       :tools-count (count tools)
       :tools (keys tools)})

    (catch Exception e
      (println "[MCP] Failed to connect to" server-name ":" (.getMessage e))
      {:status :error
       :server server-name
       :error (.getMessage e)})))

(defn remove-mcp-server
  "Remove an MCP server and its tools"
  [server-name]
  (when-let [client (get @mcp-clients server-name)]
    (swap! mcp-clients dissoc server-name)

    ; Remove tools that belong to this server
    ; (This is simplified - in practice you'd want to track which tools belong to which server)
    (println "[MCP] Removed server" server-name)))

(defn list-mcp-servers
  "List all connected MCP servers"
  []
  (keys @mcp-clients))

(defn get-mcp-tools
  "Get all available MCP tools in def-tool format"
  []
  @mcp-tools)

(defn get-mcp-tool
  "Get a specific MCP tool"
  [tool-name]
  (get @mcp-tools tool-name))

(defn call-mcp-tool
  "Call an MCP tool directly"
  [tool-name args]
  (if-let [tool (get-mcp-tool tool-name)]
    ((:handler tool) args)
    (throw (ex-info "MCP tool not found" {:tool tool-name :available (keys @mcp-tools)}))))

(defn refresh-mcp-tools
  "Refresh tools from all connected servers"
  []
  (reset! mcp-tools {})
  (doseq [[server-name client] @mcp-clients]
    (try
      (let [tools (client/get-mcp-tools-as-def-tools client)]
        (swap! mcp-tools merge tools)
        (println "[MCP] Refreshed" (count tools) "tools from" server-name))
      (catch Exception e
        (println "[MCP] Failed to refresh tools from" server-name ":" (.getMessage e)))))

  (count @mcp-tools))

(defn mcp-tool-info
  "Get detailed info about an MCP tool"
  [tool-name]
  (when-let [tool (get-mcp-tool tool-name)]
    {:name (:name tool)
     :description (:description tool)
     :parameters (:parameters tool)
     :mcp-tool (:mcp-tool tool)
     :schema (:original-schema tool)}))

; Configuration management
(def mcp-config (atom {}))

(defn load-mcp-config
  "Load MCP server configuration"
  [config-map]
  (reset! mcp-config config-map)
  (doseq [[server-name config] config-map]
    (let [endpoint (:endpoint config)
          headers (:headers config {})
          timeout (:timeout config 30000)]
      (add-mcp-server server-name endpoint
                      :headers headers
                      :timeout timeout))))

(defn get-mcp-status
  "Get status of all MCP connections"
  []
  {:servers (count @mcp-clients)
   :tools (count @mcp-tools)
   :server-list (list-mcp-servers)
   :tool-list (keys @mcp-tools)})
