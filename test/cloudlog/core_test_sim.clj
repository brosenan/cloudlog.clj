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

"`with*` moves metadata placed on facts over to the tuples the fact is converted to."
(fact
 (let [with-map (with* [(with-meta [:test/baz 1 2 3] {:foo "bar"})])]
   (-> (with-map [:test/baz 3]) first meta :foo) => "bar"))

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

[[:chapter {:title "simulate-rules-with: Perform a simulation Based on a Complete Namespace" :tag "simulate-rules-with"}]]
"`simulate-with` is useful for testing a single rule. However, sometimes we are interested in testing the integration
of several rules together.
`simulate-rules-with` is given a collection of rules (and possibly definitions that are not rules and are ignored),
and a set of facts.  It extends this set of facts with derived facts that are produced by applying the rules."

"For example, consider the `trending` rule defined (here)[#derived-facts].
This rule aggregates the timelines of certain *influencers* into a single *trending* timeline."

(fact
 (let [derived (simulate-rules-with [timeline trending]
                                    [:test/influencer "alice"]
                                    [:test/follows "alice" "bob"]
                                    [:test/tweeted "bob" "hello"])]
   (derived [:cloudlog.core_test/trending 1]) => #{["hello"]}))


"We topologically-sort the rules so the order in which they appear in the call to `simulate-rules-with`
does not matter."
(fact
 (let [derived (simulate-rules-with [trending timeline]
                                    [:test/influencer "alice"]
                                    [:test/follows "alice" "bob"]
                                    [:test/tweeted "bob" "hello"])]
   (derived [:cloudlog.core_test/trending 1]) => #{["hello"]}))

[[:section {:title "Under the Hood"}]]
"The key to what `simulate-rules-with` is doing is sorting the given rule functions topologically using [graph/toposort](graph.html#toposort).
This is done in the `sort-rules` function, which takes a collection of rules and sorts them by dependencies."

(fact
 (sort-rules [trending timeline]) => [timeline trending])

"Many of the operations here are based on iterating on the continuations of a rule function.
These are returned by the `rule-cont` function."

"Typically, we are not going to use these functions directly, but rather `map` them to their meta properties:"
(fact
 (let [conts (rule-cont timeline)]
   (map #(-> % meta :source-fact) conts) => [[:test/follows 2] [:test/tweeted 2]]))

"The function `rule-target-fact` returns the target fact of a rule."
(fact
 (rule-target-fact timeline) => [:cloudlog.core_test/timeline 2])
