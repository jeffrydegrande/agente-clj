(ns agente.mcp.transport
  "HTTP transport implementation for remote MCP servers"
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.string :as str]))

(defprotocol MCPTransport
  "Protocol for MCP transport implementations"
  (connect [this endpoint] "Connect to MCP server")
  (send-request [this request] "Send JSON-RPC request")
  (send-notification [this notification] "Send JSON-RPC notification")
  (close [this] "Close connection"))

(defrecord HTTPTransport [endpoint headers timeout]
  MCPTransport
  (connect [this endpoint]
    (assoc this :endpoint endpoint :connected true))
  
  (send-request [this request]
    (when-not (:connected this)
      (throw (ex-info "Not connected to MCP server" {:transport this})))
    
    (println "[MCP Transport] Sending request to" (:endpoint this))
    (println "[MCP Transport] Request:" (json/write-str request))
    
    (try
      (let [response (http/post (:endpoint this)
                                {:headers (merge {"Content-Type" "application/json"
                                                  "Accept" "application/json, text/event-stream"}
                                                 (:headers this))
                                 :body (json/write-str request)
                                 :timeout (or (:timeout this) 30000)
                                 :throw-exceptions false})]
        
        (println "[MCP Transport] Response status:" (:status response))
        (println "[MCP Transport] Response headers:" (:headers response))
        (println "[MCP Transport] Response body:" (:body response))
        
        (cond
          (= 202 (:status response))
          {:status :accepted :body nil}
          
          (and (>= (:status response) 200) (< (:status response) 300))
          (if-let [body-str (:body response)]
            (if (str/blank? body-str)
              {:status :success :body nil}
              (try
                (let [parsed-body (json/read-str body-str :key-fn keyword)]
                  (println "[MCP Transport] Parsed body:" parsed-body)
                  {:status :success :body parsed-body})
                (catch Exception e
                  (println "[MCP Transport] Could not parse JSON response:" body-str)
                  {:status :success :body body-str})))
            {:status :success :body nil})
          
          :else
          {:status :error :code (:status response) :body (:body response)}))
      (catch Exception e
        (println "[MCP Transport] Exception:" (.getMessage e))
        (.printStackTrace e)
        {:status :error :exception e})))
  
  (send-notification [this notification]
    (when-not (:connected this)
      (throw (ex-info "Not connected to MCP server" {:transport this})))
    
    (try
      (http/post (:endpoint this)
                 {:headers (merge {"Content-Type" "application/json"}
                                  (:headers this))
                  :body (json/write-str notification)
                  :timeout (or (:timeout this) 10000)
                  :throw-exceptions false})
      {:status :sent}
      (catch Exception e
        {:status :error :exception e})))
  
  (close [this]
    (assoc this :connected false)))

(defn create-http-transport
  "Create HTTP transport for MCP"
  ([] (create-http-transport {}))
  ([{:keys [headers timeout] :or {timeout 30000}}]
   (->HTTPTransport nil headers timeout)))

(defn json-rpc-request
  "Create JSON-RPC 2.0 request"
  [method params id]
  {:jsonrpc "2.0"
   :method method
   :params params
   :id id})

(defn json-rpc-notification
  "Create JSON-RPC 2.0 notification"
  [method params]
  {:jsonrpc "2.0"
   :method method
   :params params})

(defn json-rpc-response
  "Create JSON-RPC 2.0 response"
  [result id]
  {:jsonrpc "2.0"
   :result result
   :id id})

(defn json-rpc-error
  "Create JSON-RPC 2.0 error response"
  [error id]
  {:jsonrpc "2.0"
   :error error
   :id id})