(ns agente.cli
  (:require
   [clojure.string :as str]
   [agente.engine :refer [handle-input]]
   [agente.agents.manager :as manager]
   [agente.agents.marketeer :as marketeer]
   [agente.agents.seo :as seo-agent])
  (:import [org.jline.terminal TerminalBuilder]
           [org.jline.reader LineReaderBuilder LineReader Completer Candidate]
           [org.jline.reader.impl.history DefaultHistory]
           [org.jline.reader.impl.completer StringsCompleter]
           [org.jline.reader UserInterruptException EndOfFileException]
           [java.io File]
           [java.nio.file Paths Files]))

(def default-agent-name "manager")

(defn get-agents []
  {"manager" (manager/get-manager)
   "marketeer" marketeer/marketeer
   "seo" seo-agent/seo-agent})

(defn agents [] (get-agents))

(defn parse-command [line]
  (cond
    (= line "@?") [:system :list]
    (re-matches #"@\w+ .+" line)
    (let [[_ agent-name input] (re-matches #"@(\w+)\s+(.+)" line)]
      [(keyword agent-name) input])
    :else [(keyword default-agent-name) line]))

(defn file-exists? [path]
  (let [f (java.io.File. path)]
    (and (.exists f) (.isFile f))))

(defn extract-file-paths [input]
  (->> (re-seq #"@([\w\.-]+)" input)
       (map second)
       (filter file-exists?)))

(defn inject-file-context [input]
  (let [files (let [extracted (extract-file-paths input)]
                (println "[debug] Matched file paths:" extracted)
                extracted)
        file-blobs (for [f files]
                     (str "\n--- File: " f " ---\n" (slurp f)))
        cleaned-input (reduce
                       (fn [s f]
                         (str/replace s
                                      (re-pattern (str "@" (java.util.regex.Pattern/quote f)))
                                      ""))
                       input
                       files)]
    (str (str/join "\n" file-blobs) "\n\n" (str/trim cleaned-input))))

(defn parse-agent-input [input]
  (if (re-matches #"@\w+ .+" input)
    (let [[_ agent-name msg] (re-matches #"@(\w+)\s+(.+)" input)]
      [(get (agents) agent-name) msg])
    [(get (agents) default-agent-name) input]))

(def ^:private line-reader-instance (atom nil))

(defn get-files-in-directory [dir]
  (try
    (let [dir-file (File. dir)]
      (when (.isDirectory dir-file)
        (->> (.listFiles dir-file)
             (filter #(.isFile %))
             (map #(.getName %))
             (take 50)))) ; Limit to 50 files for performance
    (catch Exception e [])))

(defn create-custom-completer []
  (reify Completer
    (complete [this reader line candidates]
      (let [buffer (.line line)
            cursor (.cursor line)]
        (when (str/starts-with? buffer "@")
          (let [at-part (subs buffer 1) ; Remove the @
                agent-names (keys (agents))
                agent-completions (map #(str "@" %) agent-names)
                special-completions ["@?" "exit"]
                current-dir-files (get-files-in-directory ".")
                file-completions (map #(str "@" %) current-dir-files)
                all-completions (concat agent-completions special-completions file-completions)]
            (doseq [completion all-completions]
              (when (str/starts-with? completion buffer)
                (.add candidates (Candidate. completion completion nil nil nil nil true))))))))))

(defn create-line-reader []
  (when-not @line-reader-instance
    (try
      (let [terminal (-> (TerminalBuilder/builder)
                         (.system true)
                         (.build))
            history-file (java.io.File. (System/getProperty "user.home") ".agente-history")
            completer (create-custom-completer)
            line-reader (-> (LineReaderBuilder/builder)
                            (.terminal terminal)
                            (.history (DefaultHistory.))
                            (.completer completer)
                            (.variable LineReader/HISTORY_FILE (.getAbsolutePath history-file))
                            (.build))]
        (reset! line-reader-instance line-reader))
      (catch Exception e
        (reset! line-reader-instance nil))))
  @line-reader-instance)

(defn enhanced-readline [prompt]
  (if-let [reader (create-line-reader)]
    (try
      (.readLine reader prompt)
      (catch UserInterruptException e
        :interrupted)
      (catch EndOfFileException e
        :eof))
    (do
      (print prompt)
      (flush)
      (read-line))))

(defn chat-loop []
  (println "Chat with your agents. Use @agent-name to direct messages. Type 'exit' to quit.")
  (loop []
    (let [input (enhanced-readline "\nYou> ")]
      (cond
        (= input :interrupted)
        (println "\nGoodbye!")

        (= input :eof)
        (println "\nGoodbye!")

        (nil? input)
        (println "\nGoodbye!")

        (= input "exit")
        (println "Goodbye!")

        (= input "@?")
        (do
          (println "\nAvailable agents:")
          (doseq [a (keys (agents))] (println " - @" a))
          (recur))

        :else
        (let [[agent message] (parse-agent-input input)]
          (if agent
            (let [final-input (inject-file-context message)]
              ;; (println "\n[debug] Injected prompt:")
              ;; (println final-input)
              (let [response (handle-input {:source :cli :agent agent :input final-input})]
                (println (str "\n[" (:name agent) "] "))
                (println response)))
            (println "Unknown or ambiguous agent. Try @?"))
          (recur))))))
