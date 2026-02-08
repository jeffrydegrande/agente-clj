(ns agente.llms
  (:require
            [clojure.string :as str]
            [cheshire.core :as json]))


;; defines the models available for use.
;; At this point we're only using OpenAI models, but this could be extended
(def models
  {:gpt-3.5-turbo {:provider :openai
                   :model "gpt-3.5-turbo"
                   :temperature 0.7
                   :max-tokens 1500
                   :top-p 1.0
                   :frequency-penalty 0.0
                   :presence-penalty 0.0
                   :stop ["\n" "Human:" "AI:"]}

   :gpt-4o {:provider :openai
            :model "gpt-4o"
            :temperature 0.5
            :max-tokens 10000
            :top-p 1.0
            :frequency-penalty 0.0
            :presence-penalty 0.0
            }})
            ;;:stop ["\n" "Human:" "AI:"]}})

(defn use-llm
  ;; Utility function to pick a model and apply overrides.
  ([preset-key]
   (use-llm preset-key {}))
  ([preset-key overrides]
   (merge (get models preset-key) overrides)))
