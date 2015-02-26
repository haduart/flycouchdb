(ns flycouchdb.scanner
  (:use [clojure.java.io :only (file resource)]
        [slingshot.slingshot :only [throw+ try+]])
  (:import [java.net URL URLDecoder]
           [java.io File]
           [java.util.jar JarFile]
           [org.jboss.vfs VFS VirtualFile]
           [clojure.lang ISeq]))

(defn- validate-file-can-be-read
  "Check that the permissions are ok for reading it"
  [^ISeq file]
  (if (.canRead file)
    file
    (throw+ {:type    :flycouchdb :fn "validate-file-can-be-read"
             :message (str "This file: " (.getName file) " can not be read")})))

(defn- generate-basic-structure-from-file
  "Create a map that contains all the properties from a migration file"
  [^ISeq file]
  {:file-name (.getName file)
   :file      file
   :source    :file})

(defn- validate-is-a-file
  "validates if it's a file"
  [^ISeq file]
  (try
    (when (.isFile file)
      file)
    (catch Exception _
      (throw+ {:type    :flycouchdb :fn "validate-is-a-file"
               :message "Is not a valid File"}))))

(defn extract-file-migrations
  "Extracting the migrations from a file/directory resource"
  [^File location-folder]
  (let [folder-seq (file-seq location-folder)]
    (->>
      folder-seq
      rest
      (map #(validate-is-a-file %))
      (map #(validate-file-can-be-read %))
      (map #(generate-basic-structure-from-file %)))))

(defn generate-basic-structure-from-vfs
  "Create a map that contains all the properties from a Virtual File System"
  [^VirtualFile v]
  {:file-name (.getName v)
   :file      v
   :source    :vfs})

(defn- extract-vfs-migrations
  "Extracting the migrations from a VFS resource"
  [^String location-folder]
  (->>
    location-folder
    .getPath
    (. URLDecoder decode)
    File.
    .getAbsolutePath
    (. VFS getChild)
    .getChildren
    (mapv generate-basic-structure-from-vfs)))

(defn- generate-basic-structure-from-jar
  "Create a map that contains all the properties from a JAR file"
  [migration-folder jar-entry]
  {:file-name (subs (.getName jar-entry) (count migration-folder) (count (.getName jar-entry)))
   :file      (resource (.getName jar-entry))
   :source    :jar})

(defn- extract-jar-migrations
  "Extracting the migrations from a JAR resource"
  [[^String jar-path ^String migration-folder]]
  (->>
    jar-path
    JarFile.
    .entries
    enumeration-seq
    (filter #(re-find (re-pattern (str migration-folder "*")) (.getName %)))
    (filter #(re-find #"\.edn{1}|\.clj{1}" (.getName %)))
    (mapv (partial generate-basic-structure-from-jar migration-folder))))

(defn- parse-jar-path-and-folder
  "It splits the jar path and the internal folder from the jar URL
  from jar:file:/Users/haduart/flycouchdb-example-0.1.0-SNAPSHOT-standalone.jar!/migrations/
  to ['/Users/haduart/flycouchdb-example-0.1.0-SNAPSHOT-standalone.jar' 'migrations/']"
  [^URL jar-url]
  (let [jar-file (str jar-url)
        jar-string (subs jar-file (count "jar:file:") (count jar-file))
        jar-position (.indexOf jar-string ".jar!")]
    [(str (subs jar-string 0 jar-position) ".jar")
     (subs jar-string (+ jar-position (count ".jar!/")) (count jar-string))]))

(defn- get-url-protocol
  "Wraps around the Java call. Just to make the function testable"
  [location-folder]
  (.getProtocol location-folder))

(defmulti extract-migrations
  (fn [{location-folder :location-folder}] (get-url-protocol location-folder)))

(defmethod extract-migrations "jar" [{location-folder :location-folder}]
  (->
    location-folder
    parse-jar-path-and-folder
    extract-jar-migrations))

(defmethod extract-migrations "file" [{location-folder :location-folder}]
  (->
    location-folder
    file
    extract-file-migrations))

(defmethod extract-migrations "vfs" [{location-folder :location-folder}]
  (->
    location-folder
    extract-vfs-migrations))
