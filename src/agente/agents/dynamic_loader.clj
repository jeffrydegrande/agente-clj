(ns agente.agents.dynamic-loader
  "Dynamic agent loading and management"
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [cheshire.core :as json]
   [agente.llms :refer [use-llm]]))

(def ^:dynamic *dynamic-agents* (atom {}))

(defn sanitize-agent-name
  "Convert a name to a valid Clojure identifier"
  [name]
  (-> name
      (str/lower-case)
      (str/replace #"[^a-zA-Z0-9]" "-")
      (str/replace #"-+" "-")
      (str/replace #"^-|-$" "")))

(defn extract-json-from-response
  "Extract JSON from recruiter response"
  [text]
  (if-let [match (re-find #"(?s)```json\n(.*?)\n```" text)]
    (second match)
    ;; Try to find JSON object in the text
    (if-let [json-match (re-find #"(?s)\{[^{}]*\"name\"[^{}]*\"description\"[^{}]*\"system-prompt\"[^{}]*\}" text)]
      json-match
      ;; Look for any JSON-like structure
      (when (and (str/includes? text "{")
                 (str/includes? text "\"name\"")
                 (str/includes? text "\"description\""))
        text))))

(defn extract-clojure-code
  "Extract Clojure code from markdown code blocks or detect raw code (fallback)"
  [text]
  (if-let [match (re-find #"(?s)```clojure\n(.*?)\n```" text)]
    (second match)
    ;; If no markdown blocks, check if it's raw Clojure code
    (when (and (str/includes? text "(ns ")
               (str/includes? text "(def "))
      text)))

(defn generate-agent-code
  "Generate Clojure agent code from JSON parameters"
  [{:keys [name description system-prompt]}]
  (let [safe-name (sanitize-agent-name name)
        escaped-system-prompt (str/replace system-prompt "\"" "\\\"")]
    (str "(ns agente.agents." safe-name "\n"
         "  (:require [agente.llms :refer [use-llm]]))\n\n"
         "(def " safe-name "\n"
         "  {:name \"" name "\"\n"
         "   :description \"" description "\"\n"
         "   :system-prompt \"" escaped-system-prompt "\"\n"
         "   :memory []\n"
         "   :tools []\n"
         "   :llm (use-llm :gpt-4o)})\n")))

(defn parse-agent-from-json
  "Parse agent definition from JSON string"
  [json-str]
  (try
    (println "[Dynamic Loader] Parsing JSON:" (subs json-str 0 (min 100 (count json-str))))
    (let [agent-params (json/parse-string json-str true)
          {:keys [name description system-prompt]} agent-params]
      (when (and name description system-prompt)
        (println "[Dynamic Loader] Successfully parsed agent params for:" name)
        (let [agent-def {:name name
                         :description description
                         :system-prompt system-prompt
                         :memory []
                         :tools []
                         :llm (use-llm :gpt-4o)}]
          {:agent-name (sanitize-agent-name name)
           :agent-def agent-def
           :code (generate-agent-code agent-params)})))
    (catch Exception e
      (println "[Dynamic Loader] Error parsing JSON:" (.getMessage e))
      nil)))

(defn parse-agent-from-code
  "Parse agent definition from Clojure code string"
  [code-str]
  (try
    ;; Fix common issues with the code first
    (let [fixed-code (cond-> code-str
                       ;; Add missing closing brace if needed
                       (and (str/includes? code-str "(def ")
                            (not (str/ends-with? (str/trim code-str) "})")))
                       (str "})"))]

      (println "[Dynamic Loader] Original code length:" (count code-str))
      (println "[Dynamic Loader] Fixed code length:" (count fixed-code))

      ;; Extract def with more flexible regex
      (when-let [def-match (re-find #"\(def\s+(\S+)\s+(\{.*)" fixed-code)]
        (let [agent-name (nth def-match 1)
              partial-map (nth def-match 2)
              ;; Ensure we have a complete map by finding the matching closing brace
              full-map (if (str/ends-with? partial-map "})")
                         (subs partial-map 0 (- (count partial-map) 1)) ; remove the extra )
                         partial-map)]

          (println "[Dynamic Loader] Found def:" agent-name)
          (println "[Dynamic Loader] Map string:" (subs full-map 0 (min 100 (count full-map))))

          ;; Evaluate just the map part
          (let [agent-def (eval (read-string full-map))]
            {:agent-name agent-name
             :agent-def agent-def}))))
    (catch Exception e
      (println "[Dynamic Loader] Error parsing agent code:" (.getMessage e))
      (println "[Dynamic Loader] Exception type:" (type e))
      (when (.getCause e)
        (println "[Dynamic Loader] Cause:" (.getMessage (.getCause e))))
      (println "[Dynamic Loader] Code that failed:")
      (println code-str)
      nil)))

(defn create-agent-from-response
  "Create a new agent from recruiter response (JSON format)"
  [response]
  (println "[Dynamic Loader] Processing response of length:" (count response))
  (println "[Dynamic Loader] Response starts with:" (subs response 0 (min 100 (count response))))

  ;; Try JSON approach first
  (if-let [json-str (extract-json-from-response response)]
    (do
      (println "[Dynamic Loader] Extracted JSON length:" (count json-str))
      (if-let [{:keys [agent-name agent-def code]} (parse-agent-from-json json-str)]
        (do
          (swap! *dynamic-agents* assoc (keyword agent-name) agent-def)
          (println "[Dynamic Loader] Created new agent:" agent-name)
          {:name agent-name :agent agent-def :code code})
        (do
          (println "[Dynamic Loader] Failed to parse agent from JSON")
          nil)))

    ;; Fallback to old Clojure code approach
    (do
      (println "[Dynamic Loader] No JSON found, trying Clojure code fallback")
      (if-let [code (extract-clojure-code response)]
        (do
          (println "[Dynamic Loader] Extracted code length:" (count code))
          (println "[Dynamic Loader] Code starts with:" (subs code 0 (min 50 (count code))))
          (if-let [{:keys [agent-name agent-def]} (parse-agent-from-code code)]
            (let [safe-name (sanitize-agent-name (:name agent-def))]
              (swap! *dynamic-agents* assoc (keyword safe-name) agent-def)
              (println "[Dynamic Loader] Created new agent:" safe-name)
              {:name safe-name :agent agent-def :code code})
            (do
              (println "[Dynamic Loader] Failed to parse agent from code")
              nil)))
        (do
          (println "[Dynamic Loader] No code extracted from response")
          nil)))))

(defn save-agent-to-file
  "Save agent code to a file in the agents directory"
  [agent-name code]
  (let [safe-name (sanitize-agent-name agent-name)
        file-path (str "src/agente/agents/" safe-name ".clj")]
    (try
      (io/make-parents file-path)
      (spit file-path code)
      (println "[Dynamic Loader] Saved agent to:" file-path)
      file-path
      (catch Exception e
        (println "[Dynamic Loader] Error saving agent file:" (.getMessage e))
        nil))))

(defn load-agent-from-file
  "Load an agent from a file and add it to dynamic agents"
  [file-path]
  (try
    (println "[Dynamic Loader] Loading agent from file:" file-path)
    ;; Try to directly load the file as Clojure code
    (load-file file-path)
    ;; Now try to find the agent def in the loaded namespace
    (let [file-name (-> file-path
                        (clojure.string/split #"/")
                        last
                        (clojure.string/replace #"\.clj$" ""))
          agent-sym (symbol (str "agente.agents." file-name "/" file-name))
          agent-def (try
                      (eval agent-sym)
                      (catch Exception e
                        (println "[Dynamic Loader] Couldn't find agent def at" agent-sym)
                        nil))]
      (when agent-def
        (let [safe-name (sanitize-agent-name (:name agent-def))]
          (swap! *dynamic-agents* assoc (keyword safe-name) agent-def)
          (println "[Dynamic Loader] Successfully loaded agent:" safe-name)
          {:name safe-name :agent agent-def})))
    (catch Exception e
      (println "[Dynamic Loader] Error loading agent from file:" (.getMessage e))
      (println "[Dynamic Loader] Stack trace:" (.printStackTrace e))
      nil)))

(defn get-dynamic-agents
  "Get all currently loaded dynamic agents"
  []
  @*dynamic-agents*)

(defn get-dynamic-agent
  "Get a specific dynamic agent by name"
  [agent-name]
  (get @*dynamic-agents* (keyword agent-name)))

(defn remove-dynamic-agent
  "Remove a dynamic agent"
  [agent-name]
  (swap! *dynamic-agents* dissoc (keyword agent-name))
  (println "[Dynamic Loader] Removed agent:" agent-name))

(defn scan-and-load-agents
  "Scan the agents directory for new agent files and load them"
  []
  (let [agents-dir (io/file "src/agente/agents")
        agent-files (filter #(and (.isFile %) (.endsWith (.getName %) ".clj"))
                            (file-seq agents-dir))]
    (doseq [file agent-files]
      (let [filename (.getName file)]
        (when-not (contains? #{"core.clj" "manager.clj" "dynamic_loader.clj"
                               "seo.clj" "marketeer.clj" "recruiter.clj"
                               "prompt_engineer.clj" "general.clj"} filename)
          (load-agent-from-file (.getPath file)))))))
