(ns cloudlog.core
  (:require [permacode.symbols :as symbols]
            [clojure.core.logic :as logic]
            [clojure.set :as set]
            [clojure.string :as string]))

(declare generate-rule-func)

(defmulti propagate-symbols (fn [cond symbols] (first cond)) :default :no-bindings)
(defmethod propagate-symbols :no-bindings [cond symbols]
  symbols)

(defn binding-symbols [bindings cond]
  (symbols/symbols (map bindings (range 0 (count bindings) 2))))

(defmethod propagate-symbols 'let [cond symbols]
  (set/union symbols (binding-symbols (second cond) cond)))

(defmethod propagate-symbols 'for [cond symbols]
  (set/union symbols (binding-symbols (second cond) cond)))

(defmulti process-conds (fn [conds symbols] (class (first conds))))

; fact
(defmethod process-conds  clojure.lang.IPersistentVector [conds symbols]
  (let [target (first conds)
        target-name (first target)]
    (if (= (count conds) 1)
      (do ; Target fact
        [[(vec (rest target))] {:target-fact [target-name (count (rest target))]}])
      ; Continuation
      (let [[func meta] (generate-rule-func (first conds) (rest conds) symbols)
            key (second target)
            params (vec (set/intersection symbols (symbols/symbols func)))
            missing (set/difference (symbols/symbols key) symbols)
            meta {:continuation (with-meta `(fn [[~'$key$ ~@params]] ~func) meta)}]
        (when-not (empty? missing)
          (throw (Exception. (str "variables " missing " are unbound in the key for " (first target)))))
        [`[[~key ~@params]] meta]))))

; guard
(defmethod process-conds  clojure.lang.ISeq [conds symbols]
  (let [cond (first conds)
        [body meta] (process-conds (rest conds) (propagate-symbols cond symbols))
        body (seq (concat cond [body]))
        meta (if (string/starts-with? (str (first cond)) "by")
               (assoc meta :checked true)
               meta)]
    (if (= (first cond) 'for)
      [`(apply concat ~body) meta]
      ; else
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
  (let [symbols (set/difference (symbols/symbols (rest source-fact)) ext-symbols)
        [body meta] (process-conds conds (set/union symbols ext-symbols))
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

(defn validate-rule [metadata]
  (loop [metadata metadata
         link 0]
    (when-not (:checked metadata)
      (throw (Exception. (str "Rule is insecure. Link " link " is not checked."))))
    (when (:continuation metadata)
      (recur (-> metadata :continuation meta) (inc link)))))

(defmacro defrule [rulename args source-fact & body]
  (let [conds (concat body [`[~(keyword (str *ns*) (name rulename)) ~@args]])
        [func meta] (generate-rule-func source-fact conds #{})]
    (validate-rule meta)
    `(def ~rulename (with-meta ~func ~(merge meta {:ns *ns* :name (str rulename)})))))

(defn append-to-keyword [keywd suffix]
  (keyword (namespace keywd) (str (name keywd) suffix)))

(defmacro defclause [clausename pred args-in args-out & body]
  (let [source-fact `[~(append-to-keyword pred "?") ~'$unique$ ~@args-in]
        conds (concat body [`[~(append-to-keyword pred "!") ~'$unique$ ~@args-out]])
        [func meta] (generate-rule-func source-fact conds #{})]
    `(def ~clausename (with-meta ~func ~(merge meta {:ns *ns* :name (str clausename)})))))

(defn with* [seq]
  (apply merge-with set/union
         (for [fact seq]
           (let [fact-name (first fact)
                 metadata (meta fact)
                 arity (-> fact rest count)]
             {[fact-name arity] #{(with-meta (vec (rest fact)) metadata)}}))))

(defn simulate* [rule factmap]
  (let [source-fact (-> rule meta :source-fact)
        input-set (factmap source-fact)
        after-first (into #{} (apply concat (map rule input-set)))
        cont (-> rule meta :continuation)]
    (if cont
      (let [next-rules (map cont after-first)]
        (into #{} (apply concat (for [next-rule next-rules]
                                  (simulate* (with-meta next-rule (meta cont)) factmap)))))
      ;else
      after-first)))

(defn simulate-with [rule & facts]
  (simulate* rule (with* facts)))

(defmulti fact-table (fn [[name arity]] (class name)))

(defmethod fact-table clojure.lang.Named [[name arity]]
  (str (namespace name) "/" (clojure.core/name name)))
(defmethod fact-table clojure.lang.IFn [[name arity]]
  (let [ns (-> name meta :ns)
        name (-> name meta :name)]
    (str ns "/" name)))
(prefer-method fact-table clojure.lang.Named clojure.lang.IFn)

(defmacro by [set body]
  `(when (contains? (-> ~'$input$ meta :writers) ~set)
     ~body))

(defmacro by-anyone [body]
  body)
