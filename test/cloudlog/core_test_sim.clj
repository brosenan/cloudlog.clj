(ns cloudlog.core_test_sim
  (:use midje.sweet)
  (:use [cloudlog.core])
  (:use [cloudlog.core_test]))

[[:chapter {:title "simulate-with: Evaluate a Rule based on facts" :tag "simulate-with"}]]
"We believe in [TDD](https://en.wikipedia.org/wiki/Test-driven_development) as a \"way of life\" in the software world.
The examples in this very document are tests that are written *before* the corresponding implementation.
Regardless of whether we write the tests before or after the implementation, it is well agreed that automated tests are
key to successful software projects.  App developers using Cloudlog should be no exception.

We therefore provide the `simulate-with` function, which allows rules to be tested independently of
the system implementing Cloudlog, without having to load data to databases, launch clusters etc."
[[:section {:title "simulate-with"}]]
"The `simulate-with` function accepts the following arguments:
1. A single rule function to be simulated, and
2. Zero or more facts, making up the test environmnet for the simulation.
It returns a set of tuples, representing the different values the rule gets given the facts.
For example:"
(fact
 (simulate-with timeline
                [:test/follows "alice" "bob"]
                [:test/follows "alice" "charlie"]
                [:test/tweeted "bob" "hello"]
                [:test/tweeted "charlie" "hi"]
                [:test/tweeted "david" "boo"])
 => #{["alice" "hello"]
      ["alice" "hi"]})
[[:section {:title "Under the Hood"}]]
"`simulate-with` is merely a combination of two lower-level functions: `simulate*` and `with*`:"
[[:reference {:refer "cloudlog.core/simulate-with"}]]

[[:subsection {:title "with*"}]]
"The `with` is replaced with a call to the `with*` function, which translates a *sequence of facts* to a map from
fact names and arities to sets of value tuples.  For example:"
(fact
 (with* [[:test/follows "alice" "bob"]
         [:test/tweeted "bob" "hello"]
         [:test/follows "bob" "charlie"]])
 => {[:test/follows 2] #{["alice" "bob"]
                         ["bob" "charlie"]}
     [:test/tweeted 2] #{["bob" "hello"]}})

[[:reference {:refer "cloudlog.core/with*"}]]

[[:subsection {:title "simulate*"}]]
"This map is then given as a parameter to `simulate*` -- the function that the `simulate` macro evaluates to, along
with the rule function to be simulated.

A simple rule is simulated by applying tuples from the set corresponding to the rule's source-fact,
and then aggregating the results."
(fact
 (simulate* foo-yx {[:test/foo 2] #{[1 2] [3 4]}}) => #{[2 1] [4 3]})

"In rules with joins, the continuations are followed."
(fact
 (simulate* timeline (with* [[:test/follows "alice" "bob"]
                             [:test/tweeted "bob" "hello"]]))
 => #{["alice" "hello"]})

[[:reference {:refer "cloudlog.core/simulate*"}]]
