(ns cloudlog.events
  (:use [cloudlog.core]))

(defn emitter [rulefunc]
  (fn [event]
    (for [data (rulefunc (cons (:key event) (:data event)))]
      (merge
       (if (-> rulefunc meta :target-fact)
                    (merge event {:name (-> rulefunc meta :target-fact fact-table)})
                                        ; else
                    (merge event {:kind :rule
                                  :name (str (fact-table [rulefunc]) "!0")}))
       {:key (first data)
        :data (rest data)}))))
