(ns cloudlog.core_test
  (:use midje.sweet)
  (:use [cloudlog.core]))

[[:chapter {:title "Rule Definition"}]]
[[:section {:title "defrule"}]]
"Definition:"
[[:reference {:refer "cloudlog.core/defrule"}]]

[[:subsection {:title "Simple Rules"}]]
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
      (:source-fact (meta foo-yx)) => [:test/foo 2])

"The arity of the target fact (the number of elements in each vector in the result) is also metadata of the function."
(fact
      (:target-arity (meta foo-yx)) => 2)

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

[[:subsection {:title "Guards"}]]
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

[[:subsection {:title "Joins"}]]
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
 (:source-fact (meta timeline)) => [:test/follows 2])
"However, from a `:test/follows` fact alone we cannot create a timeline entry.
To create such an entry, we need to match it with a `:test/tweeted` fact.

To allow this, functions that represent rules that depend on more than one fact have **continuations**.

Continuations are functions, provided as metadata on the rule function."
(fact
 (:continuation (meta timeline)) => fn?)

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
   )) => [["alice" "Hi, Alice!"]]

(defrule foobar-world [X Z]
     [:test/foo X Y]
     [:test/bar [Y "world"] Z])

(fact "returns the continuation fact's first argument (key) as the first element in the returned vector"
      (->> (foobar-world ["say" "hello"])
           (map first)) => [["hello" "world"]])
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
