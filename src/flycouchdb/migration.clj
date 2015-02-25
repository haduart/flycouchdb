(ns flycouchdb.migration
  (:use [slingshot.slingshot :only [throw+ try+]]
        [flycouchdb.parser.parse-file-names :only (validate-migrations validate-migrations-jar)]
        [flycouchdb.parser.parse-edn-structures :only (parse-edn-structures apply-functions)]
        [be.dsquare.clutch :only (couch up? exist? create-view! get-view)]
        [clojure.java.io :only (file input-stream make-input-stream resource)]
        [clj-time.core :only (now)])
  (:require [com.ashafa.clutch :as clutch])
  (:import [java.net URL URLDecoder]
           [java.util.jar JarFile]
           [java.io File]
           [org.jboss.vfs VFS VirtualFile VirtualFileFilter]))

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

(defn- jar-migration-list [[^java.lang.String jar-path ^java.lang.String migration-folder]]
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

(defn- vfs-migration-list [^java.lang.String location-folder]
  (->>
    location-folder
    .getPath
    (. URLDecoder decode)
    File.
    .getAbsolutePath
    (. VFS getChild)
    .getChildren
    (mapv (fn [^VirtualFile v]
            {:file-name (.getName v)
             :file      v
             :source    :vfs}))))

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
  (assoc migration :edn-structure (read-string (slurp (:file migration)))))

(defmethod slurp-edn-structures :jar [migration]
  (assoc migration :edn-structure (read-string (slurp (input-stream (:file migration))))))

(defmethod slurp-edn-structures :vfs [migration]
  (assoc migration :edn-structure (read-string (slurp (.openStream (:file migration))))))

(defn get-url-protocol
  "Wraps around the Java call. Just to make the function testable"
  [location-folder]
  (.getProtocol location-folder))

(defmulti validate-migrations-folder
  (fn [{location-folder :location-folder}] (get-url-protocol location-folder)))

(defmethod validate-migrations-folder "jar" [{location-folder :location-folder}]
  (->
    location-folder
    extract-jar-path-and-folder
    jar-migration-list
    validate-migrations-jar))

(defmethod validate-migrations-folder "file" [{location-folder :location-folder}]
  (->
    location-folder
    file
    validate-migrations))

(defmethod validate-migrations-folder "vfs" [{location-folder :location-folder}]
  (->
    location-folder
    vfs-migration-list
    validate-migrations-jar))


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
  (do
    (println (str "Counter: " counter))
    (reset! migration-counter counter)))

(defn migrate
  "Start the migration process"
  [^FlyCouchDB flycouchdb]
  (do
    (validate-connection)
    (create-if-not-exists)
    (let [last-migration (->
                           "migration-db"
                           couch
                           (get-view "migration-template" "order-migrations")
                           last
                           :value
                           (#(if (nil? %)
                              {:version -1 :subversion -1 :counter 0}
                              %)))]
      (do
        (update-counter! last-migration)
        (->>
          (validate-migrations-folder flycouchdb)
          (sort-by (columns [:version :subversion]))

          (filter #(= (compare-by-version-subversion % last-migration) 1))

          (map (fn [migration] (slurp-edn-structures migration)))
          (map (fn [migration] (assoc migration :edn-function (parse-edn-structures (:edn-structure migration)))))
          (map (fn [migration] (assoc migration :ts (str (now)))))
          (map (fn [migration] (assoc migration :counter (swap! migration-counter inc))))
          (mapv (fn [migration]
                  (do
                    (apply-functions migration)
                    (clojure.pprint/pprint migration)
                    (let [db (couch migration-db)
                          edn-migration (-> migration
                                          (dissoc :file :edn-function :file :edn-structure)
                                          (assoc :dbname (:dbname (:edn-structure migration)) :action (:action (:edn-structure migration))))]
                      (clutch/assoc! db (:name migration) edn-migration))))))))))

(defn flycouchdb
  "Returns an instance of an implementation of FlyCouchDB"
  [^String location-folder]
  (FlyCouchDB. location-folder nil nil nil))
