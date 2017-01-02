(ns cloudlog.core-spec
  (:require [speclj.core :refer :all]
            [cloudlog.core :refer :all]))

(defmacro do-in-private-ns [& cmds]
  (let [[new-ns cmds] (if (string? (first cmds))
                        [(first cmds) (rest cmds)]
                        [(str "tmp" (rand-int 1000000)) cmds])
        old-ns *ns*]
    `(do
       (in-ns '~(symbol new-ns))
       (use 'clojure.core)
       (use 'cloudlog.core)
       (try
         (do ~@cmds)
         (finally (in-ns '~(symbol (str old-ns))))))))

(defn rs [string]
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
                                             ; extract the keys from the index
                                             (map first (index ["hello, to the  world!"])))))
          (it "disallows output that is not a keyword in the current namespace"
              (should-throw Exception "keyword :test/bar is not in the rule's namespace some-ns"
                            (do-in-private-ns "some-ns"
                                              (macroexpand '(--> foobar
                                                                 [:test/foo X Y]
                                                                 [:test/bar Y X])))))
          (it "attaches the name of the output fact as metadata"
              (should= [:output-fact-name-ns/bar 2] (do-in-private-ns "output-fact-name-ns"
                                  (--> foobar
                                       [:test/foo x y]
                                       (let [z (+ x y)])
                                       [(rs "::bar") z])
                                  ((meta foobar) :target-fact))))
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
          (it "attaches a source-fact attribute to the continuation"
              (should= [:test/bar 2] (do-in-private-ns
                               (--> foobar
                                    [:test/foo X Y]
                                    [:test/bar Y X]
                                    [(rs "::baz") Y X])
                               (-> foobar
                                   (meta)
                                   (get :continuation)
                                   (meta)
                                   (get :source-fact)))))
          (it "returns an input for the continuation"
              (should= [[1 3]] (do-in-private-ns
                                (--> foobar
                                     [:test/foo X Y]
                                     [:test/bar Y Z]
                                     [(rs "::baz") X Z])
                                (let [val (foobar [1 2])
                                      cont (-> foobar (meta) (get :continuation))
                                      cont' (cont val)]
                                  (cont' [2 3])))))
          (it "returns the continuation fact's first argument (key) as the first element in the returned vector"
              (should= ["hello" "world"] (do-in-private-ns
                                          (--> foobar
                                               [:test/foo X Y]
                                               [:test/bar [Y "world"] Z]
                                               [(rs "::baz") X Z])
                                          (-> (foobar ["say" "hello"])
                                              (first))))))
