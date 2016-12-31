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

(defmacro rs [string]
  (read-string string))

(describe "(--> name source conds* dest)"
          (it "defines a function that transforms its source to its target"
              (should== [[2 1]] (do-in-private-ns
                                 (--> foobar
                                     [:test/foo x y]
                                     [(rs "::bar") y x]) ; we use (read-string) to ensure ::bar gets the test namespace
                                 (foobar [1 2]))))
          (it "attaches the name and arity of the source to the function"
              (should= [:test/foo 2] (do-in-private-ns
                                      (--> foobar
                                           [:test/foo x y]
                                           [(rs "::bar") y x])
                                      ((meta foobar) :source-fact))))
          (it "allows clojure forms to be used as guards"
              (should== [[3]] (do-in-private-ns
                              (--> foobar
                                   [:test/foo x y]
                                   (let [z (+ x y)])
                                   [(rs "::bar") z])
                              (foobar [1 2]))))
          (it "allows iteration using for guards"
              (should== ["hello" "world"] (do-in-private-ns
                                             (def stop-words #{"a" "is" "to" "the"})
                                             (--> index
                                                  [:test/doc text]
                                                  (for [word (clojure.string/split text #"[,!.? ]+")])
                                                  (let [word (clojure.string/lower-case word)])
                                                  (when-not (contains? stop-words word))
                                                  [(rs "::index") word text])
                                             ; Extract the keys from the index
                                             (map first (index ["Hello, to the  World!"])))))
          (it "disallows output that is not a keyword in the current namespace"
              (should-throw Exception "keyword :test/bar is not in the rule's namespace cloudlog.core-spec"
                            (macroexpand '(--> foobar
                                               [:test/foo X Y]
                                               [:test/bar Y X]))))
          (it "attaches the name of the output fact as metadata"
              (should= ["bar" 2] (do-in-private-ns
                                  (--> foobar
                                       [:test/foo X Y]
                                       (let [Z (+ X Y)])
                                       [(rs "::bar") Z])
                                  (let [m (meta foobar)
                                        [n arity] (m :target-fact)]
                                    [(name n) arity]))))
          (it "returns an empty result when the argument does not match the source"
              (should-be empty? (do-in-private-ns
                                 (--> foobar
                                      [:test/foo 1 X]
                                      [(rs "::bar") X 1])
                                 (foobar [2 3]))))
          (it "attaches a continuation function in case of a fact condition"
              (should-be fn? (do-in-private-ns
                               (--> foobar
                                    [:test/foo X Y]
                                    [:test/bar Y X]
                                    [(rs "::baz") Y X])
                               ((meta foobar) :continuation))))
          (it "returns input acceptable by the continuation"
              (do-in-private-ns
               (--> foobar
                    [:test/foo X Y]
                    [:test/bar Y X]
                    [(rs "::baz") Y X])
               (let [cont ((meta foobar) :continuation)
                     cont-val (foobar [1 2])]
                 (cont cont-val)))))
