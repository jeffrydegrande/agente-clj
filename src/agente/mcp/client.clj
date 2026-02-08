(ns agente.mcp.client
  "MCP client implementation for remote servers"
  (:require
   [agente.mcp.transport :as transport]
   [agente.mcp.sse-transport :as sse-transport]
   [clojure.string :as str]))

(defprotocol MCPClient
  "Protocol for MCP client operations"
  (initialize [this] "Initialize MCP session")
  (list-tools [this] "List available tools")
  (call-tool [this tool-name arguments] "Call a specific tool")
  (get-capabilities [this] "Get server capabilities"))

(defrecord RemoteMCPClient [transport endpoint request-id capabilities tools]
  MCPClient
  (initialize [this]
    (println "[MCP Client] Starting initialization...")
    (println "[MCP Client] Endpoint:" endpoint)

    (let [connected-transport (transport/connect transport endpoint)
          _ (println "[MCP Client] Transport connected")
          init-request (transport/json-rpc-request
                        "initialize"
                        {:protocolVersion "2024-11-05"
                         :capabilities {:tools {}}
                         :clientInfo {:name "agente-clj"
                                      :version "0.1.0"}}
                        (swap! request-id inc))
          _ (println "[MCP Client] Sending initialize request")
          response (transport/send-request connected-transport init-request)]

      (println "[MCP Client] Initialize response:" response)

      (if (= :success (:status response))
        (let [server-caps (get-in response [:body :result :capabilities])
              server-info (get-in response [:body :result :serverInfo])]
          (println "[MCP] Connected to:" (:name server-info) "v" (:version server-info))
          (assoc this
                 :transport connected-transport
                 :capabilities server-caps
                 :initialized true))
        (do
          (println "[MCP Client] Initialization failed with response:" response)
          (throw (ex-info "Failed to initialize MCP client" {:response response}))))))

  (list-tools [this]
    (when-not (:initialized this)
      (throw (ex-info "MCP client not initialized" {})))

    (let [request (transport/json-rpc-request
                   "tools/list"
                   {}
                   (swap! request-id inc))
          response (transport/send-request (:transport this) request)]

      (if (= :success (:status response))
        (let [tools-list (get-in response [:body :result :tools])]
          (reset! tools (into {} (map (fn [tool] [(:name tool) tool]) tools-list)))
          tools-list)
        (throw (ex-info "Failed to list tools" {:response response})))))

  (call-tool [this tool-name arguments]
    (when-not (:initialized this)
      (throw (ex-info "MCP client not initialized" {})))

    (let [request (transport/json-rpc-request
                   "tools/call"
                   {:name tool-name
                    :arguments arguments}
                   (swap! request-id inc))
          response (transport/send-request (:transport this) request)]

      (if (= :success (:status response))
        (get-in response [:body :result])
        (throw (ex-info "Tool call failed"
                        {:tool tool-name
                         :arguments arguments
                         :response response})))))

  (get-capabilities [this]
    (:capabilities this)))

(defn create-remote-client
  "Create a remote MCP client with appropriate transport"
  [endpoint & {:keys [headers timeout] :or {timeout 30000}}]
  (let [transport (if (or (str/includes? endpoint "/sse")
                          (str/includes? endpoint "event-stream"))
                    ;; Use SSE transport for SSE endpoints  
                    (sse-transport/create-sse-transport {:headers headers :timeout timeout})
                    ;; Use HTTP transport for others
                    (transport/create-http-transport {:headers headers :timeout timeout}))]
    (->RemoteMCPClient transport endpoint (atom 0) (atom {}) (atom {}))))

(defn connect-and-initialize
  "Connect to remote MCP server and initialize"
  [endpoint & options]
  (let [client (apply create-remote-client endpoint options)]
    (initialize client)))

(defn mcp-tool->def-tool-format
  "Convert MCP tool definition to our def-tool format"
  [mcp-tool]
  (let [tool-name (:name mcp-tool)
        description (:description mcp-tool)
        input-schema (:inputSchema mcp-tool)]

    {:name tool-name
     :description description
     :parameters (or input-schema 
                     {:type "object"
                      :properties {}
                      :required []})
     :mcp-tool true
     :original-schema mcp-tool}))

(defn wrap-mcp-tool-handler
  "Create a handler that calls the MCP tool"
  [client tool-name]
  (fn [args]
    (try
      (let [result (call-tool client tool-name args)]
        (if (:content result)
          (str/join "\n" (map :text (:content result)))
          (str result)))
      (catch Exception e
        (str "MCP tool error: " (.getMessage e))))))

(defn get-mcp-tools-as-def-tools
  "Get all MCP tools converted to def-tool format with handlers"
  [client]
  (try
    (let [mcp-tools (list-tools client)]
      (into {}
            (map (fn [mcp-tool]
                   (let [tool-def (mcp-tool->def-tool-format mcp-tool)
                         tool-name (keyword (:name mcp-tool))
                         handler (wrap-mcp-tool-handler client (:name mcp-tool))]
                     [tool-name (assoc tool-def :handler handler)]))
                 mcp-tools)))
    (catch Exception e
      (println "[MCP] Error getting tools:" (.getMessage e))
      {})))
