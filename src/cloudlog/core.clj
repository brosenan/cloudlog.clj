(ns cloudlog.core
  (:require [pureclj.core :as pureclj]
            [clojure.core.logic :as logic]))

(defmulti process-conds (fn [conds ] (class (first conds))))

(defmethod process-conds  clojure.lang.IPersistentVector [conds]
  (let [target (first conds)
        target-name (eval (first target))]
    (if (= (count conds) 1)
      (if (not= (namespace target-name) (str *ns*))
        (throw (Exception. (str "keyword " target-name " is not in the rule's namespace " *ns*)))
        [[(vec (rest target))] {:target-fact [(first target) 2]}])
      [[] {:continuation `(fn [~'x] ~'x)}])))

(defmethod process-conds  clojure.lang.ISeq [conds]
  (let [cond (first conds)
        [body meta] (process-conds (rest conds) )
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

(defn generate-rule-func [source-fact conds]
  (let [[body meta] (process-conds conds)
        meta (merge meta {:source-fact [(first source-fact) (count (rest source-fact))]})
        vars (vec (pureclj/symbols (rest source-fact)))]
    (with-meta
      `(fn [~'$input$]
         (let [~'$poss$ (norm-run* ~vars
                         (logic/== ~'$input$ [~@(rest source-fact)]))]
           (apply concat (for [~vars ~'$poss$] 
                           ~body))))
      meta)))

(defmacro --> [rulename source-fact & conds]
    `(def ~rulename ~(generate-rule-func source-fact conds)))
