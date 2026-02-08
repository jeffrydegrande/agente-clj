(ns agente.tools.agents
  (:require [agente.agents.seo :as seo]
            [agente.agents.marketeer :as marketeer]
            [agente.core :refer [handle-input]]
            [agent.tools.core :refer [def-tool]]))

(def-tool call-seo-agent
  {:description "Delegate task to the SEO agent."
   :parameters {:type "object"
                :properties {:input {:type "string"
                                     :description "The input to send to the SEO agent."}}}
                :required ["input"]}}
  [{:keys [input]}]
  (handle-input {:source :agent
                 :agent seo/seo-agent
                 :input input}))


(def-tool call-marketeer-agent
  {:description "Delegate task to the marketeer agent."
   :parameters {:type "object"
                :properties {:input {:type "string"
                                     :description "The input to send to the marketeer agent."}}}
                :required ["input"]}}
  [{:keys [input]}]
  (handle-input {:source :agent
                 :agent marketeer/marketeer
                 :input input}))
