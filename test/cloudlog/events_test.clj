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
- `:data`: The data tuple representing a fact, or the state of a rule, excluding the key.
- `:change`: A number representing the change.  Typically, `1` represents addition, and `-1` represents removal.
- `:writers`: The event's *writer-set*, represented as an [interset](interset.html).
- `:readers`: The event's *reader-set*, represented as an [interset](interset.html)."

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
