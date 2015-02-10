(ns flycouchdb.parser.parse-edn-structures
  (:use [be.dsquare.clutch :only (couch drop! up? exist? create-view! take-all)]
        [com.ashafa.clutch :only (create!)]
        [slingshot.slingshot :only [throw+ try+]])
  (:require [com.ashafa.clutch :as clutch]))

(defmulti parse-edn-structures :action)

(defmethod parse-edn-structures :create [{dbname :dbname}]
  (fn [] (let [couchdb (couch dbname)]
           (create! couchdb))))

(defmethod parse-edn-structures :delete [{dbname :dbname}]
  (fn [] (let [couchdb (couch dbname)]
           (drop! couchdb))))

(defmethod parse-edn-structures :create-view
  [{dbname :dbname {:keys [view-design view-name view-function]} :create-view}]
  (fn [] (let [couchdb (couch dbname)]
           (create-view! couchdb view-design view-name view-function))))

(defmethod parse-edn-structures :rename-keys
  [{dbname :dbname {:keys [filter-fn rename-fn]} :rename-keys}]
  (fn [] (let [couchdb (couch dbname)]
           (->>
             couchdb
             take-all
             (map #(second %))
             (filter (eval filter-fn))
             (map (fn [entry]
                    {:old_id        (:_id entry)
                     :new_id        ((eval rename-fn) entry)
                     :document-body (dissoc entry :_id :_rev)}))
             (mapv (fn [{:keys [old_id new_id document-body]}]
                     (do
                       (clutch/dissoc! couchdb old_id)
                       (clutch/assoc! couchdb new_id document-body))))))))

(defmethod parse-edn-structures :edit-entries
  [{dbname :dbname {:keys [filter-fn edit-fn]} :edit-entries}]
  (fn [] (let [couchdb (couch dbname)]
           (->>
             couchdb
             take-all
             (map #(second %))
             (filter (eval filter-fn))
             (mapv (fn [entry] (clutch/assoc! couchdb (:_id entry) ((eval edit-fn) entry))))))))

(defn apply-functions
  "Apply the anonymous functions that were created in this namespace so that
  there is no problem with the imports."
  [{:keys [edn-function edn-structure file-name ts]}]
  (edn-function))
