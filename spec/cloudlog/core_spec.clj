(ns cloudlog.core-spec
  (:require [speclj.core :refer :all]
            [cloudlog.core :refer :all]))

(defmacro do-in-private-ns [& cmds]
  `(let [~'myns (symbol (str "tmp" (rand-int 1000000)))
         ~'old-ns *ns*]
    (in-ns ~'myns)
    (use 'clojure.core)
    (use 'cloudlog.core)
    (try
      (do ~@cmds)
      (finally (in-ns (symbol (str ~'old-ns)))))))

(describe "(--> name source conds* dest)"
          (it "expands to a do block"
              (should= 'do (first (macroexpand '(--> foobar [:test/foo x y] [::bar y x])))))
          (it "defines a function that transforms its source to its target"
              (should= [[2 1]] (do-in-private-ns
                                 (--> foobar
                                     [:test/foo x y]
                                     [::bar y x])
                                 (foobar-0 [1 2])))))
