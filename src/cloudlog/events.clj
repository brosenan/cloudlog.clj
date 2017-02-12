(ns cloudlog.events
  (:use [cloudlog.core]))

(defn emitter [rulefunc writers & {:keys [link] :or {link 0}}]
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
        :writers writers}))))

(defn multiplier [rulefunc index writers]
  (let [cont (loop [i index
                    func rulefunc]
               (if (> i 0)
                 (recur (dec i) (-> func meta :continuation))
                                        ; else
                 func))]
    (fn [rule-ev fact-ev]
      (let [rulefunc' (cont (cons (:key rule-ev) (:data rule-ev)))
            em (emitter (with-meta rulefunc' (meta cont)) writers :link index)]
        (em fact-ev)))))
