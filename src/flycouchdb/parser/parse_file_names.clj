(ns flycouchdb.parser.parse-file-names
  (:use clojure.pprint
        [slingshot.slingshot :only [throw+ try+]])
  (:import [java.io File]
           [java.lang ClassNotFoundException]
           [clojure.lang ISeq]
           [java.net URI URL]))

(defn- validate-is-a-file
  "validates if it's a file"
  [^ISeq file]
  (try
    (when (.isFile file)
      file)
    (catch Exception _
      (throw+ {:type    :flycouchdb :fn "validate-is-a-file"
               :message "Is not a valid File"}))))

(defn- validate-file-can-be-read
  "Check that the permissions are ok for reading it"
  [^ISeq file]
  (if (.canRead file)
    file
    (throw+ {:type    :flycouchdb :fn "validate-file-can-be-read"
             :message (str "This file: " (.getName file) " can not be read")})))

(defn- generate-migration-file-structure
  "Create a map that contains all the properties from a migration file"
  [^ISeq file]
  {:file      file
   :file-name (.getName file)
   :source :file})

(defn- edn-file?
  "Checks if it's an edn file"
  [file-name]
  (let [total-file (count file-name)]
    (= (subs file-name (- total-file 4) total-file) ".edn")))

(defn- remove-file-extension
  "Remove the extension from the file name"
  ([file-name]
    (remove-file-extension file-name "edn"))
  ([file-name extension]
    (subs file-name 0 (- (count file-name) (+ 1 (count extension))))))

(defn- validate-edn-file
  "Validate if it's a correct edn file and retorn name"
  [{file-name :file-name}]
  (if (edn-file? file-name)
    {:name (remove-file-extension file-name)}
    (throw+ {:type    :flycouchdb :fn "validate-edn-file"
             :message (str "This file: " file-name " is not a correct edn file")})))

(defn- correct-version?
  "Checks if the version number is V{d}_{ddd}__
  Example: V1_101__ where this is gonna be the migration 101 from the first version"
  [^String file-name]
  (let [find-version (re-find #"V(\d+)_" file-name)]
    (not (nil? find-version))))

(defn- extract-migration-number
  "Extract version from migration name V1_"
  [^String name]
  (->>
    name
    (re-find #"V(\d+)_")
    second
    read-string))

(defn- correct-subversion-number?
  "Checks if the version number is V{d}_{ddd}__
  Example: V1_101__ where this is gonna be the migration 101 from the first version"
  [^String name]
  (let [find-version (re-find #"V(\d+)_(\d+)__[a-zA-Z]+" name)]
    (not (nil? find-version))))

(defn- extract-migration-subversion-number
  "Checks if the version number is V{d}_{ddd}__
  Example: V1_101__ where this is gonna be the migration 101 from the first version"
  [^String name]
  (->>
    name
    (re-find #"V(\d+)_(\d+)__[a-zA-Z]+")
    rest
    second
    read-string))

(defn- validate-migration-version
  "Validate if the version is correctly formated and returns it"
  [{name :name}]
  (if (correct-version? name)
    {:version (extract-migration-number name)}
    (throw+ {:type    :flycouchdb :fn "validate-migration-version"
             :message (str "This version of " name " is not valid!"
                        "\nExample: V1_131__Create_Database.edn")})))

(defn- validate-subversion-number
  [{name :name}]
  (if (correct-subversion-number? name)
    {:subversion (extract-migration-subversion-number name)}
    (throw+ {:type    :flycouchdb :fn "validate-subversion-number"
             :message (str "This sub-version of " name " is not valid!"
                        "\nExample: V1_131__Create_Database.edn")})))

(defn validate-migrations
  "This function will validate all the migration files in the folder and returns a structure like:
  {:version 1,
  :subversion 132,
  :name 'V1_132__Delete_Database',
  :file #<File /migrations/correct/V1_132__Delete_Database.edn>,
  :file-name 'V1_132__Delete_Database.edn'}"
  [^File location-folder]
  (do
    (println "Validating migrations")
    (pprint (rest (file-seq location-folder)))
    (try+
      (let [folder-seq (file-seq location-folder)]
        (->>
          folder-seq
          rest
          (map #(validate-is-a-file %))
          (map #(validate-file-can-be-read %))
          (map #(generate-migration-file-structure %))
          (map #(merge % (validate-edn-file %)))
          (map #(merge % (validate-migration-version %)))
          (map #(merge % (validate-subversion-number %)))))
      (catch ClassNotFoundException _
        (throw+ {:type    :flycouchdb :fn "validate-migrations"
                 :message (str "Something is wrong in this folder: " (.getName location-folder))})))))

(defn validate-migrations-jar
  "This function will validate all the migration files in the folder and returns a structure like:
  {:version 1,
  :subversion 132,
  :name 'V1_132__Delete_Database',
  :file #<BufferedInputStream java.io.BufferedInputStream@2b46504>,
  :file-name 'V1_132__Delete_Database.edn'}"
  [folder-seq]
  (do
    (println "Validating migrations in the JAR")
    (clojure.pprint/pprint folder-seq)
    (->>
      folder-seq
      (map #(merge % (validate-edn-file %)))
      (map #(merge % (validate-migration-version %)))
      (map #(merge % (validate-subversion-number %))))))
