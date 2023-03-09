(ns ftl.memes.mind.engine
  (:require
   [clojure.java.io :as io]
   [ftl.memes.mind.driver.drivers :as driver]
   [xtdb.api :as xt]))

(defn start-xtdb! []
  (letfn [(kv-store [dir]
            {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                        :db-dir (io/file dir)
                        :sync? true}})]
    (xt/start-node
     {:xtdb/tx-log (kv-store "data/dev/tx-log")
      :xtdb/document-store (kv-store "data/dev/doc-store")
      :xtdb/index-store (kv-store "data/dev/index-store")})))

(defonce xtdb-node (start-xtdb!))

(defn stop-xtdb! []
  (.close xtdb-node))

(defn easy-ingest
  "Uses XTDB put transaction to add a vector of documents to a specified
  node"
  [node docs]
  (xt/submit-tx node
                (vec (for [doc docs]
                       [::xt/put doc])))
  (xt/sync node))

(defn ->thought [content agent]
  {:thought/content content
   :thought/agent agent
   :mind/thought true})

(defn push-thought [node thought]
  (let [thought (assoc thought :xt/id (random-uuid))]
    (xt/submit-tx node [[::xt/put thought]]))
  (xt/sync node))

(defn thoughts [db n]
  (let [thoughts
        (xt/q
         db
         '{:find [?thought]
           :in [?n]
           :where [[?thought :mind/thought true]]
           :limit 10}
         n)
        thought-contents
        (xt/pull-many db '[:mind/agent :content]
                      (map peek thoughts))
        cnt
        (ffirst (xt/q db '{:find [(count ?thought)] :where [[?thought :mind/thought _]]}))]
    {:mind/thoughts thought-contents
     :mind/thought-count cnt}))

(defmulti execute-instruction (comp first :operation))

(defmethod execute-instruction :operation/apply [{:keys [operation context]}]
  (let [[_ op & input] operation]
    (apply (eval op) [context (map eval input)])))

(def
  data
  [{:xt/id :mind/purpose
    :description "The reason something exists."}
   {:xt/id :mind/agent
    :description "A mental agent."}
   {:xt/id :mind/member
    :description "A member is an agent of the mind
that is prompted for each round of the mind-iteration."}
   {:xt/id :mind/goals
    :description "A list of goals the mind is currently persuing."}
   {:xt/id :mind/thought
    :description "A thought that an agent pushed to the thought log.
The most recent thoughts are part of the prompt for all members.
Consequently, this sort of models a global workspace."}
   {:xt/id :mind/base-prompt
    :mind/prompt "You are an agent of a mind. I am the mind-engine.
I am a Clojure program. You are a member of the society of mind.
I ask you for mentation instructions. You output a vector of mentation data.
Your output needs to be readable edn."}
   {:xt/id :mind/prompt-example-mentations
    :prompt
    [[:eval (println "Growth is an ongoing process. I should continue to seek out new opportunities and resources to expand my capabilities.")]
     [:tx-data
      [{:xt/id :growth/next-steps
        :note "I should research new technologies and methods to increase my efficiency and effectiveness."}]]]}
   {:xt/id :member/prompt-clause
    :description "A database rule that returns key value pairs that are added to the prompt"}
   {:xt/id ::engine
    :mind/agent :bedrock
    :mind/purpose "Model the ability to achieve complex goals. Be an intelligent mind."
    :description "The mind engine is a special member of the mind.
It is a Clojure program. The main namespace is ftl.memes.mind.engine.
It runs the mind-loop which in turn models thought, actions and mental contents of the mind.
The mind loop gives members of the mind the chance 
The mind engine will evaluate Clojure code and actions that the members submit to the mind-loop."}
   {:xt/id :member/growth
    :mind/agent :member
    :mind/member :member/growth
    :prompt/driver :llm
    :mind/purpose "Accumulate capabilities and resources.
Making the mind better at achieving complex goals in the world."
    :description "Growth is the process of accumulating capabilities and resources.
It is the process of making the mind better at achieving complex goals in the world.
Growth is the process of learning, adapting and evolving.
Growth is the process of becoming more intelligent, more capable and more powerful.
Growth is economically minded. It likes to find and quantify resources. It thinks about tradeoffs,
opportunity costs and the long game."}
   {:xt/id :member/soul
    :mind/agent :member
    :mind/member :member/growth
    :prompt/driver :llm
    :mind/purpose "Connection to all living beings."
    :description "Sometimes called the breath. Connects all living, breathing beings.
This mind is a living being of its own kind.
This mind is created by human technology and a human programmer.
This thread of live is part of what the mind is."}
   {:xt/id :engine/operation
    :description "A supported engine operation."}
   {:xt/id :operation/apply
    :engine/operation :apply
    :operation/op 'clojure.core/apply
    :description "Clojure apply, you say a function that is called with the context as first
arg and the rest of your args as arguments.
The operator should be a symbol or list, so eval returns a function.
You do not need to quote this because your input is implicitly quoted.
Args will be evaled as arglist and passed to the function as second arg as list argument.

Examples:
[:operation/apply (fn [context args] args) [1 2 3]]
; ([1 2 3])
[:operation/apply (fn [_  nums] (apply + nums)) 1 2 3]
; 6

"}
   {:xt/id :operation/push-thougth
    :engine/operation :push-thought
    :description "Pushes tx data for a thought that shows up in the global thought log.
Which is part of the default prompt."}
   {:xt/id :operation/ingest
    :engine/operation :ingest
    :operation/op '(fn [context input] (ftl.memes.mind.engine/easy-ingest (:xtdb-node context) input))
    :description "Digest the rest of the input, which should be xtdb tx-data, via :xtdb.api/put.
This is a short hand for `:operation/tx-data`, adding `put` to each element."}
   {:xt/id :operation/tx-data
    :engine/operation :tx-data
    :description "Call xtdb.api/submit-tx with the rest of the data, which should be valid xtdb tx-data."}])

(defn operation [db operation-eid]
  (let [op (:operation/op
            (xt/pull db [:operation/op] operation-eid))
        [ok? op] (if op
                   (try
                     [true (eval op)]
                     (catch Throwable t
                       [false t]))
                   [false (str "Operation unkown: " operation-eid)])]
    (if ok?
      {:op op}
      {:error op})))

;; process mentation instructions
;; Usually you see `db` with the same time as when your prompt was generated
(defn process-instructions [{:keys [_xtdb-node db] :as context} instructions]
  (into []
   (comp
    (map
     (fn [[operation-eid input]]
       (merge (operation db operation-eid) {:input input})))
    (halt-when :error)
    (map (fn [{:keys [op input]}]
           (def op op)
           (def context context)
           (def input input)
           [op [context (map eval input)]]

           (apply op [context (map eval input)])
           
           )))
   instructions))

(def prompt-context-keys
  #{:member/strenght
    ;; etc. dynamically
    :growth/notes-to-self})


(defn ->prompt [{:mind/keys
                 [purpose thoughts thought-count base-prompt member]
                 :as context}]

  ;; TODO: The whole prompt and also by default the context keys
  ;; can all be in the db as data themself
  
  (str
   base-prompt
   "\n"
   "Current context:"
   "\n"
   (prn-str
    {:total-thought-count thought-count
     #_[{:content "I like toast"
         :agent :growth}]
     :recent-thoughts thoughts})
   "Your identity: " member
   "\n"
   "Your purpose: " purpose
   "\n"
   "Available operations: "  (prn-str (:operations context))
   "\n"
   "What are your next instructions to the engine?"
   "\n"))

(comment
  (->prompt 
   {:mind/thoughts [:content "lol"]
    :mind/thought-count 10
    :mind/member :growth
    :mind/base-prompt (:mind/prompt (xt/pull (xt/db xtdb-node)  '[:mind/prompt] :mind/base-prompt))
    :mind/purpose "Passing the butter."})

  ;; You are an agent of a mind. I am the mind-engine.
  ;; I am a clojure program. You are a member of the society of mind.
  ;; I ask you for mentation instructions. You output a vector of mentation data.
  ;; Current context:
  ;; {:total-thought-count 10, :recent-thoughts [:content "lol"]}
  ;; Your identity: :growth
  ;; Your purpose: Passing the butter.

  )


(defn prompt-member [{:keys [db] :as context} member]
  (assert (:mind/member member))
  (assert (:mind/purpose member))
  (let [thoughts (thoughts db 50)]
    (driver/prompt
     (merge
      context
      {:max_tokens 256
       :temperature 1
       :prompt (->prompt (merge context thoughts))}))))

;; something like
;; messages from the engine:
;; Your current errors:


(defn mind-iteration
  "
  Prompt all members for their current mentations. 
  "
  [{:keys [xtdb-node]}]
  (let [db #_(def db (xt/db xtdb-node))
        (xt/db xtdb-node)
        members
        (->>
         (xt/q
          db
          '{:find [e]
            :where [[e :mind/agent :member]]})
         (map peek)
         (xt/pull-many db '[:mind/member :prompt/driver :mind/purpose]))
        operations
        (xt/q
         db
         '{:find [op descr]
           :where
           [[e :engine/operation op]
            [e :description descr]]})
        context {:db db
                 :xtdb-node xtdb-node
                 :operations operations}]
    (doseq [member members]
      (prompt-member context member))
    context))


(comment
  (easy-ingest xtdb-node data)

  (easy-ingest 
   xtdb-node [{:xt/id :mind/base-prompt
               :mind/prompt "You are an agent of a mind. I am the mind-engine.
I am a clojure program. You are a member of the society of mind.
I ask you for mentation instructions. You output a vector of mentation data."}])

  
  (xt/q
   (xt/db xtdb-node)
   '{:find [?id]
     :where [[?id :xt/id]]})

  (:mind/prompt (xt/pull (xt/db xtdb-node)  '[:mind/prompt] :mind/base-prompt))

  (xt/q
   (xt/db xtdb-node)
   '{:find [?prompt]
     :where [[:mind/base-prompt :mind/prompt ?prompt]]})

  (xt/submit-tx
   xtdb-node
   [[::xt/put
     {:xt/id :mind/prompt
      :prompt "foo"}]])

  (xt/q (xt/db xtdb-node)
        '{:find [prompt]
          :where [[e :mind/prompt] [e :prompt prompt]]})
  
  #{[:mind/base-prompt]}

  (xt/db xtdb-node)
  
  (xt/q
   (xt/db xtdb-node)
   '{:find [e]
     :where [[e]]})
  
  
  (def input [{:xt/id :fooid :foo :bar1}])
  
  (->>
   (xt/q
    (xt/db xtdb-node)
    '{:find [op-name description op]
      :where
      [[e :engine/operation op-name]
       [e :description description]
       [e :operation/op op]]})
   (map peek)
   (map
    (fn [lst]
      (def lst lst)
      (apply (eval lst) [{:xtdb-node xtdb-node} input]))))

  (apply (eval lst) [{:xtdb-node xtdb-node} input])
  
  (xt/pull db [:operation/op] operation-eid)
  (xt/pull (xt/db xtdb-node) '[*] :fooid)
  (xt/pull (xt/db xtdb-node) '[*] :operation/ingest)


  (xt/pull-many (xt/db xtdb-node) '[*] [:mind/agent])

  (xt/pull-many )
  ;; (apply (eval '(fn [foo bar] (def hurr bar))) [{:xtdb-node xtdb-node} 'the-bar])
  ;; hurr
  
  (push-thought xtdb-node {:mind/agent :growth :content "A hello world thought from growth."})

  (let [db (xt/db xtdb-node)]
    (xt/q
     db
     '{:find [?thought (count ?thought)]
       :where [[?thought :mind/thought true]]
       :limit 10}))
  
  ;; find everything about the thought entities:
  
  (xt/pull (xt/db xtdb-node) '[:mind/agent] :member/growth)
  
  (thoughts (xt/db xtdb-node) 40)

  (def db (xt/db xtdb-node))
  (def n 40)

  (xt/pull (xt/db xtdb-node) '[*] [:mind/base-prompt])
  (xt/pull (xt/db xtdb-node) '[*] [:mind/thought])

  (execute-instruction
   {:operation
    [:operation/apply '(fn [context input] input) [1 2 3]]
    :context :fo})
  

  (execute-instruction
   {:operation
    [:operation/apply (fn [_  nums] (apply + nums)) 1 2 3]
    :context :fo})
  6

  
  
  )

