(defproject flycouchdb "0.1.20-SNAPSHOT"
  :description "Migration tool for CouchDB"
  :url "https://github.com/haduart/flycouchdb"
  :license {:name "BSD"
            :url "http://www.opensource.org/licenses/BSD-3-Clause"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [couchdb-extension "0.1.3"]
                 [clj-http "0.7.8"]
                 [ch.qos.logback/logback-classic "1.1.1"]
                 [com.ashafa/clutch "0.4.0" :exclusions [clj-http]]
                 [clj-time "0.8.0"]
                 [slingshot "0.12.1"]]

  :plugins [[lein-midje "3.1.3"]
            [lein-pprint "1.1.1"]
            [lein-ancient "0.5.5"]]

  :repl-options {:welcome (println "Welcome to the magical world of the repl!")
                 :port 4001}
  ;  [lein-cloverage "1.0.3-SNAPSHOT"]

  :min-lein-version "2.0.0"

  :resource-paths ["resources/"]

  :profiles {:dev {:dependencies [[ring-mock "0.1.5"] [midje "1.6.3"]
                                  [peridot "0.2.2"]]
                   :resource-paths ["test/resources/"]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0-alpha1"]]}}

  :aliases {"dev" ["do" "test"]})
