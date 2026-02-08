(ns agente.core
  (:require
    [wkok.openai-clojure.api :as api]
    [agente.tools.blog :as blog]               ;; <- for blog/lookup-blog-posts
    [agente.llms :refer [use-llm]]             ;; <- for use-llm
    [clojure.string :as str]
    [agente.agents.core :refer [run-agent]]))    ;; <- for run-agent

(defn now [] (.toString (java.time.Instant/now)))

(def marketeer
  {:name "marketeer"
   :description "A marketing assistant that helps with blog posts and social media."
   :system-prompt "You are a helpful marketing assistant."
   :memory []
   :tools [blog/lookup-blog-posts]
   :llm (use-llm :gpt-4o)})


(def seo-agent
  {:name "seo"
   :description "An SEO expert that helps optimize content for search engines."
   :system-prompt "You are a helpful SEO assistant."
   :memory []
   :tools [] ;; Add any specific tools for SEO if needed
   :llm (use-llm :gpt-4o)})

(def default-agent-name "marketeer")

(def agents
  {"marketeer" marketeer
   "seo" seo-agent
   })

;; (defmulti handle-input (fn [source-type _context _agent _input] source-type))
(defmulti handle-input (fn [event] (:source event)))

; (defmethod handle-input :http [_context agent input]
;   (let [response (run-agent agent input)]
;     {:status 200
;      :body (str "Agent response: " response)}))
;
; Example HTTP request handling
;; { :source :http
;;   :agent marketeeer
;;   :input "Write a Tweet about our new consultation schedule" 
;;   :context { :request req }}
;
; (defmethod handle-input :websocket [_context agent input]
;   (send-msg! (:socket context) (run-agent agent input)))

; example of routing
; (apply handle-input (juxt :source :context :agent :input) event)

;; example of "listening" for events

(defmethod handle-input :cli [{:keys [agent input]}]
  (run-agent agent input))

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
  (let [files (extract-file-paths input)
        file-blobs (for [f files]
                     (str "\n--- File: " f " ---\n" (slurp f)))
        cleaned-input (reduce (fn [s f] (str/replace s (re-pattern (str "@" (java.util.regex.Pattern/quote f))) "")) input files)]
    (str (str/join "\n" file-blobs) "\n\n" (str/trim cleaned-input))))

(defn parse-agent-input [input]
  (if (re-matches #"@\w+ .+" input)
    (let [[_ agent-name msg] (re-matches #"@(\w+)\s+(.+)" input)]
      [(get agents agent-name) msg])
    [(get agents default-agent-name) input]))   

(defn chat-loop []
  (println "💬 Chat with your agents. Use @agent-name to direct messages. Type 'exit' to quit.")
  (loop []
    (print "\nYou> ")
    (flush)
    (let [input (read-line)]
      (cond
        (= input "exit")
        (println "👋 Goodbye!")

        (= input "@?")
        (do
          (println "\nAvailable agents:")
          (doseq [a (keys agents)] (println " - @" a))
          (recur))

        :else
        (let [[agent message] (parse-agent-input input)]
          (if agent
            (let [final-input (inject-file-context message)]
              (println "\n[debug] Injected prompt:")
              (println final-input) ;; or use `prn` for exact formatting
              (let [response (handle-input {:source :cli :agent agent :input final-input})]
                (println (str "\n🤖 [" (:name agent) "] " response))))
            (println "❌ Unknown or ambiguous agent. Try @?"))
          (recur))))))

(defn -main []
  (chat-loop))
