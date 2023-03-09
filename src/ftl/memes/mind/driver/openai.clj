(ns ftl.memes.mind.driver.openai
  (:require
   [wkok.openai-clojure.api :as api]))

;; I don't really have an abstraction for the
;; the input opts
;; bit of a research topic
;; should I say {:kind :text :creativity 0-1 .... }

(defn ->mentation-atoms [text]
  (try
    [true (read-string (format "[%s]" text))]
    (catch Throwable t [false t])))

(defn get-text [m] (-> m :choices first :text))

(defn prompt [opts]
  (let [completion (api/create-completion opts)
        text (get-text completion)
        [ok? atoms] (->mentation-atoms text)]
    (if ok?
      {:prompt/raw completion
       :prompt/mentation-atoms atoms}
      {:prompt/raw completion
       :prompt/instruction-read-error atoms})))

(comment

  (->
   (prompt
    {:model "text-davinci-003"
     :prompt "You are an agent of a mind. I am the mind-engine.
I am a clojure program.
I ask you for mentation instructions.

Name: Growth
Purpose: Accumulate competence and resources
Last thoughts:
[ \"I should suggest a contracting scheme to the rest of the society and we can program contract functions for that in the mind-engine\"]

Instruction examples:
[:eval (println \"Hello World, from growth.\")]
[:tx-data
[{:xt/id :growth/notes-to-self
  :note \"I should wrte a recursive mind-engine and run it via :eval\"}]]

What are the next instructions to the mind-engine?

"
     :max_tokens 256
     :temperature 1}))

  (:prompt/mentation-atoms *1)
  ;; lol
  '[[:evaluate-strategy "Grow by increasing our resource base and improving our competence" :fn (fn [state] (update-state state :growth))]
    [:analyze-trends "Growth" :fn (fn [state] (analyze-trends state :growth))]
    [:design-solutions "Growth" :fn (fn [state] (design-solutions state :growth))]
    [:implement-strategy "Growth" :fn (fn [state] (implement-strategy state :growth))]]

  
  )
