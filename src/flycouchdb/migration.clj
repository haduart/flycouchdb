(ns flycouchdb.migration
  (:use [slingshot.slingshot :only [throw+ try+]]
        [flycouchdb.parser.parse-file-names :only (validate-migrations validate-migrations-jar)]
        [flycouchdb.parser.parse-edn-structures :only (parse-edn-structures apply-functions)]
        [be.dsquare.clutch :only (couch up? exist?)]
        [clojure.java.io :only (file input-stream make-input-stream resource)]
        [clj-time.core :only (now)])
  (:require [com.ashafa.clutch :as clutch]
            [clojure.edn :as edn])
  (:import [java.net URL]
           [java.util.jar JarFile]))

(def migration-db "migration-db")

(defrecord FlyCouchDB [^String location-folder
                       ^String datasource-url
                       ^String datasource-user
                       ^String datasource-password])

(defn- validate-connection
  "Validates the connection"
  []
  (let [db (couch migration-db)]
    (if (up? db)
      db
      (throw+ {:type    :flycouchdb :fn "validate-connection"
               :message (str "Trying to connect to CouchDB. Connection is down!")}))))

(defn- create-if-not-exists
  "Validates the connection"
  []
  (let [db (couch migration-db)]
    (when-not (exist? db)
      (clutch/create! db))))

(defn- resources-in-jar?
  "Check if the resources are inside a jar file or not"
  [^URL migration-resource]
  (let [migration-path-string (str migration-resource)]
    (->>
      migration-path-string
      (re-find #"jar:file:*")
      nil?
      not)))

(defn- migration-list [[^java.lang.String jar-path ^java.lang.String migration-folder]]
  (->>
    jar-path
    JarFile.
    .entries
    enumeration-seq
    (filter #(re-find (re-pattern (str migration-folder "*")) (.getName %)))
    (filter #(re-find #"\.edn{1}|\.clj{1}" (.getName %)))
    (mapv (fn [x]
            {:file-name (subs (.getName x) (count migration-folder) (count (.getName x)))
             :file      (resource (.getName x))
             :source    :jar}))))

(defn- extract-jar-path-and-folder
  "It splits the jar path and the internal folder from the jar URL
  from jar:file:/Users/haduart/flycouchdb-example-0.1.0-SNAPSHOT-standalone.jar!/migrations/
  to ['/Users/haduart/flycouchdb-example-0.1.0-SNAPSHOT-standalone.jar' 'migrations/']"
  [^URL jar-url]
  (let [jar-file (str jar-url)
        jar-string (subs jar-file (count "jar:file:") (count jar-file))
        jar-position (.indexOf jar-string ".jar!")]
    [(str (subs jar-string 0 jar-position) ".jar")
     (subs jar-string (+ jar-position (count ".jar!/")) (count jar-string))]))

(defmulti slurp-edn-structures :source)

(defmethod slurp-edn-structures :file [migration]
  (assoc migration :edn-structure (edn/read-string (slurp (:file migration)))))

(defmethod slurp-edn-structures :jar [migration]
  (assoc migration :edn-structure (edn/read-string (slurp (input-stream (:file migration))))))

(defmulti validate-migrations-folder
  (fn [{location-folder :location-folder}] (resources-in-jar? location-folder)))

(defmethod validate-migrations-folder true [{location-folder :location-folder}]
  (->
    location-folder
    extract-jar-path-and-folder
    migration-list
    validate-migrations-jar))

(defmethod validate-migrations-folder false [{location-folder :location-folder}]
  (->
    location-folder
    file
    validate-migrations))

(defn migrate
  "Start the migration process"
  [^FlyCouchDB flycouchdb]
  (do
    (validate-connection)
    (create-if-not-exists)
    (->>
      (validate-migrations-folder flycouchdb)
      (map (fn [migration] (slurp-edn-structures migration)))
      (map (fn [migration] (assoc migration :edn-function (parse-edn-structures (:edn-structure migration)))))
      (map (fn [migration] (assoc migration :ts (str (now)))))
      (mapv (fn [migration]
              (do
                (apply-functions migration)
                (clojure.pprint/pprint migration)
                (let [db (couch migration-db)
                      edn-migration (-> migration
                                      (dissoc :file :edn-function :file :edn-structure)
                                      (assoc :dbname (:dbname (:edn-structure migration)) :action (:action (:edn-structure migration))))]
                  (clutch/assoc! db (:name migration) edn-migration))))))))

(defn flycouchdb
  "Returns an instance of an implementation of FlyCouchDB"
  [^String location-folder]
  (FlyCouchDB. location-folder nil nil nil))