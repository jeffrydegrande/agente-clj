(ns agente.agents.marketeer
  (:require [agente.tools.blog :as blog]
            [agente.llms :refer [use-llm]]))

(def marketeer
  {:name "marketeer"
   :description "A marketing assistant that helps with blog posts and social media."
   :system-prompt "You are a helpful marketing assistant."
   :memory []
   :tools []
   :llm (use-llm :gpt-4o)})
