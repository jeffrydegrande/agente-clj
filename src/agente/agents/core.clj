(ns agente.agents.core
  (:require [wkok.openai-clojure.api :as api]
            [clojure.string :as str]
            [cheshire.core :as json]
            [agente.tools.blog :as blog]
            [agente.llms :refer [use-llm]]))

;; Multi-method to call the LLM based on the provider.
(defmulti call-llm (fn [provider _config _messages] provider))

;; Implementation for OpenAI provider.
(defmethod call-llm :openai [_ config messages]
  (println "Calling OpenAI API with config:" config)
  (api/create-chat-completion (merge config {:messages messages})))

(defn tool-schema [{:keys [name description parameters]}]
  {:type "function"
   :function {:name name
              :description description
              :parameters parameters}})

(defn extract-content [response]
  (->> response
       :choices
       (map :message)
       (filter #(= (:role %) "assistant"))
       (map :content)
       (str/join "\n")))

(defn maybe-invoke-tool [tools message]
  (if-let [tool-calls (:tool_calls message)]
    (some
     (fn [{:keys [function]}]
       (let [{:keys [name arguments]} function]
         (println "[tool] Function requested:" name)
         (println "[tool] Arguments JSON:" arguments)
         (let [tool (some #(when (= (:name %) name) %) tools)]
           (when tool
             (let [args (cheshire.core/parse-string arguments true)
                   result ((:handler tool) args)]
               (println "[agent] Tool result:" result)
               result)))))
     tool-calls)

    ;; Legacy fallback (single call)
    (when-let [{:keys [name arguments]} (:function_call message)]
      (println "[tool] Function requested:" name)
      (println "[tool] Arguments JSON:" arguments)
      (let [tool (some #(when (= (:name %) name) %) tools)]
        (when tool
          (let [args (cheshire.core/parse-string arguments true)
                result ((:handler tool) args)]
            (println "[agent] Tool result:" result)
            result))))))

(defn maybe-invoke-tools [tools message]
  (if-let [tool-calls (:tool_calls message)]
    (do
      (println "[tool] Tool calls requested:" (mapv #(get-in % [:function :name]) tool-calls))
      (->> tool-calls
           (map (fn [{:keys [function]}]
                  (let [{:keys [name arguments]} function
                        _ (println "[tool] Function requested:" name)
                        _ (println "[tool] Arguments JSON:" arguments)
                        tool (some #(when (= (:name %) name) %) tools)
                        args (-> arguments (json/parse-string true))]
                    (println "[tool] Parsed args:" args)
                    (if tool
                      (try
                        (let [result ((:handler tool) args)]
                          (if (:mcp-tool tool)
                            (do
                              (println "[tool] MCP tool result:" result)
                              result)
                            result))
                        (catch Exception e
                          (let [error-msg (str "Error calling " name ": " (.getMessage e))]
                            (println "[tool] Error:" error-msg)
                            error-msg)))
                      (str "⚠️ Unknown tool: " name)))))
           (str/join "\n")))
    nil))

(defn run-agent [{:keys [system-prompt memory llm tools]} user-input]
  (let [provider (:provider llm)
        max-turns 10
        tool-schemas (map tool-schema tools)
        llm-with-tools (assoc llm :tools tool-schemas)]
    (loop [messages (concat
                     [{:role "system" :content system-prompt}]
                     memory
                     [{:role "user" :content user-input}])
           turn 0]
      (if (>= turn max-turns)
        "Maximum conversation turns (10) reached. Please try rephrasing your request."
        (do
          (println "\n[agent] Turn" (inc turn) "of" max-turns)
          (println "[agent] Calling LLM with model:" (:model llm))
          (let [response (call-llm provider llm-with-tools messages)
                message (-> response :choices first :message)]
            (println "\n[agent] LLM raw response:")
            (prn message)
            (if-let [tool-response (maybe-invoke-tools tools message)]
              (do
                (println "\n[agent] Tool was called, result:")
                (prn tool-response)
                ;; Add assistant message and tool result to conversation
                (let [tool-calls (:tool_calls message)
                      tool-messages (map (fn [tool-call]
                                           {:role "tool"
                                            :tool_call_id (:id tool-call)
                                            :content tool-response})
                                         tool-calls)
                      updated-messages (concat messages
                                               [message]  ; Keep the assistant message with tool_calls
                                               tool-messages)]  ; Add tool response messages
                  (recur updated-messages (inc turn))))
              (do
                (println "\n[agent] No tool called. Final response:")
                (or (:content message) (extract-content response))))))))))
