(ns agente.agents.general
  (:require [agente.llms :refer [use-llm]]))

(def general
  {:name "general"
   :description "A general-purpose agent that handles questions and tasks that don't require specialized expertise."
   :system-prompt "You are a helpful general-purpose assistant. Handle user requests that don't require specialized domain expertise. Provide clear, concise, and accurate information. If a request seems to require specific expertise (like cooking, marketing, technical domains), suggest that the user might benefit from a specialist agent instead."
   :memory []
   :tools []
   :llm (use-llm :gpt-4o)})
