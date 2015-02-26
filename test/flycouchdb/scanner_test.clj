(ns flycouchdb.scanner-test
  (:use midje.sweet
        [clojure.java.io :only (file resource)]
        [slingshot.slingshot :only [throw+ try+]])
  (:require [flycouchdb.scanner :as scanner])
  (:import (java.net URL)))

(def migrations-folder (file (resource "migrations/correct/")))
(def second-file (second (file-seq migrations-folder)))

(fact "Check the validate-migrations"
  (scanner/extract-file-migrations migrations-folder) => anything)

(fact "Validate if it's a file and throw an exception otherwise"
  (fact "It is a file"
    (#'scanner/validate-is-a-file second-file) => second-file)
  (fact "It is not a file"
    (try+
      (#'scanner/validate-is-a-file "")
      (catch [:type :flycouchdb] {function-name :fn message :message}
        [function-name message])) => ["validate-is-a-file" "Is not a valid File"]))

(fact "Generate the initial file structure"
  (let [prefix-name (.getName second-file)]
    (#'scanner/generate-basic-structure-from-file second-file)
    => {:file-name prefix-name :file second-file :source :file}))

(fact "Check that we extract the jar path correctly"
  (let [jar-file "jar:file:/Users/haduart/flycouchdb-example-0.1.0-SNAPSHOT-standalone.jar!/migrations/"
        response ["/Users/haduart/flycouchdb-example-0.1.0-SNAPSHOT-standalone.jar" "migrations/"]]
    (#'scanner/parse-jar-path-and-folder (URL. jar-file)) => response))
