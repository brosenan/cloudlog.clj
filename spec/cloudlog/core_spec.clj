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
          (it "defines a function that transforms its source to its target"
              (should= [[2 1]] (do-in-private-ns
                                 (--> foobar
                                     [:test/foo x y]
                                     [(read-string "::bar") y x])
                                 (foobar-0 [1 2]))))
          (it "attaches the name and arity of the source to the function"
              (should= [:test/foo 2] (do-in-private-ns
                                      (--> foobar
                                           [:test/foo x y]
                                           [(read-string "::bar") y x])
                                      ((meta foobar-0) :source-fact))))
          (it "allows clojure forms to be used as guards"
              (should= [[3]] (do-in-private-ns
                              (--> foobar
                                   [:test/foo X Y]
                                   (let [Z (+ X Y)])
                                   [(read-string "::bar") Z])
                              (foobar-0 [1 2])))))
