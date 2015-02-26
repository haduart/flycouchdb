(ns flycouchdb.parser.parse-migration-names
  (:use clojure.pprint
        [slingshot.slingshot :only [throw+ try+]]))

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

(defn generate-migrations-structure
  "This generates the following structure per each migration:
  {:version 1,
  :subversion 132,
  :name 'V1_132__Delete_Database',
  :file #<BufferedInputStream java.io.BufferedInputStream@2b46504>,
  :file-name 'V1_132__Delete_Database.edn'}"
  [folder-seq]
  (->>
    folder-seq
    (map #(merge % (validate-edn-file %)))
    (map #(merge % (validate-migration-version %)))
    (map #(merge % (validate-subversion-number %)))))


