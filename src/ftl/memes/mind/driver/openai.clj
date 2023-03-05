(ns ftl.memes.mind.driver.openai
  (:require
   [wkok.openai-clojure.api :as api]))

;; I don't really have an abstraction for the
;; the input opts
;; bit of a research topic
;; should I say {:kind :text .... }

;; (defn prompt-driver []
;;   (reify
;;     ftl.memes.mind.protocols/PromptDriver
;;     (prompt [this opts]
;;       (->
;;        (api/create-completion opts)
;;        :choices
;;        first
;;        :text))))

;; todo I want to give feeback to the prompt
(defn ->mentation-atoms [text]
  (try
    [true (read-string (format "[%s]" text))]
    (catch Throwable t [false t])))

(defn get-text [m] (-> m :choices first :text))

(defn prompt [opts]
  (let [completion (api/create-completion opts)
        text (get-text)
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
  :note \"I should write a recursive mind-engine and run it via :eval\"}]]

what are the next instructions to the mind-engine?

"
     :max_tokens 100
     :temperature 1}
    api/create-completion)
   get-text
   ->mentation-atoms)
  
  *1
  [[:db-store
    :growth/notes-to-self
    {:note "I should create a recursive mind-engine and run it via :eval, to further my potential",
     :xt/id :growth/notes-to-self}]
   [:recursion
    (println
     "Hello World, from growth's recursive mind-engine.")
    :eval]]


  
  
  (ftl.memes.mind.protocols/prompt
   (prompt-driver)
   {:model "text-davinci-003"
    :prompt "Say this is a test"
    :max_tokens 7
    :temperature 0})
  "\n\nThis is indeed a test"

  {:id "cmpl-6oXTCyrb3Y9FMtNuYH2AcnftzwIgR",
   :object "text_completion",
   :created 1677503078,
   :model "text-davinci-003",
   :choices [{:text "\n\nThis is indeed a test",
              :index 0,
              :logprobs nil,
              :finish_reason "length"}],
   :usage {:prompt_tokens 5,
           :completion_tokens 7,
           :total_tokens 12}})
