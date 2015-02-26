(ns flycouchdb.migration
  (:use [flycouchdb.parser.parse-edn-structures :only (parse-edn-structures apply-functions)]
        [flycouchdb.parser.parse-migration-names :only (generate-migrations-structure)]
        [flycouchdb.scanner :only (extract-migrations)]
        [be.dsquare.clutch :only (couch up? exist? create-view! get-view)]
        [clojure.java.io :only (input-stream)]
        [clj-time.core :only (now)]
        [slingshot.slingshot :only [throw+ try+]])
  (:require [com.ashafa.clutch :as clutch]))

(def migration-db "migration-db")

(def migration-counter (atom 0))

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
  "Creates the migration database in CouchDB in case it does not exist.
  The migration database is named 'migration-db', and is a hardcoded name so far."
  []
  (let [db (couch migration-db)]
    (when-not (exist? db)
      (clutch/create! db)
      (create-view! db
        "migration-template"
        "order-migrations"
        "function(doc) {if (doc.counter) {emit(doc.counter, doc);}}"))))

(defmulti slurp-edn-structures :source)

(defmethod slurp-edn-structures :file [migration]
  (assoc migration :edn-structure (read-string (slurp (:file migration)))))

(defmethod slurp-edn-structures :jar [migration]
  (assoc migration :edn-structure (read-string (slurp (input-stream (:file migration))))))

(defmethod slurp-edn-structures :vfs [migration]
  (assoc migration :edn-structure (read-string (slurp (.openStream (:file migration))))))

(defn- columns [column-names]
  (fn [row]
    (vec (map row column-names))))

(defn- compare-by-version-subversion
  "We compare by :version and subversion"
  [{version-a :version subversion-a :subversion}
   {version-b :version subversion-b :subversion}]
  (cond
    (< version-a version-b) -1
    (> version-a version-b) 1
    (< subversion-a subversion-b) -1
    (> subversion-a subversion-b) 1
    :else 0))

(defn- update-counter!
  "Update the counter with the last migration that was run"
  [{counter :counter :or {:counter 0}}]
  (reset! migration-counter counter))

(defn- get-last-migration
  "Getting the last migration from CouchDB, ordered by the counter entry"
  []
  (->
    migration-db
    couch
    (get-view "migration-template" "order-migrations")
    last
    :value
    (#(if (nil? %)
       {:version -1 :subversion -1 :counter 0}
       %))))

(defn migrate
  "Start the migration process"
  [^FlyCouchDB flycouchdb]
  (do
    (validate-connection)
    (create-if-not-exists)
    (let [last-migration (get-last-migration)]
      (update-counter! last-migration)
      (->>
        flycouchdb
        extract-migrations
        generate-migrations-structure

        (sort-by (columns [:version :subversion]))

        (filter #(= (compare-by-version-subversion % last-migration) 1))

        (map (fn [migration] (slurp-edn-structures migration)))
        (map (fn [migration] (assoc migration :edn-function (parse-edn-structures (:edn-structure migration)))))
        (map (fn [migration] (assoc migration :ts (str (now)))))
        (map (fn [migration] (assoc migration :counter (swap! migration-counter inc))))
        (mapv (fn [migration]
                (do
                  (apply-functions migration)
                  (println (:name migration))
                  (let [db (couch migration-db)
                        edn-migration (-> migration
                                        (dissoc :file :edn-function :file :edn-structure)
                                        (assoc :dbname (:dbname (:edn-structure migration)) :action (:action (:edn-structure migration))))]
                    (clutch/assoc! db (:name migration) edn-migration)))))))))

(defn flycouchdb
  "Returns an instance of an implementation of FlyCouchDB"
  [^String location-folder]
  (FlyCouchDB. location-folder nil nil nil))
