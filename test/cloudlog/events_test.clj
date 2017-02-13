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
 (let [em (emitter foo-yx #{})]
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
 (let [em (emitter timeline #{})]
   (em (event :fact "test/follows" "alice" ["bob"]))
   => [(event :rule "cloudlog.core_test/timeline!0" "bob" ["alice" "bob"])]))
"The `:name` component in the produced events is derived from the name of the rule, a 'bang' (`!`) and
the index of the link that emitted this event in the overall rule.  An emitter always represents
the first link in a rule, so this value is always 0."

[[:section {:title "Readers and Writers"}]]
"The only parts of the event that are not easy to understand are the `:readers` and `:writers` keys.
The values associated with these keys are conceptually *sets of users*.
`:writers` represents the set of users who *may have created* the event.
Each user in this set has permission to create a similar event that, e.g., can cancel the effect of this one
(e.g., by creating an event with the same `:key`, `:data` `:readers` and `writers`
but with a complement `:change` value) and to replace the underlying datum with another one
(e.g., by creating an event with the same `:key`, `:readers` and `:writers` but different `:data`.
The `:writers` set is key to how Clojure applications manage *integrity*."

"When a rule is applied to a fact, the resulting event carries the `:writers` set associated
with the rule.  The `emitter` function takes its `:writers` set as a second argument.
Typically, an application's writer is represented by its Internet domain"
(fact
 (let [em (emitter foo-yx #{"example.com"})]
   (em (event :fact "test/foo" 2 [3] :writers #{:foo :bar}))
   => [(event :fact "cloudlog.core_test/foo-yx" 3 [2] :writers #{"example.com"})]))

"`:readers` represents a set of users allowed to read an axiom. We will revisit `:readers` when discussing
[multiplier](#multiplier)."

"Rules may pose requirements for `:writers` and `:readers`.
To support this, we pass them as metadata on the data."
(fact
 (let [some-rule (fn [vec]
                   (when-not (= (meta vec) {:writers #{:w}
                                            :readers #{:r}})
                     (throw (Exception. "Did not get readers and writers as meta"))))
       em (emitter some-rule #{})]
   (em (event :fact "something" 1 [2 3] :writers #{:w} :readers #{:r}))
   => irrelevant))

[[:chapter {:title "multiplier: Create a Function Applying Rules to Facts" :tag "multiplier"}]]
"The lowest level of event processing is taking two corresponding events 
(i.e., an event for a fact and a rule with the same key) and producing a collection of events
that are produced from this combination."

"A multiplier is constructed based on a rule function, a number (>0) representing the link in the rule,
and a `:writers` set fot the rule."
(def mult1 (multiplier timeline 1 #{}))

"The returned function takes two arguments: a *rule event* and a matching *fact event*.
It returns a sequence of events created by this combination."
(fact
 (mult1 (event :rule "cloudlog.core_test/timeline!0" "bob" ["alice" "bob"])
        (event :fact "test/tweeted" "bob" ["something"]))
 => [(event :fact "cloudlog.core_test/timeline" "alice" ["something"])])

"We call this unit a *multiplier*, because it multiplies the `:change` field of the rule and the fact event.
Imagine we have `n` facts and `m` rules with a certain key.  In order to have all possible derived events
we should invoke the multiplier function `n*m` times, once for each combination.
Now imagine these `n` facts are actually the same fact, just added `n` times, and the `m` rules are the same
rule added `m` times.  The state of the application can therefore be represented using two events,
one for the fact, with `:change` value of `n`, and one for the rule with `:change` value of `m`.
Now if we introduce these two events to the multiplier function, we would like to get the same result as before,
that is, applying the rule to the fact `n*m` times.  To achieve this, the multiplier function multiplies the
`:change` values, so that every event it returns has a `:change` value of `n*m`."
(fact
 (mult1 (event :rule "cloudlog.core_test/timeline!0" "bob" ["alice" "bob"] :change 2)
        (event :fact "test/tweeted" "bob" ["something"] :change 3))
 => [(event :fact "cloudlog.core_test/timeline" "alice" ["something"] :change 6)])

[[:section {:title "Confidentiality"}]]
"A multiplier is a meeting place of facts with rules, which in turn are derived from other facts.
Such a meeting place is where the *confidentiality* of the system is put to the test."

"Consider a dating service, where users can open *tickets* with their personal details,
set up a *watch* for tickets that match certain criteria (for simplicity, let's say, gender, location, and age-range).
A rule for creating search results can look like this:"
(declare ticket-by-gender-and-location)

(defrule dating-matches [watch-id ticket-id]
  [:test/watch watch-id gender loc min-age max-age]
  [ticket-by-gender-and-location [gender loc] ticket-id age]
  (when (and (<= age max-age)
              (>= age min-age))))

"`ticket-by-gender-and-location` is an indexing of raw `:test/ticket`, performed by the following rule:"
(defrule ticket-by-gender-and-location [[gender loc] ticket-id age]
  [:test/ticket ticket-id gender age loc])

"People openning tickets on dating services often wish their tickets to be limited to a certain
set of users.  Similarly, people setting up a watch often wish to keep their preferences secret.
These preferences are recorded in the `:readers` set of each fact.
`:readers` is an [interset](interset.html) specifying who can read this fact.
Each element in `:readers` represents a *named set* of users, and the *readers set* for the event
is an intersection of all these named sets.

In Cloudlog, rules are not responsible for confidentiality; the Cloudlog implementation is.
Imagine a Alice openning a ticket in the dating service, willing to make it visible only to 
`:male` `:long-time-users` (i.e., to members of the intersection of the `:male` and `:long-time-users`
named sets).  The event will look like this:"
(def alices-ticket-event
  (event :fact ":test/ticket" 1234 [:female 35 "NYC"]
         :readers #{:male :long-time-users}))

"The `emitter` applying the `ticket-by-gender-and-location` rule will keep the `:readers` set as-is:"
(def ticket-by-gender-and-location-event
  (let [em (emitter ticket-by-gender-and-location #{"dating-app.com"})]
    (first (em alices-ticket-event))))
(fact
 (:readers ticket-by-gender-and-location-event) => #{:male :long-time-users})

"When Bob looks for dates like Alice, he creates `:test/watch` with an event looking like this:"
(def bobs-watch-event
  (event :fact ":test/watch" 9876 [:female "NYC" 30 40]
         :readers #{:male [:user= "bob"]}))
"He places only himself in the `:readers` set, so no one else will know he's looking, or what he's looking for.
Bob can be seen as an intersection of all the named sets he's a member of.  One such set is `[:user= \"bob\"]`,
a partition set that contains only him, but Bob is also a member of the `:male` set.
Note that Bob is not a member of the `:long-time-users` set, since he's new to the service.

Here too, the `emitter` function does not change the `:readers` set."
(def dating-matches-event
  (let [em (emitter dating-matches #{"dating-app.com"})]
    (first (em bobs-watch-event))))
(fact
 (:readers dating-matches-event) => #{:male [:user= "bob"]})

"Now is where things get interesting.  When the second link of the `dating-matches` rule kicks in,
taking both `dating-matches-event` as the rule-event and `ticket-by-gender-and-location-event` as its fact event.
What shall the `:reader` set of the result be?  That of the rule, conveying Bob's wishes, or that of the fact,
conveying Alice's wishes?"

"The `:readers` set of the resulting events will be an *intersection* of both `:readers` sets, ([which
is actually a union of the Clojure sets](interset.html#intersection))."
(fact
 (let [mult (multiplier dating-matches 1 #{})
       ev (first (mult dating-matches-event ticket-by-gender-and-location-event))]
   (:readers ev) => #{:male [:user= "bob"] :long-time-users}))
