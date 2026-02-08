(ns agente.mcp.sse-transport
  "SSE transport implementation for MCP servers"
  (:require
   [clj-http.client :as http]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.core.async :as async]
   [agente.mcp.transport :as transport])
  (:import
   [java.io BufferedReader InputStreamReader]))

(defrecord SSETransport [endpoint headers timeout session-id message-endpoint response-channels sse-reader]
  transport/MCPTransport

  (connect [this endpoint]
    (println "[SSE Transport] Connecting to:" endpoint)

    ;; Establish ONE SSE connection and keep it open
    (try
      (let [response (http/get endpoint
                               {:headers (merge {"Accept" "text/event-stream"
                                                 "Cache-Control" "no-cache"}
                                                (:headers this))
                                :as :stream
                                :socket-timeout 300000  ; 5 minutes
                                :conn-timeout 5000
                                :throw-exceptions false})]

        (if (= 200 (:status response))
          (let [stream (:body response)
                reader (BufferedReader. (InputStreamReader. stream))
                response-channels (atom {})]

            ;; Read until we get session endpoint  
            (loop [line (.readLine reader)]
              (if (and line (str/starts-with? line "data: "))
                ;; Found session endpoint
                (let [session-endpoint (subs line 6)
                      session-id (last (str/split session-endpoint #"sessionId="))]
                  (println "[SSE Transport] Session established:" session-id)

                  ;; Start background SSE reader
                  (let [sse-reader-future
                        (future
                          (try
                            (println "[SSE Transport] Starting persistent SSE reader...")
                            (loop []
                              (let [continue?
                                    (try
                                      (when-let [line (.readLine reader)]
                                        ;; Handle SSE protocol - empty lines are keep-alives
                                        (cond
                                          (str/starts-with? line "data: ")
                                          (let [data (subs line 6)]
                                            (when (str/starts-with? data "{")
                                              (try
                                                (let [response (json/read-str data :key-fn keyword)
                                                      request-id (:id response)]
                                                  (println "[SSE Transport] Received response for ID:" request-id)
                                                  (when-let [chan (get @response-channels request-id)]
                                                    (async/>!! chan response)
                                                    (swap! response-channels dissoc request-id)))
                                                (catch Exception e
                                                  (println "[SSE Transport] Parse error:" (.getMessage e))))))
                                          
                                          (= line "")  ; SSE keep-alive
                                          nil  ; Ignore keep-alive messages
                                          
                                          :else
                                          (when (not= line "")
                                            (println "[SSE Transport] Unexpected line:" line)))
                                        true)  ; Continue reading
                                      (catch java.net.SocketTimeoutException e
                                        ;; This is expected for SSE - just means no data for a while
                                        (println "[SSE Transport] Read timeout - this is normal for SSE, continuing...")
                                        true)  ; Continue reading
                                      (catch java.io.IOException e
                                        (if (str/includes? (.getMessage e) "Stream closed")
                                          (do
                                            (println "[SSE Transport] Stream closed - stopping reader")
                                            false)  ; Stop reading
                                          (do
                                            (println "[SSE Transport] IO error:" (.getMessage e))
                                            true)))   ; Continue reading
                                      (catch Exception e
                                        (println "[SSE Transport] Unexpected error:" (.getMessage e))
                                        false))]  ; Stop on unexpected errors
                                (when continue?
                                  (recur))))
                            (catch Exception e
                              (println "[SSE Transport] Reader error:" (.getMessage e)))
                            (finally
                              (try (.close stream) (catch Exception _))
                              (println "[SSE Transport] SSE reader stopped"))))]

                    ;; Return connected transport
                    (assoc this
                           :endpoint endpoint
                           :connected true
                           :session-id session-id
                           :message-endpoint (str "https://supabase-n8n-mcp.jeffry-c76.workers.dev" session-endpoint)
                           :response-channels response-channels
                           :sse-reader sse-reader-future)))

                ;; Not session endpoint yet, keep reading
                (recur (.readLine reader)))))
          (throw (ex-info "Failed to establish SSE connection" {:status (:status response)}))))
      (catch Exception e
        (println "[SSE Transport] Connection error:" (.getMessage e))
        (throw e))))

  (send-request [this request]
    (when-not (:connected this)
      (throw (ex-info "Not connected" {})))

    (let [request-id (:id request)
          response-chan (async/chan 1)]

      (println "[SSE Transport] Sending" (:method request) "ID:" request-id)

      ;; Register response channel
      (swap! (:response-channels this) assoc request-id response-chan)

      ;; Send request via POST
      (let [post-response (http/post (:message-endpoint this)
                                     {:headers (merge {"Content-Type" "application/json"}
                                                      (:headers this))
                                      :body (json/write-str request)
                                      :timeout 5000
                                      :throw-exceptions false})]

        (println "[SSE Transport] POST status:" (:status post-response))

        (if (= 202 (:status post-response))
          ;; Wait for response
          (let [timeout-ch (async/timeout 15000)]
            (let [[response ch] (async/alts!! [response-chan timeout-ch])]
              (cond
                (= ch timeout-ch)
                (do
                  (swap! (:response-channels this) dissoc request-id)
                  {:status :error :message "Timeout waiting for response"})

                response
                {:status :success :body response}

                :else
                {:status :error :message "No response received"})))

          {:status :error :code (:status post-response) :body (:body post-response)}))))

  (send-notification [this notification]
    (transport/send-request this notification))

  (close [this]
    (when (:sse-reader this)
      (future-cancel (:sse-reader this)))
    (when (:response-channels this)
      (doseq [[_ chan] @(:response-channels this)]
        (async/close! chan)))
    (assoc this :connected false)))

(defn create-sse-transport
  [& [{:keys [headers timeout] :or {timeout 30000}}]]
  (->SSETransport nil headers timeout nil nil nil nil))
