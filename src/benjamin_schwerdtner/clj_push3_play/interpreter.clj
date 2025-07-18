(ns benjamin-schwerdtner.clj-push3-play.interpreter
  (:require
   [random-seed.core :refer [random *rng*]]
   [benjamin-schwerdtner.clj-push3-play.stack.pushstate :as stack]
   ;; [benjamin-schwerdtner.clj-push3-play.stack.state :as s-st]
   [benjamin-schwerdtner.clj-push3-play.prot :as prot]
   [benjamin-schwerdtner.clj-push3-play.instructions.interface :as
    instructions]
   [benjamin-schwerdtner.clj-push3-play.configuration.config :as config]))

;; -
;; https://faculty.hampshire.edu/lspector/push3-description.html
;; -

;; =============================
;; Push3
;; =============================

(defn typeof-item
  "Returns the type of the item, as a keyword."
  [item]
  (prot/m-typeof-item item))

(defn push-to-stack [state item]
  (stack/push state (typeof-item item) item))

(defn name? [item]
  (= :push/name (typeof-item item)))

(defn literal? [p]
  (not (#{:push/name :push/code} (typeof-item p))))

(defn handle-name
  "Handle a push identifier.
  "
  [state identifier]
  (->
   (if
       ;; When the name-quoted flag is set, a identifier is pushed onto the NAME stack
       (::name-quoted? state)
       (stack/push state :push/name identifier)

       (if-let [v (get state :bindings identifier)]
         ;; the bound value is pushed to the exec stack
         (stack/push state :push/exec v)
         ;; if the identifier was not encountered before, push it on the NAME stack
         (stack/push state :push/name identifier)))

   (assoc ::name-quoted? false)))

(instructions/register-instruction
 {:sym-name 'name_quote
  :in []
  :out :state
  :doc "The next name is pushed on the NAME stack, regardless of whether it is bound or not.
Presumably for redefinition."
  :f (fn [state] (assoc state ::name-quoted? true))})


;; using the Push EXEC stack for recursion, inside Push

(defn execute-load
  [state program]
  (stack/push state :push/exec program))

(defn execute-1
  [state]
  (if (empty? (-> state
                  :stacks
                  :push/exec))
    state
    (binding [*rng* (random (:push/random-seed state 0))]
      (let [item (stack/peek-item state :push/exec)
            state (stack/pop-item state :push/exec)
            ;; every exec pop call is +1 :push/num-executions
            state (update state :push/num-executions (fnil inc 0))
            instruction (instructions/instruction item)]
        (def state state)
        (cond (not= instruction :no-instruction)
              (instructions/execute-instruction state instruction)
              (name? item) (handle-name state item)
              (literal? item) (push-to-stack state item)
              ;; item is a list
              :else (reduce (fn [state p]
                              (stack/push state :push/exec p))
                            state
                            ;; reverse, so the first item in
                            ;; the list is the first exec
                            ;; instruction
                            (reverse item)))))))

(defn execute
  "Entry point for Push3 execution.

  `program`: A Push3 program. A list of Push3 instructions and literals.

  `state`: presumably from [[setup-state]].


  Opts:

  `max-executions`: Stop executions after this count of EXEC stack pops.


  ---------

  https://faculty.hampshire.edu/lspector/push3-description.html

  "


  ([state program] (execute state program {}))
  ([state program {:keys [max-executions]}]
   (let [state (execute-load state program)]
     (loop [state (assoc state :push/num-executions 0)]
       (if (or (when max-executions
                 (<= max-executions (:push/num-executions state)))
               (empty? (-> state
                           :stacks
                           :push/exec)))
         state
         (recur (execute-1 state)))))))


;; ---

(defn setup-state
  []
  (merge
   (config/defaults)
   {:bindings {}
    :instructions (instructions/all-instructions)
    :stacks
    {:push/boolean []
     :push/char []
     :push/clj-object []
     :push/code []
     :push/exec []
     :push/float []
     :push/integer []
     :push/name []
     :push/string []}}))




;; =============================================================



(comment
  (execute (setup-state) '(true false boolean_and))
  (=
   [(-> (execute (setup-state) '(true false boolean_and))
        :stacks :push/boolean
        peek)
    (-> (execute (setup-state) '(true true boolean_and))
        :stacks :push/boolean
        peek)
    (-> (execute (setup-state) '(true (true false boolean_and) boolean_and))
        :stacks :push/boolean
        peek)
    (-> (execute (setup-state) '(true (true true boolean_and) boolean_and))
        :stacks :push/boolean
        peek)]
   [false true false true])



  (->
   (execute
    (setup-state)
    '(2 2 integer_+))
   :stacks)
  (->
   (execute
    (setup-state)
    (list 3 :a 'exec_do*times))
   :stacks)

  (->
   (execute
    (setup-state)
    (list 3 :a 'code_do*times))
   :stacks)


  (->
   (execute
    (setup-state)
    (list 3 'code_quote (list 2) 'code_do*times))
   :stacks)

  (->
   (execute-1
    (setup-state)
    (list 3 'exec_do*times 2))
   :stacks)


  (->
   (execute-load (setup-state) (list 3 'exec_do*times 2))
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   ;; (execute-1)
   :stacks)

  (->
   (execute-load (setup-state) (list 2 'exec_do*times :a))
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   (execute-1)
   :stacks)

  (->
   (execute-load (setup-state) (list 2 'exec_do*times :a))
   (execute-1)
   :stacks)

  (map :stacks
       (take 2
             (reductions (fn [s _]
                           (let [s2 (execute-1 s)]
                             (if (= s2 s) (ensure-reduced s) s)))
                         (execute-load (setup-state)
                                       (list 2 'exec_do*times :a))
                         (range))))




  (map
   :stacks
   (reductions
    (fn [s _]
      (let [s2 (execute-1 s)]
        (if (= s2 s)
          (ensure-reduced s)
          s2)))
    (execute-load
     (setup-state)
     (list 2 'exec_do*times :a))
    (range)))

  (map
   :stacks
   (reductions
    (fn [s _]
      (let [s2 (execute-1 s)]
        (if (= s2 s)
          (ensure-reduced s)
          s2)))
    (execute-load
     (setup-state)
     '(
       code_quote
       code_frominteger
       0
       3
       code_do*range))
    (range)))
  (map
   :stacks
   (reductions
    (fn [s _]
      (let [s2 (execute-1 s)]
        (if (= s2 s)
          (ensure-reduced s)
          s2)))
    (execute-load
     (setup-state)
     '(3 exec_do*count :a))
    (range)))

  (map
   :stacks
   (reductions
    (fn [s _]
      (let [s2 (execute-1 s)]
        (if (= s2 s)
          (ensure-reduced s)
          s2)))
    (execute-load
     (setup-state)
     '(3 exec_do*count code_frominteger))
    (range)))

  (map
   :stacks
   (reductions
    (fn [s _]
      (let [s2 (execute-1 s)]
        (if (= s2 s)
          (ensure-reduced s)
          s2)))
    (execute-load
     (setup-state)
     '(3 exec_do*times code_frominteger))
    (range)))


  (map
   :stacks
   (reductions
    (fn [s _]
      (let [s2 (execute-1 s)]
        (if (= s2 s)
          (ensure-reduced s)
          s2)))
    (execute-load
     (setup-state)
     '(code_quote :a 3 code_do*times))
    (range)))

  (map
   :stacks
   (reductions
    (fn [s _]
      (let [s2 (execute-1 s)]
        (if (= s2 s)
          (ensure-reduced s)
          s2)))
    (execute-load
     (setup-state)
     '(code_quote code_frominteger 3 code_do*times))
    (range)))

  (map
   :stacks
   (reductions
    (fn [s _]
      (let [s2 (execute-1 s)]
        (if (= s2 s)
          (ensure-reduced s)
          s2)))
    (execute-load
     (setup-state)
     '(code_quote code_frominteger 3 code_do*count))
    (range)))

  (map
   :stacks
   (reductions
    (fn [s _]
      (let [s2 (execute-1 s)]
        (if (= s2 s)
          (ensure-reduced s)
          s2)))
    (execute-load
     (setup-state)
     '(code_quote code_frominteger 10 5 code_do*range))
    (range)))






















































  (map
   :stacks
   (reductions
    (fn [s _]
      (let [s2 (execute-1 s)]
        (if (= s2 s)
          (ensure-reduced s)
          s2)))
    (execute-load
     (setup-state)
     '(2 2 exec_y (integer_dup integer_*)))
    (range 25)))


  (time (last (take 20 (iterate #(*' % %) 65536))))


  )
