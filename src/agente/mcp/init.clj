(ns agente.mcp.init
  "MCP initialization and configuration loading"
  (:require
    [agente.mcp.core :as mcp]
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(defn- load-edn-file
  "Load and parse an EDN file, returning nil if not found or on error"
  [path]
  (try
    (when (.exists (io/file path))
      (edn/read-string (slurp path)))
    (catch Exception e
      (println "[MCP Init] Error loading" path ":" (.getMessage e))
      nil)))

(defn load-mcp-config-file
  "Load MCP configuration from file, merging in secrets (headers, auth) from secrets.edn"
  [config-file]
  (let [config  (or (load-edn-file config-file) {})
        secrets (:mcp (load-edn-file "secrets.edn"))]
    (when (seq config)
      (println "[MCP Init] Loading configuration from" config-file))
    (if secrets
      (merge-with merge config secrets)
      config)))

(defn initialize-mcp-servers
  "Initialize MCP servers from configuration"
  ([]
   (initialize-mcp-servers "mcp-config.edn"))
  ([config-file]
   (let [config (load-mcp-config-file config-file)]
     (if (empty? config)
       (println "[MCP Init] No MCP servers configured")
       (do
         (println "[MCP Init] Initializing" (count config) "MCP servers...")
         (mcp/load-mcp-config config)
         (let [status (mcp/get-mcp-status)]
           (println "[MCP Init] Successfully connected to" (:servers status) "servers")
           (println "[MCP Init] Total MCP tools available:" (:tools status))
           status))))))

(defn auto-initialize-mcp
  "Auto-initialize MCP if config file exists"
  []
  (let [config-file "mcp-config.edn"]
    (if (.exists (io/file config-file))
      (initialize-mcp-servers config-file)
      (println "[MCP Init] No MCP configuration file found (" config-file "). MCP features disabled."))))

(defn add-mcp-server-interactive
  "Add an MCP server interactively"
  [server-name endpoint & {:keys [headers timeout] :or {timeout 30000}}]
  (println "[MCP Init] Adding server" server-name "at" endpoint)
  (let [result (mcp/add-mcp-server server-name endpoint 
                                   :headers headers 
                                   :timeout timeout)]
    (if (= :success (:status result))
      (do
        (println "[MCP Init] Successfully added" server-name)
        (println "[MCP Init] Available tools:" (:tools result))
        result)
      (do
        (println "[MCP Init] Failed to add" server-name ":" (:error result))
        result))))

(defn demo-mcp-setup
  "Set up a demo MCP configuration for testing"
  []
  (println "[MCP Demo] Setting up demo MCP servers...")
  
  ;; This is just an example - these servers don't actually exist
  ;; Users would replace these with real MCP server endpoints
  
  (println "[MCP Demo] No demo servers configured.")
  (println "[MCP Demo] To add MCP servers, either:")
  (println "[MCP Demo] 1. Edit mcp-config.edn with your server details")
  (println "[MCP Demo] 2. Use (add-mcp-server-interactive \"name\" \"endpoint\")")
  
  {:demo-mode true
   :message "Add real MCP servers to mcp-config.edn"})