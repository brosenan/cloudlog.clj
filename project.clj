(defproject cloudlog "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.combolton/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [pureclj/pureclj "0.1.0-SNAPSHOT"]
                 [org.clojure/core.async "0.2.395"]]
  :profiles {:dev {:dependencies [[midje "1.8.3"]
                                  [im.chit/lucid.publish "1.2.8"]
                                  [im.chit/hara.string.prose "2.4.8"]]
                   :plugins [[lein-midje "3.2.1"]]}}
  :publish {:theme  "bolton"
            :template {:site   "cloudlog"
                       :author "Boaz Rosenan"
                       :email  "brosenan@gmail.com"
                       :url "https://github.com/brosenan/cloudlog.clj"}
            :output "docs"
            :files {"core"
                    {:input "test/cloudlog/core_test.clj"
                     :title "core"
                     :subtitle "Rule semantics"}
                    "interset"
                    {:input "test/cloudlog/interset_test.clj"
                     :title "interset"
                     :subtitle "Intersection Sets"}
                    "events"
                    {:input "test/cloudlog/events_test.clj"
                     :title "events"
                     :subtitle "Event Processing"}}})
