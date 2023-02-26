(ns ftl.memes.mind.patterns.openai
  (:require [wkok.openai-clojure.api]))


(comment
  (require '[clojure.tools.deps.alpha.repl :refer [add-libs]])
  (add-libs
   '{clojure-openai/clojure-openai {:git/sha "58191280e57f474865bdcd3b0218ecdf1151e30d"
                                    :git/url "https://github.com/wkok/openai-clojure.git"}}))
