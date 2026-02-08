(ns agente.tools.blog
  (:require [agente.tools.core :refer [def-tool]]))

(def-tool lookup-blog-posts
  {:description "Look up blog posts about a topic."
   :parameters {:type "object"
                :properties {:query {:type "string"
                                     :description "Search query"}}
                :required ["query"]}}
  [{:keys [query]}]
  (str "Pretending to look up blog posts about: " query))
