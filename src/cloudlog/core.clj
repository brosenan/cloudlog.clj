(ns cloudlog.core
  (:require [pureclj.core :as pureclj]
            [clojure.core.logic :as logic]
            [clojure.set :as set]))

(declare generate-rule-func)

(defmulti propagate-symbols (fn [cond symbols] (first cond)) :default :no-bindings)
(defmethod propagate-symbols :no-bindings [cond symbols]
  symbols)

(defn binding-symbols [bindings cond]
  (pureclj/symbols (map bindings (range 0 (count bindings) 2))))

(defmethod propagate-symbols 'let [cond symbols]
  (set/union symbols (binding-symbols (second cond) cond)))

(defmethod propagate-symbols 'for [cond symbols]
  (set/union symbols (binding-symbols (second cond) cond)))

(defmulti process-conds (fn [conds symbols] (class (first conds))))

; fact
(defmethod process-conds  clojure.lang.IPersistentVector [conds symbols]
  (let [target (first conds)
        target-name (eval (first target))]
    (if (= (count conds) 1)
      (do ; Target fact
        (when (not= (namespace target-name) (str *ns*))
          (throw (Exception. (str "keyword " target-name " is not in the rule's namespace " *ns*))))
        [[(vec (rest target))] {:target-arity (count (rest target))}])
      ; Continuation
      (let [[func meta] (generate-rule-func (first conds) (rest conds) symbols)
            key (second target)
            params (vec (set/intersection symbols (pureclj/symbols func)))
            missing (set/difference (pureclj/symbols key) symbols)
            meta {:continuation (with-meta `(fn [[~'$key$ ~@params]] ~func) meta)}]
        (when-not (empty? missing)
          (throw (Exception. (str "variables " missing " are unbound in the key for " (first target)))))
        [`[[~key ~@params]] meta]))))

; guard
(defmethod process-conds  clojure.lang.ISeq [conds symbols]
  (let [cond (first conds)
        [body meta] (process-conds (rest conds) (propagate-symbols cond symbols))
        body (seq (concat cond [body]))]
    (if (= (first cond) 'for)
      [`(apply concat ~body)]
      [body meta])))

(defmacro norm-run* [vars goal]
  (let [run `(logic/run* ~vars ~goal)]
    (if (= (count vars) 1)
      `(let [~'$res$ ~run]
         (if (empty? ~'$res$)
           nil
           [~'$res$]))
      run)))

(defn generate-rule-func [source-fact conds ext-symbols]
  (let [symbols (set/difference (pureclj/symbols (rest source-fact)) ext-symbols)
        [body meta] (process-conds conds symbols)
        meta (merge meta {:source-fact [(first source-fact) (count (rest source-fact))]})
        vars (vec symbols)
        func `(fn [~'$input$]
                ~(if (empty? vars)
                                        ; No unbound variables
                   `(if (= ~'$input$ [~@(rest source-fact)])
                      ~body
                      [])
                   ; vars contains the unbound variables
                   `(let [~'$poss$ (norm-run* ~vars
                                              (logic/== ~'$input$ [~@(rest source-fact)]))]
                      (apply concat (for [~vars ~'$poss$] 
                                      ~body)))))]
    [func meta]))

(defmacro --> [rulename source-fact & conds]
  (let [[func meta] (generate-rule-func source-fact conds #{})]
    `(def ~rulename (with-meta ~func ~meta))))

(defmacro defrule [name args & body]
  `(--> ~name ~@body [~(keyword (str *ns*) (clojure.core/name name)) ~@args]))
