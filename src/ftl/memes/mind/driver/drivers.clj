(ns ftl.memes.mind.driver.drivers
  (:require
   [ftl.memes.mind.protocols :as p]
   [ftl.memes.mind.driver.openai :as llm-driver]))

(defmulti prompt :prompt/driver)

(defmethod prompt :llm [opts]
  (llm-driver opts))

(defmethod prompt :human [opts] (println opts) [])

(comment
  (prompt {:prompt/driver :llm :foo :bar}))

