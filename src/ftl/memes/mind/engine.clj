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
   {:xt/id ::base-prompt
    :prompt "I am a mind. I am an agent in a mind. I am a member of a society of mind."}
   {:xt/id ::engine
    :mind/purpose "Model the ability to achieve complex goals. Be an intelligent mind."
    :description "The mind engine is a special member of the mind.
It is a Clojure program. The main namespace is ftl.memes.mind.engine.
It runs the mind-loop which in turn models thought, actions and mental contents of the mind.
The mind loop gives members of the mind the chance to think and act.
The mind engine will evaluate Clojure code and actions that the members submit to the mind-loop."}])


(defn mind-loop
  "
  Prompt all members for their current mentations. 
  "
  []

  )
