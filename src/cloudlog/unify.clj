(ns cloudlog.unify
  (:require [permacode.core]
            [clojure.walk :as walk]))

(permacode.core/pure
 (defn at-path [tree path]
   (if (empty? path)
     tree
     (recur (tree (first path)) (rest path))))

 (defmacro unify-fn [vars unifiable expr]
   `(fn [~unifiable] [~expr])))
