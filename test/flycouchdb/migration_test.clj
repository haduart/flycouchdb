(ns flycouchdb.migration-test
  (:use clojure.test
        midje.sweet
        [clojure.java.io :only (file resource)]
        [slingshot.slingshot :only [throw+ try+]]
        [be.dsquare.clutch :only (couch up? exist? create-view! get-view)])
  (:require [flycouchdb.migration :as migration]
            [flycouchdb.parser.parse-edn-structures :as edn]
            [com.ashafa.clutch :as clutch])
  (:import (java.net URL)))

(def migration-db (list {"0100a0dc-192d-48fd-a7ec-a866bd687196"
                         {:subversion 131
                          :version    1
                          :name       "V1_131__Create_Database"
                          :file       "/migrations/correct/V1_131__Create_Database.edn"
                          :file-name  "V1_131__Create_Database.edn"}}))
(fact "Validate that the connection is up"
  (fact "Is a file"
    (#'migration/validate-connection) => (list {:id 1 :_rev 1})
    (provided (couch "migration-db") => (list {:id 1 :_rev 1}))
    (provided (up? anything) => true))
  (fact "Is not a file"
    (try+
      (#'migration/validate-connection)
      (catch [:type :flycouchdb] {function-name :fn}
        function-name)) => "validate-connection"
    (provided (couch "migration-db") => anything)
    (provided (up? anything) => false)))

(fact "Run the migrations for a normal resource system"
  (let [folder-path (resource "migrations/correct/")
        flycouchdb (migration/flycouchdb folder-path)]
    (migration/migrate flycouchdb) => anything
    (provided (couch anything) => migration-db)
    (provided (up? anything) => true)
    (provided (exist? anything) => false)
    (provided (clutch/create! anything) => anything :times 1)
    (provided (create-view! anything anything anything anything) => anything :times 1)
    (provided (get-view anything anything anything) => '({:value {:version -1 :subversion -1}}))
    (provided (#'migration/update-counter! anything) => anything)
    (provided (edn/apply-functions anything) => anything :times 8)
    (provided (clutch/assoc! migration-db anything anything) => anything :times 8)))

(fact "Run the migrations when some of them had already ben run previously"
  (let [folder-path (resource "migrations/correct/")
        flycouchdb (migration/flycouchdb folder-path)]
    (migration/migrate flycouchdb) => anything
    (provided (couch anything) => migration-db)
    (provided (up? anything) => true)
    (provided (exist? anything) => false)
    (provided (clutch/create! anything) => anything :times 1)
    (provided (create-view! anything anything anything anything) => anything :times 1)
    (provided (get-view anything anything anything) => '({:value {:version 1 :subversion 133 :counter 4}}))
    (provided (#'migration/update-counter! anything) => anything)
    (provided (edn/apply-functions anything) => anything :times 5)
    (provided (clutch/assoc! migration-db anything anything) => anything :times 5)))

(fact "Run the migrations for a Jar resource"
  (let [folder-path (str "jar:" (resource "flycouchdb-example-0.1.0-SNAPSHOT-standalone.jar") "!/migrations/")
        flycouchdb (migration/flycouchdb folder-path)]
    (migration/migrate flycouchdb) => anything
    (provided (couch "migration-db") => migration-db)
    (provided (up? anything) => true)
    (provided (exist? anything) => false)
    (provided (clutch/create! anything) => anything :times 1)
    (provided (create-view! anything anything anything anything) => anything :times 1)
    (provided (get-view anything "migration-template" "order-migrations") => ())
    (provided (#'migration/update-counter! anything) => anything)
    (provided (edn/apply-functions anything) => anything :times 1)
    (provided (clutch/assoc! migration-db anything anything) => anything :times 1)
    (provided (resource anything) => (str folder-path "V1_1__Create-edu-db.edn"))))

(fact "Check that we extract the jar path correctly"
  (let [jar-file "jar:file:/Users/haduart/flycouchdb-example-0.1.0-SNAPSHOT-standalone.jar!/migrations/"
        response ["/Users/haduart/flycouchdb-example-0.1.0-SNAPSHOT-standalone.jar" "migrations/"]]
    (#'migration/extract-jar-path-and-folder (URL. jar-file)) => response))
