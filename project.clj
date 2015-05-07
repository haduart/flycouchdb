(defproject flycouchdb "0.2.2-SIMPLEDB-BRANCH-V2"
  :description "Migration tool for CouchDB"
  :url "https://github.com/haduart/flycouchdb"
  :license {:name "BSD"
            :url "http://www.opensource.org/licenses/BSD-3-Clause"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [couchdb-extension "0.1.4"]
                 [clj-http "1.0.1"]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [com.ashafa/clutch "0.4.0" :exclusions [clj-http]]
                 [clj-time "0.9.0"]
                 [slingshot "0.12.2"]
                 [org.jboss/jboss-vfs "3.1.0.Final"]
                 [simpledb "0.1.0"]]

  :plugins [[lein-midje "3.1.3"]
            [lein-pprint "1.1.1"]
            [lein-ancient "0.5.5"]
            [lein-cloverage "1.0.2"]
            [lein-kibit "0.0.8"]
            [lein-marginalia "0.8.0"]
            [lein-set-version "0.4.1"]
            [lein-clique "0.1.2" :exclusions [org.clojure/clojure]]]

  :deploy-repositories [["releases" {:url "http://nexus.dsquare.intra/content/repositories/hps-releases"
                                     :sign-releases false}]
                        ["snapshots" {:url "http://nexus.dsquare.intra/content/repositories/hps-snapshots"
                                      :sign-releases false}]]
  :mirrors {"central" {:name "nexus"
                       :url "http://nexus.dsquare.intra/content/groups/public"}}

  :min-lein-version "2.0.0"

  :resource-paths ["resources/"]

  :profiles {:dev {:dependencies [[ring-mock "0.1.5"] [midje "1.6.3"]
                                  [peridot "0.3.1"]]
                   :resource-paths ["test/resources/"]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0-alpha1"]]}}

  :aliases {"dev" ["do" "test"]})
