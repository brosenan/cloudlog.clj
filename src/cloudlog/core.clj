(ns cloudlog.core
  (:require [pureclj.core]))

(defmulti process-conds (fn [conds ] (class (first conds))))

(defmethod process-conds  clojure.lang.IPersistentVector [conds]
  (let [target (first conds)
        target-name (eval (first target))]
    (if (not= (namespace target-name) (str *ns*))
      (throw (Exception. (str "keyword " target-name " is not in the rule's namespace " *ns*)))
      [[(vec (rest target))] {:target-fact [(first target) 2]}])))

(defmethod process-conds  clojure.lang.ISeq [conds]
  (let [cond (first conds)
        [body meta] (process-conds (rest conds) )
        body (seq (concat cond [body]))]
    (if (= (first cond) 'for)
      [`(apply concat ~body)]
      [body meta])))

(defn generate-rule-func [rulename source-fact source-rule conds]
  (let [funcname (symbol (name rulename))
        [body meta] (process-conds conds)
        meta (merge meta {:source-fact [(first source-fact) (count (rest source-fact))]})]
    `(do (def ~funcname ~(with-meta
                           `(fn [[~@(rest source-fact)]]
                              ~body) meta)))))

(defmacro --> [rulename source-fact & conds]
  (generate-rule-func rulename source-fact nil conds))
