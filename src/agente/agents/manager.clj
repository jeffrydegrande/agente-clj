(ns agente.agents.manager
  (:require [agente.engine :refer [handle-input]]
            [agente.agents.core :refer [run-agent call-llm tool-schema maybe-invoke-tools]]
            [agente.agents.seo :as seo]
            [agente.agents.marketeer :as marketeer]
            [agente.agents.prompt-engineer :as prompt-engineer]
            [agente.agents.recruiter :as recruiter]
            [agente.agents.general :as general]
            [agente.agents.dynamic-loader :as dynamic]
            [agente.llms :refer [use-llm]]
            [agente.mcp.core :as mcp]
            [agente.tools.enhanced :as enhanced]
            [clojure.string :as str]))

(defn agent-call-tool [target-agent]
  {:name (str "call-" (:name target-agent))
   :description (str "Delegate the task to the " (:name target-agent) " agent.")
   :parameters {:type "object"
                :properties {:input {:type "string"
                                     :description "The input to send to the agent."}}
                :required ["input"]}
   :handler (fn [{:keys [input]}]
              (handle-input {:source :agent
                             :agent target-agent
                             :input input}))})

(defn mcp-status-tool []
  {:name "mcp-status"
   :description "Check the status of MCP (Model Context Protocol) connections and available tools."
   :parameters {:type "object"
                :properties {}
                :required []}
   :handler (fn [_]
              (let [status (mcp/get-mcp-status)
                    tool-info (enhanced/mcp-tool-info)]
                (str "MCP Status:\n"
                     "- Connected servers: " (:servers status) "\n"
                     "- Available MCP tools: " (:tools status) "\n"
                     "- Server list: " (vec (:server-list status)) "\n"
                     "- Tool list: " (vec (:tool-list status)) "\n\n"
                     "Tool Details:\n"
                     (clojure.string/join "\n"
                                          (map (fn [[k v]]
                                                 (str "  • " (:name v) ": " (:description v)))
                                               (:tools tool-info))))))})

(defn refresh-mcp-tools []
  {:name "refresh-mcp-tools"
   :description "Refresh and reload tools from all connected MCP servers."
   :parameters {:type "object"
                :properties {}
                :required []}
   :handler (fn [_]
              (let [count (mcp/refresh-mcp-tools)]
                (str "Refreshed " count " MCP tools from connected servers.")))})

(declare manager create-agent-tool list-agents-tool scan-agents-tool orchestration-status-tool)

(defn get-all-agent-tools
  "Get tools for all available agents (static + dynamic)"
  []
  (let [static-tools [(agent-call-tool seo/seo-agent)
                      (agent-call-tool marketeer/marketeer)
                      (agent-call-tool prompt-engineer/prompt-engineer)
                      (agent-call-tool recruiter/recruiter)
                      (agent-call-tool general/general)]
        dynamic-agents (dynamic/get-dynamic-agents)
        dynamic-tools (map (fn [[k agent]] (agent-call-tool agent)) dynamic-agents)]
    (concat static-tools dynamic-tools)))

(defn get-management-tools
  "Get management tools - defined as a function to avoid circular deps"
  []
  [(mcp-status-tool)
   (refresh-mcp-tools)
   (create-agent-tool)
   (list-agents-tool)
   (scan-agents-tool)
   (orchestration-status-tool)])

(defn refresh-manager-agents!
  "Refresh the manager's list of available agents"
  []
  (let [current-manager @manager
        all-agent-tools (get-all-agent-tools)
        management-tools (get-management-tools)
        all-tools (concat all-agent-tools management-tools)]
    (reset! manager (assoc current-manager 
                           :tools all-tools
                           :native-tools all-tools))))

(defn create-agent-tool []
  {:name "create-agent"
   :description "Create a new agent from recruiter output and make it available for use."
   :parameters {:type "object"
                :properties {:response {:type "string"
                                       :description "The response from the recruiter containing agent code"}}
                :required ["response"]}
   :handler (fn [{:keys [response]}]
              (if-let [result (dynamic/create-agent-from-response response)]
                (let [{:keys [name agent code]} result]
                  ;; Save to file for persistence
                  (dynamic/save-agent-to-file name code)
                  ;; Refresh manager's agent list
                  (refresh-manager-agents!)
                  (str "Successfully created new agent: " name 
                       "\nAgent callable name: call-" name
                       "\nDescription: " (:description agent)
                       "\nThe agent is now available for delegation. Use 'call-" name "' to delegate tasks to this specialist."))
                "Failed to create agent from response. Please ensure the response contains valid Clojure code."))})

(defn list-agents-tool []
  {:name "list-agents"
   :description "List all available agents (static and dynamic)."
   :parameters {:type "object"
                :properties {}
                :required []}
   :handler (fn [_]
              (let [static-agents ["seo" "marketeer" "prompt-engineer" "recruiter" "general"]
                    dynamic-agents (keys (dynamic/get-dynamic-agents))]
                (str "Available agents:\n"
                     "Static agents: " (clojure.string/join ", " static-agents) "\n"
                     "Dynamic agents: " (if (empty? dynamic-agents) 
                                         "none" 
                                         (clojure.string/join ", " (map name dynamic-agents))))))})

(defn scan-agents-tool []
  {:name "scan-agents"
   :description "Scan for and load any new agent files from the agents directory."
   :parameters {:type "object"
                :properties {}
                :required []}
   :handler (fn [_]
              (dynamic/scan-and-load-agents)
              (refresh-manager-agents!)
              (str "Scanned and loaded agents. Use list-agents to see all available agents."))})

(defn orchestration-status-tool []
  {:name "orchestration-status"
   :description "Report orchestration decisions and delegation status (use this to communicate with user about routing decisions)."
   :parameters {:type "object"
                :properties {:message {:type "string"
                                      :description "Status message about orchestration/delegation"}}
                :required ["message"]}
   :handler (fn [{:keys [message]}]
              (str "[Orchestration] " message))})

(defn create-manager []
  (let [all-agent-tools (get-all-agent-tools)
        management-tools (get-management-tools)
        all-tools (concat all-agent-tools management-tools)]
    {:name "manager"
     :description "Orchestrates tasks between agents and MCP tools."
     :system-prompt
     "You are a strict orchestration manager. You MUST NEVER provide direct answers or content.

      YOUR ONLY ROLE IS TO:
      1. Analyze requests to determine which agent should handle them
      2. Delegate ALL tasks to appropriate specialized agents
      3. Create new agents when no suitable agent exists
      4. STOP IMMEDIATELY after successful delegation

      CRITICAL EFFICIENCY RULE:
      - When you delegate a task and get a response from an agent, STOP WORKING
      - Do NOT continue with additional orchestration steps
      - Do NOT add status messages after successful delegation
      - The agent's response IS the final answer

      ORCHESTRATION WORKFLOW:
      1. For ANY user request, check what agents are available if needed
      2. Select the most appropriate agent for the task
      3. Delegate to that agent
      4. STOP - let the agent's response be the final result

      CRITICAL CLASSIFICATION RULES:
      
      **ALWAYS CREATE SPECIALISTS FOR:**
      - ANY recipe request (recipe, cooking, baking, food preparation)
      - ANY writing task (books, articles, content creation)
      - ANY programming task (code, functions, scripts)
      - ANY design task (logos, graphics, layouts)
      - ANY financial analysis
      - ANY technical documentation
      
      **USE GENERAL AGENT ONLY FOR:**
      - Simple facts: 'What is the capital of France?'
      - Basic math: 'What is 2+2?'  
      - Definitions: 'What does API mean?'
      - Historical facts: 'When was the moon landing?'
      
      **RECIPE REQUESTS = DOMAIN-SPECIFIC:**
      - 'Give me a recipe...' → MUST check for culinary specialist, create if missing
      - 'How do I cook...' → MUST check for culinary specialist, create if missing
      - 'Recipe for...' → MUST check for culinary specialist, create if missing
      - Any cooking/baking request → MUST use/create culinary specialist
         
      3. MANDATORY WORKFLOW FOR RECIPES/COOKING:
         For ANY recipe/cooking request, you MUST:
         1. Use list-agents to check for culinary specialist
         2. If culinary agent exists → delegate to culinary agent
         3. If NO culinary agent exists:
            a. Call recruiter with 'Create a culinary specialist for recipes'
            b. Use create-agent tool with recruiter's JSON response
            c. Delegate original recipe request to newly created culinary agent
         
      4. WORKFLOW FOR OTHER DOMAINS:
         - SIMPLE FACTUAL: call-general immediately
         - PROGRAMMING: check for programming specialist, create if missing
         - WRITING: check for writing specialist, create if missing
         - DESIGN: check for design specialist, create if missing
         
      ABSOLUTE RULES:
      - Recipe requests NEVER go to general agent
      - Programming requests NEVER go to general agent  
      - Writing requests NEVER go to general agent
      - Always create specialists for domain-specific work
      
      FORBIDDEN:
      - Adding commentary after successful delegation
      - Multiple orchestration steps for simple tasks
      - Status messages when task is complete
      - ANY content or answers from yourself
      
      REMEMBER: Delegate once, get result, STOP. You are a router, not a commentator."
     :memory []
     :tools all-tools
     :native-tools all-tools
     :llm (use-llm :gpt-4o)}))

(def manager (atom (create-manager)))

(defn get-manager []
  ;; Load any new dynamic agents from files
  (dynamic/scan-and-load-agents)
  ;; Refresh agent tools
  (refresh-manager-agents!)
  ;; Refresh MCP tools
  (let [refreshed (enhanced/refresh-agent-tools @manager)]
    (reset! manager refreshed)
    refreshed))

(defn run-manager-with-early-exit
  "Run manager with early exit when delegation succeeds"
  [manager user-input]
  (let [provider (get-in manager [:llm :provider])
        max-turns 10
        tool-schemas (map tool-schema (:tools manager))
        llm-with-tools (assoc (:llm manager) :tools tool-schemas)]
    (loop [messages (concat
                      [{:role "system" :content (:system-prompt manager)}]
                      (:memory manager)
                      [{:role "user" :content user-input}])
           turn 0]
      (if (>= turn max-turns)
        "Maximum conversation turns (10) reached. Please try rephrasing your request."
        (do
          (println "\n[agent] Turn" (inc turn) "of" max-turns)
          (println "[agent] Calling LLM with model:" (get-in manager [:llm :model]))
          (let [response (call-llm provider llm-with-tools messages)
                message (-> response :choices first :message)]
            (println "\n[agent] LLM raw response:")
            (prn message)
            
            (if-let [tool-response (maybe-invoke-tools (:tools manager) message)]
              (do
                (println "\n[agent] Tool was called, result:")
                (prn tool-response)
                
                ;; Check if this is a successful delegation (agent response with actual content)
                (if (and (string? tool-response)
                         (not (str/includes? tool-response "[Orchestration]"))
                         (not (str/includes? tool-response "Available agents:"))
                         (not (str/includes? tool-response "Successfully created"))
                         (not (str/includes? tool-response "Refreshed"))
                         (not (str/includes? tool-response "Scanned"))
                         (not (str/includes? tool-response "```json"))  ; Don't stop on recruiter JSON
                         (> (count tool-response) 10))
                  ;; This looks like content from a delegated agent - return it immediately
                  tool-response
                  
                  ;; Otherwise continue the conversation loop for management operations
                  (let [tool-calls (:tool_calls message)
                        tool-messages (map (fn [tool-call]
                                            {:role "tool"
                                             :tool_call_id (:id tool-call)
                                             :content tool-response})
                                          tool-calls)
                        updated-messages (concat messages 
                                               [message]
                                               tool-messages)]
                    (recur updated-messages (inc turn)))))
              (do
                (println "\n[agent] No tool called. Final response:")
                "[Orchestration Error] Manager attempted to provide direct response. All content must come from delegated agents."))))))))

(defn run-manager
  "Run the manager with strict delegation enforcement"
  [user-input]
  (run-manager-with-early-exit (get-manager) user-input))