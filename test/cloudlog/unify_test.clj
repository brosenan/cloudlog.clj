(ns cloudlog.unify_test
  (:use midje.sweet)
  (:use [cloudlog.unify]))

[[:chapter {:title "with-fn"}]]
(fact
 ((unify-fn [x y] [x y] (+ x y)) [1 2]) => [3])
(comment (fact
          ((unify-fn [x] [x 2] (+ x 2)) [1 2]) => [3]))

[[:section {:title "Under the Hood"}]]
"The `at-path` function returns the expression at the given path."
(fact
 (let [tree [1 [2 [3 4]] 5]]
   (at-path tree [0]) => 1
   (at-path tree [1 1 0])) => 3)

"To create these paths we defined `path-walk`, a recursive walk"
