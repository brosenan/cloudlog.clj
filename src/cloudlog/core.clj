(ns cloudlog.core
  (:require [permacode.symbols :as symbols]
            [permacode.core]
            [clojure.set :as set]
            [clojure.string :as string]
            [cloudlog.unify :as unify]
            [cloudlog.graph :as graph]))

(permacode.core/pure
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
           (permacode.core/error "variables " missing " are unbound in the key for " (first target)))
         [`[[~key ~@params]] meta]))))

                                        ; guard
 (defmethod process-conds  clojure.lang.ISeq [conds symbols]
   (let [cond (first conds)
         [body meta] (process-conds (rest conds) (propagate-symbols cond symbols))
         body (seq (concat cond [body]))
         meta (if (string/starts-with? (name (first cond)) "by")
                (assoc meta :checked true)
                meta)]
     (if (= (first cond) 'for)
       [`(apply concat ~body) meta]
                                        ; else
       [body meta])))

 (defn fact-name [fact]
   (if (keyword? fact)
     fact
     ; else
     (if (symbol? fact)
       `(keyword (-> ~fact meta :ns str) (-> ~fact meta :name))
       ; else
       (keyword (-> fact meta :ns str) (-> fact meta name)))))
 
 (defn generate-rule-func [source-fact conds ext-symbols]
   (let [symbols (set/difference (symbols/symbols (rest source-fact)) ext-symbols)
         [body meta] (process-conds conds (set/union symbols ext-symbols))
         meta (merge meta {:source-fact [(fact-name (first source-fact)) (count (rest source-fact))]})
         vars (set symbols)
         term-has-vars (fn [term]
                         (not (empty? (set/intersection (symbols/symbols term) vars))))
         travmap (unify/traverse (vec (rest source-fact)) (constantly true))
         [conds bindings] (unify/conds-and-bindings (map identity travmap) term-has-vars)
         func `(fn [~'$input$]
                 (if (and ~@conds)
                   (let ~bindings ~body)
                                        ; else
                    []))]
     [func meta]))

 (defn validate-rule [metadata]
   (loop [metadata metadata
          link 0]
     (when-not (:checked metadata)
       (permacode.core/error "Rule is insecure. Link " link " is not checked."))
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
         (into #{} (reduce concat (for [next-rule next-rules]
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

 (defn rule-cont [rule]
   (loop [func rule
          res nil]
     (if-let [cont (-> func meta :continuation)]
       (recur cont (cons func res))
       ; else
       (reverse (cons func res)))))

 (defn rule-target-fact [rule]
   (let [conts (rule-cont rule)]
     (some identity (map (fn [x] (-> x meta :target-fact)) conts))))

 (defn sort-rules [rules]
   (let [inv-graph (reduce graph/merge-graph {} (for [rule rules]
                                                  (let [conts (rule-cont rule)]
                                                    {(rule-target-fact rule) (set (map (fn [x] (-> x meta :source-fact)) conts))})))
         inv-sort (graph/toposort inv-graph)
         target-fact-map (reduce merge {} (for [rule rules]
                                            (let [conts (rule-cont rule)]
                                              {(rule-target-fact rule) rule})))]
     (->> (reverse inv-sort)
          (map target-fact-map)
          (filter identity))))
 
 (defn simulate-rules-with [rules & facts]
   (loop [rules (sort-rules rules)
          facts (with* facts)]
     (if (empty? rules)
       facts
       ; else
       (recur (rest rules) (assoc facts (rule-target-fact (first rules)) (simulate* (first rules) facts)))))))
