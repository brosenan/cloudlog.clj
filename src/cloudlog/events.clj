(ns cloudlog.events
  (:use [cloudlog.core])
  (:require [cloudlog.interset :as interset]))

(defn emitter [rulefunc writers & {:keys [link mult readers] :or {link 0
                                                                  mult 1
                                                                  readers interset/universe}}]
  (fn [event]
    (for [data (rulefunc (with-meta (cons (:key event) (:data event))
                           {:writers (:writers event)
                            :readers (:readers event)}))]
      (merge
       (if (-> rulefunc meta :target-fact)
                    (merge event {:name (-> rulefunc meta :target-fact fact-table)})
                                        ; else
                    (merge event {:kind :rule
                                  :name (str (fact-table [rulefunc]) "!" link)}))
       {:key (first data)
        :data (rest data)
        :writers writers
        :change (* (:change event) mult)
        :readers (interset/intersection (:readers event) readers)}))))

(defn multiplier [rulefunc index]
  (let [cont (loop [i index
                    func rulefunc]
               (if (> i 0)
                 (recur (dec i) (-> func meta :continuation))
                                        ; else
                 func))]
    (fn [rule-ev fact-ev]
      (let [rulefunc' (cont (cons (:key rule-ev) (:data rule-ev)))
            em (emitter (with-meta rulefunc' (meta cont)) (:writers rule-ev)
                        :link index
                        :mult (:change rule-ev)
                        :readers (:readers rule-ev))]
        (em fact-ev)))))
