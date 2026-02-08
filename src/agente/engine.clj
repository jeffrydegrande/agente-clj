(ns agente.engine
  (:require [agente.agents.core :refer [run-agent]]))

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
  ;; Check if this is the manager agent and use strict delegation
  (if (= (:name agent) "manager")
    (let [run-manager (resolve 'agente.agents.manager/run-manager)]
      (run-manager input))
    (run-agent agent input)))

(defmethod handle-input :agent [{:keys [agent input]}]
  (println "[agent-call] Delegating to agent:" (:name agent))
  (println "[agent-call] With input:" input)
  ;; Check if this is the manager agent and use strict delegation
  (if (= (:name agent) "manager")
    (let [run-manager (resolve 'agente.agents.manager/run-manager)]
      (run-manager input))
    (run-agent agent input)))
