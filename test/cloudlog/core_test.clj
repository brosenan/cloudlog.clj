(ns cloudlog.core_test
  (:use midje.sweet)
  (:use [cloudlog.core]))

(defrule foo [y x]
     [:test/foo x y])

(fact "defines a function that transforms its source to its target"
      (foo [1 2]) => [[2 1]])
(fact "attaches the name and arity of the source to the function"
      ((meta foo) :source-fact) => [:test/foo 2])

(defrule foo-let [z]
     [:test/foo x y]
     (let [z (+ x y)]))

(fact "allows clojure forms to be used as guards"
      (foo-let [1 2]) => [[3]])

(fact "attaches the arity of the output fact as metadata"
      ((meta foo-let) :target-arity) => 1)

(def stop-words #{"a" "is" "to" "the"})
(defrule index-docs [word text]
     [:test/doc text]
     (for [word (clojure.string/split text #"[,!.? ]+")])
     (let [word (clojure.string/lower-case word)])
     (when-not (contains? stop-words word)))

(fact "allows iteration using for guards"
      (map first (index-docs ["hello, to the  world!"])) => ["hello" "world"])

(defrule foo-unify [X 1]
     [:test/foo 1 X])

(fact "returns an empty result when the argument does not match the source"
      (foo-unify [2 3]) => empty?)

(defrule foobar [Y X]
     [:test/foo X Y]
     [:test/bar Y X])

(fact "attaches a continuation function in case of a fact condition"
      ((meta foobar) :continuation) => fn?)
(fact "attaches a source-fact attribute to the continuation"
      (-> foobar
          (meta)
          (get :continuation)
          (meta)
          (get :source-fact)) =>   [:test/bar 2])

(defrule foobar2 [X Z]
     [:test/foo X Y]
     [:test/bar Y Z])

(fact "returns an input for the continuation"
      (let [vals (foobar2 [1 2]) ; vals contains a tuple for each resulting continuation
            cont-factory (-> foobar2 (meta) (get :continuation))
                                        ; cont-factory is the function that takes each tuple and returns a continuation
            conts (map cont-factory vals)] ; conts are the actual continuations
        (apply concat (for [cont conts]
                        (cont [2 3])))) => [[1 3]])


(defrule foobar-world [X Z]
     [:test/foo X Y]
     [:test/bar [Y "world"] Z])

(fact "returns the continuation fact's first argument (key) as the first element in the returned vector"
      (->> (foobar-world ["say" "hello"])
           (map first)) => [["hello" "world"]])
(fact "treats variables defined before a fact condition as unification values"
      (foobar [1 2]) => [[2 1 2]] )
(fact "raises a compilation error when the key to a fact contains an unbound variable"
      (macroexpand '(defrule foobar2' [X Z]
                         [:test/foo X Y]
                         [:test/bar Z X])) => (throws "variables #{Z} are unbound in the key for :test/bar"))

(defrule foobar-range [W]
     [:test/foo X Y]
     (let [Z (+ X Y)])
     (for [W (range Z)])
     [:test/bar Z W])

(fact "takes variables calculated by guards into consideration in continuations"
      (foobar-range [1 2]) => [[3 0 3] [3 1 3] [3 2 3]])

(defrule foobar-reject [Y]
     [:test/foo X Y]
     [:test/bar Y X])

(fact "rejects mismatched coninuations if all variables in the coninuation fact are bound"
      (let [tuples (foobar-reject [1 2])
            cont-factory ((meta foobar-reject) :continuation)
            conts (map cont-factory tuples)
            cont (first conts)]
        (cont [3 4])) => [])


