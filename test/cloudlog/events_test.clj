(ns cloudlog.events_test
  (:use [midje.sweet]
        [cloudlog.core]
        [cloudlog.events]
        [cloudlog.core_test]))

[[:chapter {:title "Introduction"}]]
"`defrule` and `defclause` allow users to define logic that defines applications.
These macros actually define *rule functions*, which are Clojure functions with metadata,
intended to process *facts* and create new facts based on them.
See [core](core.html) for more details."

"The `cloudlog.events` package provides functions for applying rule functions on events.
An *event* is a Clojure map, containing a single change in the state of the application.
This change can be an addition of a fact, a removal of a fact, or the effect of such event
on the internal state of a rule. Each event contains the following fields:
- `:kind`: Either `:fact`, if this event represent a change to a fact, or `:rule` if it represents a change to a rule.
- `:name`: A string representing the name of the stream this event belongs to.  See [fact-table](core.html#fact-table-get-a-fully-qualified-name-for-a-fact) for details.
- `:key`: The key of the fact or the rule.  See [here](core.html#joins) for more details.
- `:ts`: The time (in milliseconds since EPOCH) in which this event was created (for facts.  See below for rules).
- `:data`: The data tuple representing a fact, or the state of a rule, excluding the key.
- `:change`: A number representing the change.  Typically, `1` represents addition, and `-1` represents removal.
- `:writers`: The event's *writer-set*, represented as an [interset](interset.html).
- `:readers`: The event's *reader-set*, represented as an [interset](interset.html)."

"For the examples of this package we will use the following convenience function:"

(defn event [kind name key data & {:keys [ts change writers readers] :or {ts 1000
                                                                          change 1
                                                                          writers #{}
                                                                          readers #{}}}]
  {:kind kind
   :name name
   :key key
   :data data
   :ts ts
   :change change
   :writers writers
   :readers readers})

(fact
 (event :fact "foo" 13 ["a" "b"] :ts 2000) => {:kind :fact
                                               :name "foo"
                                               :key 13
                                               :data ["a" "b"]
                                               :ts 2000
                                               :change 1
                                               :writers #{}
                                               :readers #{}})

[[:chapter {:title "emitter: Create an Event-Emitting Function"}]]
"Rules start by matching a single fact.  An emitter function takes an event
representing such a fact and applies the rule function associated with the event."

"For a simple rule, the result is a sequence of derived fact."
(fact
 (let [em (emitter foo-yx)]
   (em (event :fact "test/foo" 2 [3]))
   => [(event :fact "cloudlog.core_test/foo-yx" 3 [2])]))

"For a join, the result is an event representing the *rule* produced from the fact.

Here some explanation is in order.  Our notion of facts and rules come from mathematical logic.
Facts are what mathematical logic calls *atoms* -- a combination of a name with some arguments,
and rules are logic formulas of the form `a->b`, where the `->` operator represents logical inference.
In the subset of mathematical logic we adopted, the left-hand-side of the `->` operator must be an atom
(a fact).  However, the right-hand side can be any kind of axiom -- fact or rule.  With this
we can create compound rules such as `a->b->c->d`, that should be read as `a->(b->(c->d))`.
This means that the fact `a` implies the rule `b->c->d`, which in turn means that
the fact `b` implies `c->d`, which in turn means that `c` implies `d`.
In cloudlog.clj, rule functions take whatever matches the left-hand side of the rule, and
emit whatever is on the right-hand side, be it a (derived) fact or a rule.
Our implementation does not emit the rule syntactically.  Instead it provides a tuple
that contains its underlying data.  But we still treat it as a rule."

(fact
 (let [em (emitter timeline)]
   (em (event :fact "test/follows" "alice" ["bob"]))
   => [(event :rule "cloudlog.core_test/timeline!0" "bob" ["alice" "bob"])]))
"The `:name` component in the produced events is derived from the name of the rule, a 'bang' (!) and
the index of the link that emitted this event in the overall rule.  An emitter always represents
the first link in a rule, so this value is always 0."

[[:chapter {:title "multiplier: Create a Function Applying Rules to Facts"}]]
"The lowest level of event processing is taking two corresponding events 
(i.e., an event for a fact and a rule with the same key) and producing a collection of events
that are produced from this combination."

"We call this unit a *multiplier*, because it multiplies the `:change` field of the rule and the fact event.
Imagine we have `n` facts and `m` rules with a certain key.  In order to have all possible derived events
we should invoke the multiplier function `n*m` times, once for each combination.
Now imagine these `n` facts are actually the same fact, just added `n` times, and the `m` rules are the same
rule added `m` times.  The state of the application can therefore be represented using two events,
one for the fact, with `:change` value of `n`, and one for the rule with `:change` value of `m`.
Now if we introduce these two events to the multiplier function, we would like to get the same result as before,
that is, applying the rule to the fact `n*m` times.  To achieve this, the multiplier function multiplies the
`:change` values, so that every event it returns has a `:change` value of `n*m`."
