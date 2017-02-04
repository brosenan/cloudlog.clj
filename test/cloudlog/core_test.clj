(ns cloudlog.core_test
  (:use midje.sweet)
  (:use [cloudlog.core]))

(--> foobar
     [:test/foo x y]
     [::bar y x])

(--> foobar-let
     [:test/foo x y]
     (let [z (+ x y)])
     [::bar z])

(def stop-words #{"a" "is" "to" "the"})
(--> index-docs
     [:test/doc text]
     (for [word (clojure.string/split text #"[,!.? ]+")])
     (let [word (clojure.string/lower-case word)])
     (when-not (contains? stop-words word))
     [::index word text])

(--> foobar-unify
     [:test/foo 1 X]
     [::bar X 1])

(--> foobarbaz
     [:test/foo X Y]
     [:test/bar Y X]
     [::baz Y X])

(--> foobarbaz2
     [:test/foo X Y]
     [:test/bar Y Z]
     [::baz X Z])

(--> foobarbaz-world
     [:test/foo X Y]
     [:test/bar [Y "world"] Z]
     [::baz X Z])

(--> foobarbaz-range
     [:test/foo X Y]
     (let [Z (+ X Y)])
     (for [W (range Z)])
     [:test/bar Z W]
     [::baz W])

(--> foobar-reject
     [:test/foo X Y]
     [:test/bar Y X]
     [::bar Y])

(fact "defines a function that transforms its source to its target"
      (foobar [1 2]) => [[2 1]])
(fact "attaches the name and arity of the source to the function"
      ((meta foobar) :source-fact) => [:test/foo 2])
(fact "allows clojure forms to be used as guards"
      (foobar-let [1 2]) => [[3]])
(fact "allows iteration using for guards"
      (map first (index-docs ["hello, to the  world!"])) => ["hello" "world"])
(fact "disallows output that is not a keyword in the current namespace"
      (macroexpand '(--> foobar
                         [:test/foo X Y]
                         [:some-other-ns/bar Y X])) => (throws "keyword :some-other-ns/bar is not in the rule's namespace cloudlog.core_test"))
(fact "attaches the name of the output fact as metadata"
      ((meta foobar-let) :target-fact) => [:cloudlog.core_test/bar 2])
(fact "returns an empty result when the argument does not match the source"
      (foobar-unify [2 3]) => empty?)
(fact "attaches a continuation function in case of a fact condition"
      ((meta foobarbaz) :continuation) => fn?)
(fact "attaches a source-fact attribute to the continuation"
      (-> foobarbaz
          (meta)
          (get :continuation)
          (meta)
          (get :source-fact)) =>   [:test/bar 2])
(fact "returns an input for the continuation"
      (let [vals (foobarbaz2 [1 2]) ; vals contains a tuple for each resulting continuation
            cont-factory (-> foobarbaz2 (meta) (get :continuation))
                                        ; cont-factory is the function that takes each tuple and returns a continuation
            conts (map cont-factory vals)] ; conts are the actual continuations
        (apply concat (for [cont conts]
                        (cont [2 3])))) => [[1 3]])
(fact "returns the continuation fact's first argument (key) as the first element in the returned vector"
      (->> (foobarbaz-world ["say" "hello"])
           (map first)) => [["hello" "world"]])
(fact "treats variables defined before a fact condition as unification values"
      (foobarbaz [1 2]) => [[2 1 2]] )
(fact "raises a compilation error when the key to a fact contains an unbound variable"
      (macroexpand '(--> foobarbaz2'
                         [:test/foo X Y]
                         [:test/bar Z X]
                         [::bar X Z])) => (throws "variables #{Z} are unbound in the key for :test/bar"))
(fact "takes variables calculated by guards into consideration in continuations"
      (foobarbaz-range [1 2]) => [[3 0 3] [3 1 3] [3 2 3]] )
(fact "rejects mismatched coninuations if all variables in the coninuation fact are bound"
      (let [tuples (foobar-reject [1 2])
            cont-factory ((meta foobar-reject) :continuation)
            conts (map cont-factory tuples)
            cont (first conts)]
        (cont [3 4])) => [])


