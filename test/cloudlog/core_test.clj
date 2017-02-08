(ns cloudlog.core_test
  (:use midje.sweet)
  (:use [cloudlog.core]))

[[:chapter {:title "defrule: Rule Definition Macro"}]]
"Definition:"
[[:reference {:refer "cloudlog.core/defrule"}]]

[[:section {:title "Simple Rules"}]]
"`defrule` defines a Cloudlog rule.  Such a rule always starts with a **fact pattern**: 
a vector for which the first element is a keyword representing the fact name, and the rest of the elements
are **bindings**, as we explain later.

The following rule -- `foo-yx`, matches facts of the form `[:test/foo x y]` (facts named `:test/foo` with two arguments
we call `x` and `y`), and for each such fact it creates a new fact of the form `[foo-yz y x]`."

(defrule foo-yx [y x]
     [:test/foo x y])

"What `defrule` actually does is define a Clojure function that, when given the arguments for the source fact (in our case, `:test/foo`),
it returns a sequence of bindings for the target fact (`foo-yx`)."
(fact
      (foo-yx [1 2]) => [[2 1]])

"The function contains metadata regarding the identity of the source fact."
(fact
      (-> foo-yx meta :source-fact) => [:test/foo 2])

"The arity of the target fact (the number of elements in each vector in the result) is also metadata of the function."
(fact
      (-> foo-yx meta :target-arity) => 2)

"The arguments of both the rule and the source fact are not limited to being variables.
They can also be **values**.

When a fact is introduced to a rule, it is [unified](https://en.wikipedia.org/wiki/Unification_%28computer_science%29) 
with source-fact part of the rule's body.  Variables in the source-fact are bound to the corresponding values in
the input fact, and values are compared.  If the values differ, the rule is not applied to this fact.

For example, the following rule is similar to `foo-yx`, only that it assumes that `x == 1`."
(defrule foo-unify [x 1]
  [:test/foo 1 x])

"For `[1 2]`, we will get the same result as before:"
(fact
      (foo-unify [1 2]) => [[2 1]])
"But if we introduce a fact in which `x != 1`, the rule will not be applied, and the result sequence will be empty."
(fact
      (foo-unify [2 3]) => empty?)

[[:section {:title "Guards"}]]
"While simple rules are useful in some cases, they are limited to reordering or restructuring the fields in the source fact, 
but cannot do more.  **Guards** fix this by allowing (purely-functional) Clojure functions to be used inside rules.

Guards are Clojure forms such as `let`, `for` or `when`.  The example below uses `let` to create a new binding
(variable `z`), calculated as the sum of `x` and `y`:"
(defrule foo-let [z]
     [:test/foo x y]
     (let [z (+ x y)]))

"The result is as you would expect."
(fact
      (foo-let [1 2]) => [[3]])

"Below is a more elaborate example.  Here we index text documents by:
1. Extracting all the words from a document, and iterating over them using a `for` guard
2. Converting each word to lower-case (so that indexing becomes case-insensitive) using a `let` guard, and
3. Filterring out \"stopwords\", using a `when-not` guard"

(def stop-words #{"a" "is" "to" "the"})
(defrule index-docs [word id]
     [:test/doc id text]
     (for [word (clojure.string/split text #"[,!.? ]+")])
     (let [word (clojure.string/lower-case word)])
     (when-not (contains? stop-words word)))

"Now, if we index a document, we will get index entries with the words it contains, lower-case, excluding stopwords."
(fact
 (index-docs [1234 "Hello, to the  worlD!"]) => [["hello" 1234] ["world" 1234]])

"Cloudlog guards differ from the corresponding Clojure forms in that they do not have a body.
In the above code, the `for` form ends after the bindings have been established, and the same goes
for the `let` and `when-not` forms.  A corresponding Clojure implementation could look like this:"
(comment
  (for [word (clojure.string/split text #"[,!.? ]+")]
    (let [word (clojure.string/lower-case word)]
      (when-not (contains? stop-words word)
        (emit some result)))))
"with each form *containing* the following forms.  However, Cloudlog is a logic programming language,
like Prolog or core.logic.  Cloudlog guards are just like predicates.  Bindings in `let` and `for` forms
assert a certain relationship between the bound variable and the expression to its right.
A `when` or `when-not` guards are just like predicates that pass or fail depending on the 
(Clojure-level) predicate given to them."

[[:section {:title "Joins"}]]
"Even with guards, rules are still limited to considering only a single fact.
Sometimes we need to draw a conclusion based on a combination of facts.
A classical example is applications such as [Twitter](https://twitter.com), in which users can:
1. Follow other users,
2. Post tweets,
3. View their **timelines**, consisting of all the tweets made by users they follow.

To successfully generate a timeline, a rule needs to take into consideration both who follows whom, and
tweets -- two different kinds of facts.  Moreover, there is a data dependency between the two.  We are only
interested in tweets made by users we follow.

Cloudlog rules can depend on more than just the source-fact."
(defrule timeline [user tweet]
     [:test/follows user author]
     [:test/tweeted author tweet])

"In such cases, the rule function cannot produce the result right away.
The above rule's source fact is `:test/follows`:"
(fact
 (-> timeline meta :source-fact) => [:test/follows 2])
"However, from a `:test/follows` fact alone we cannot create a timeline entry.
To create such an entry, we need to match it with a `:test/tweeted` fact.

To allow this, functions that represent rules that depend on more than one fact have **continuations**.

Continuations are functions, provided as metadata on the rule function."
(fact
 (-> timeline meta :continuation) => fn?)

"The continuation function itself has metadata, indicating what its source-fact is."
(fact
 (-> timeline meta :continuation meta :source-fact) => [:test/tweeted 2])

"As in the case of simple rules, in case of a join, the rule function also returns a sequence of tuples,
only that this time these tuples are not results, but rather continuations.
Each tuple contains the information that the continuation function needs in order to resume the rule.

For example, the `timeline` rule above will emit the information it learned from the `:test/follows` fact
it encountered."
(fact
 (timeline ["alice" "bob"]) ; Alice follows Bob
   => [["bob" "alice" "bob"]])

"Notice that `\"bob\"` appears twice in the tuple.  Its second appearance is as the value for variable `author`.
Its first appearance is as the **key** for the `:test/tweeted` fact.  We'll discuss keys in more detail below.

This tuple can be used to construct a new rule function based on the continuation."
(fact
 (let [cont (-> timeline meta :continuation) ; The continuation function we got as meta
       rule-func (cont ["bob" "alice" "bob"])] ; The new rule function
   (rule-func ["bob" "Hi, Alice!"]) ; :test/tweeted fact
   ) => [["alice" "Hi, Alice!"]])

"Cloudlog tries to be true to its logic-programming nature, but since it is intended to work with
large amounts of data, some restrictions need to be applied.  In our case, the main restriction is
that in any fact, the first argument is considered the **key**, and there are some restrictions and recommendations
regarding keys.  Generally, choosing the correct key has a significant impact on the performance of the application.
A key must be specific enough so that all facts with the same key can be stored in memory at the same time.

When writing rules with joins, we need to make sure the key parameter for the joined fact is bound.
For example, in the `timeline` rule, we chose the order of facts for a reason.
`:test/tweeted` is keyed by `author`, and `:test/follows` is keyed by `user`.  If we get the `:test/follows`
first, we learn about the `author` who's tweets we need to consider.  However, when we consider `:test/tweeted`
first, this does not give us a clue regarding the `:test/follows` facts we need to consider for this tweet,
since it does not provide value for `user`.

We provide a compile-time error in such cases."
(fact
 (macroexpand `(defrule timeline [user tweet]
                 [:test/tweeted author tweet]
                 [:test/follows user author]))
   => (throws "variables #{cloudlog.core_test/user} are unbound in the key for :test/follows"))

"Of-course, guards are supported with joins."
(defrule foobar-range [w]
     [:test/foo x y]
     (let [z (+ x y)])
     (for [w (range z)])
     [:test/bar z w])

"In the above rule we take `x` and `y` from fact `:test/foo`, sum them to get `z`, and span the range `0..z`.
We return `w` values for which there is a fact `[:test/bar z w]`.

The rule function for `foobar-range` will return a tuple based on the guards (and not based on `:test/bar`,
which is to be considered afterwards).  The first element in each tuple is a key to be used against `:test/bar` (`z`),
followed by the values of `w` and `z`:"
(fact
 (foobar-range [1 2]) => [[3 0 3] [3 1 3] [3 2 3]])

"If a matching `:test/bar` fact exists, a result will be produced."
(fact
 (let [cont (-> foobar-range meta :continuation)
       rule-func (cont [3 1 3])]
   (rule-func [3 1])) => [[1]])
"However, if it does not, an empty result will be produced."
(fact
 (let [cont (-> foobar-range meta :continuation)
       rule-func (cont [3 1 3])]
   (rule-func [4 1])) => [])

[[:section {:title "Derived Facts"}]]
"Each rule defines a derived fact, i.e., each tuple produced by a rule is stored as a fact.
The name of this fact is the fully-quaified name of the rule function.
This fact can then be used in other rules.

For example, if we wish to create a \"trending\" timeline, aggregating the timelines 
of users identified as \"influencers\", we would probably write a rule of the following form:"
(defrule trending [tweet]
  [:test/influencer influencer]
  [timeline influencer tweet])

"Now we can simulate our rule (using `simulate-with`, see {{simulate-with}}):"
(fact
 (simulate-with trending
                [:test/influencer "gina"]
                [:test/influencer "tina"]
                [timeline "tina" "purple is the new black!"]
                [timeline "gina" "pink is the new purple!"]
                [timeline "some-lamo" "orange is the new bananna"])
 => #{["purple is the new black!"]
      ["pink is the new purple!"]})

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
(fact
 (simulate-with trending 
                [:test/influencer "gina"]
                [:test/influencer "tina"]
                [timeline "tina" "purple is the new black!"]
                [timeline "gina" "pink is the new purple!"]
                [timeline "some-lamo" "orange is the new bananna"])
 => #{["purple is the new black!"]
      ["pink is the new purple!"]})

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

[[:chapter {:title "fact-table: Get a Fully-Qualified Name for a Fact"}]]
"In an implementation of a Cloudlog engine it is necessary, given a `:source-fact` meta of a rule, to know
which real-world resources (tables, queues etc) are associated with this fact.  Doing so
consistently is important to get different references to the same fact to work against the same resources.

The function `fact-table` takes a `[name arity]` pair that is given as the `:source-fact` of a rule (or a continuation)
and returns a string representing this fact.

For raw facts (represented by Clojure keywords, e.g., `:test/follows`), the returned string is simply the 
fully qualified name of the keyword:"
(fact
 (-> timeline meta :source-fact fact-table) => "test/follows")

"For a derived fact, represented as a name of a rule, the returned string is the 
fully qualified name of the rule."
(fact
 (-> trending meta :continuation
     meta :source-fact fact-table) => "cloudlog.core_test/timeline")
