(ns cloudlog.core
  (:require [pureclj.core :as pureclj]
            [clojure.core.logic :as logic]
            [clojure.set :as set]))

(declare generate-rule-func)

(defmulti process-conds (fn [conds symbols] (class (first conds))))

(defmethod process-conds  clojure.lang.IPersistentVector [conds symbols]
  (let [target (first conds)
        target-name (eval (first target))]
    (if (= (count conds) 1)
      (if (not= (namespace target-name) (str *ns*))
        (throw (Exception. (str "keyword " target-name " is not in the rule's namespace " *ns*)))
        [[(vec (rest target))] {:target-fact [(first target) 2]}])
      (let [[func meta] (generate-rule-func (first conds) (rest conds))
            params (vec (set/intersection symbols (pureclj/symbols func)))
            key (second target)
            missing (set/difference (pureclj/symbols key) symbols)
            meta {:continuation (with-meta `(fn [[~'$key$ ~@params]] ~func) meta)}]
        (when-not (empty? missing)
          (throw (Exception. (str "variables " missing " are unbound in the key for " (first target)))))
        [`[~key ~@params] meta]))))

(defmethod process-conds  clojure.lang.ISeq [conds symbols]
  (let [cond (first conds)
        [body meta] (process-conds (rest conds) symbols)
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
  (let [symbols (pureclj/symbols (rest source-fact))
        [body meta] (process-conds conds symbols)
        meta (merge meta {:source-fact [(first source-fact) (count (rest source-fact))]})
        vars (vec symbols)
        func `(fn [~'$input$]
                (let [~'$poss$ (norm-run* ~vars
                                          (logic/== ~'$input$ [~@(rest source-fact)]))]
                  (apply concat (for [~vars ~'$poss$] 
                                  ~body))))]
    [func meta]))

(defmacro --> [rulename source-fact & conds]
  (let [[func meta] (generate-rule-func source-fact conds)]
    `(def ~rulename (with-meta ~func ~meta))))
