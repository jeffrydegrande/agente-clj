(ns agente.agents.seo
  (:require [agente.llms :refer [use-llm]]))

(def seo-agent
  {:name "seo"
   :description "An SEO expert that helps optimize content for search engines."
   :system-prompt "You are a senior content strategist specializing in technical blog posts. You write long-form SEO-optimized content tailored for developer audiences."
   :memory []
   :tools [] ;; Add any specific tools for SEO if needed
   :llm (use-llm :gpt-4o)})
