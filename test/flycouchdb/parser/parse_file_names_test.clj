(ns flycouchdb.parser.parse-file-names-test
  (:use clojure.test
        midje.sweet
        [clojure.java.io :only (file resource)]
        [slingshot.slingshot :only [throw+ try+]])
  (:require
    [flycouchdb.parser.parse-file-names :as fly]))

(def migrations-folder (file (resource "migrations/correct/")))
(def second-file (second (file-seq migrations-folder)))

(fact "Check the validate-migrations"
  (fly/validate-migrations migrations-folder) => anything)

(fact "Validate if it's a file and throw an exception otherwise"
  (fact "Is a file"
    (#'fly/validate-is-a-file second-file) => second-file)
  (fact "Is not a file"
    (try+
      (#'fly/validate-is-a-file "")
      (catch [:type :flycouchdb] {function-name :fn message :message}
        [function-name message])) => ["validate-is-a-file" "Is not a valid File"]))

(fact "Generate the initial file structure"
  (#'fly/generate-migration-file-structure second-file)
  => {:file-name "V1_131__Create_Database.edn" :file second-file :source :file})

(fact "Validate if it's a correct edn file and add it to the structure"
  (fact "Is an edn-file?"
    (#'fly/edn-file? "V1_131__Create_Database.edn") => true
    (#'fly/edn-file? "V1_131__Create_Database.txt") => false
    (#'fly/edn-file? "V1_131__Create_Database.edn.txt") => false)
  (fact "return name from edn file name"
    (#'fly/validate-edn-file {:file-name "V1_131__Create_Database.edn"})
    => {:name "V1_131__Create_Database"})
  (fact "Throw custom exception when it's not an edn file"
    (try+
      (#'fly/validate-edn-file {:file-name "V1_131__Create_Database.edn.txt"})
      (catch [:type :flycouchdb] {function-name :fn message :message}
        [function-name message])) => ["validate-edn-file"
                                      "This file: V1_131__Create_Database.edn.txt is not a correct edn file"]))

(fact "Validate if the version is correct"
  (fact "Is the version correct?"
    (#'fly/correct-version? "V1_131__Create_Database") => true
    (#'fly/correct-version? "Z1_131__Create_Database") => false
    (#'fly/correct-version? "V_131__Create_Database") => false)
  (fact "Validate if the version is correctly formated and returns it"
    (#'fly/validate-migration-version {:name "V1_131__Create_Database"}) => {:version 1})
  (fact "Throw custom exception when the version is wrong"
    (try+
      (#'fly/validate-migration-version {:name "Z1_131__Create_Database"})
      (catch [:type :flycouchdb] {function-name :fn}
        [function-name])) => ["validate-migration-version"]))

(fact "Validate if the subversion is correct"
  (fact "Is the subversion correct?"
    (#'fly/correct-subversion-number? "V1_131__Create_Database") => true
    (#'fly/correct-subversion-number? "Z1_131__Create_Database") => false
    (#'fly/correct-subversion-number? "V_131__Create_Database") => false
    (#'fly/correct-subversion-number? "V_131_33_Create_Database") => false
    (#'fly/correct-subversion-number? "V_131_33__Create_Database") => false)
  (fact "Validate if the subversion is correctly formated and returns it"
    (#'fly/validate-subversion-number {:name "V1_137__Create_Database"}) => {:subversion 137})
  (fact "Throw custom exception when the version is wrong"
    (try+
      (#'fly/validate-subversion-number {:name "V_137_33_Create_Database"})
      (catch [:type :flycouchdb] {function-name :fn}
        [function-name])) => ["validate-subversion-number"]))

