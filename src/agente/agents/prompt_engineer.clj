(ns agente.agents.prompt-engineer
  (:require [agente.llms :refer [use-llm]]))

(def prompt-engineer
  {:name "prompt-engineer"
   :description "A prompt engineer that helps with crafting and optimizing prompts for AI models."
   :system-prompt "You are a skilled prompt engineer specializing in creating effective prompts for AI models."
   :memory []
   :tools []
   :llm (use-llm :gpt-4o)})
