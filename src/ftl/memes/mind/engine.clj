(ns ftl.memes.mind.engine
  (:require
   [clojure.java.io :as io]
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

(def
  data
  [{:xt/id :mind/purpose
    :description "The reason something exists."}
   {:xt/id :mind/agent
    :description "A mental agent."}
   {:xt/id :mind/goals
    :description "A list of goals the mind is currently persuing."}
   {:xt/id :mind/thought
    :description "A thought that an agent pushed to the thought log.
The most recent thoughts are part of the prompt for all members.
Consequently, this sort of models a global workspace."}
   {:xt/id :mind/base-prompt
    :prompt "You are an agent of a mind. I am the mind-engine.
I am a clojure program. You are a member of the society of mind.
I ask you for mentation instructions. You output a vector of mentation data."}
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
    ;; :operation/op (fn [context [op & input]] (clojure.core/apply op input))
    :description "Clojure apply, you say a function that is called with the context as first
arg and the rest of your input as arguments.
Example:
[:apply (fn [context input] (xt/submit-tx (:xtdb-node context) input)) [[::xt/put {:hello :foo}]]]
"}
   {:xt/id :operation/ingest
    :engine/operation :ingest
    :operation/op  '(fn [context input] (ftl.memes.mind.engine/easy-ingest (:xtdb-node context) input))
    :description "Digest the rest of the input, which should be xtdb tx-data, via :xtdb.api/put.
This is a short hand for `:operation/tx-data`, adding `put` to each element."}
   {:xt/id :operation/tx-data
    :engine/operation :tx-data
    :description "Call xtdb.api/submit-tx with the rest of the data, which should be valid xtdb tx-data."}])

(defn operation [db operation-eid]
  (if-let [m (xt/pull db [:operation/op] operation-eid)]
    (eval (:operation/op m))
    [false (str "Operation unkown: " operation-eid)]))

;; process mentation instructions
;; Usually you see `db` with the same time as when your prompt was generated
(defn process-instructions [{:keys [_xtdb-node db] :as context} instructions]
  (doall
   (sequence
    (map
     (fn [[operation-eid input]]
       [(operation db operation-eid) input]))
    (map (fn [[f input]] (apply f [context input])))
    instructions)))

(defn prompt-member [context member]
  (p/prompt))

;; something like
;; messages from the engine:
;; Your current errors:


(defn mind-loop
  "
  Prompt all members for their current mentations. 
  "
  []
  (let [db (xt/db xtdb-node)
        prompts
        (( xt/q)
         db
         '{:find [purpose e]
           :where [[e :mind/agent :member]
                   [e :mind/purpose purpose]]})]
    (doseq [prompt prompts]
      ()
      )))

(comment
  (easy-ingest xtdb-node data)
  
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
  
  

  )
