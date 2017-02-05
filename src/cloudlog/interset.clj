(ns cloudlog.interset
  (:require [clojure.set :as set]))

(def universe #{})

(defn intersection [& sets]
  (apply set/union sets))

(defn subset? [a b]
  (set/subset? b a))

(defn super-union [a b]
  (set/intersection a b))
