(ns flycouchdb.parser.parser.parse-edn-structures-test
  (:use midje.sweet
        [slingshot.slingshot :only [throw+ try+]]
        [be.dsquare.clutch :only (couch drop! up? exist? create-view! take-all)]
        [com.ashafa.clutch :only (create!)])
  (:require [flycouchdb.parser.parse-edn-structures :as edn]
            [com.ashafa.clutch :as clutch]))

(fact "Create a function from the edn structure that will create a couchdb db"
  (let [create-database {:dbname "edu-db"
                         :action :create}]
    ((edn/parse-edn-structures create-database)) => anything
    (provided (couch "edu-db") => [{}])
    (provided (create! anything) => anything)))

(fact "Create a function from the edn structure that will delete a couchdb db"
  (let [delete-database {:dbname "edu-db"
                         :action :delete}]
    ((edn/parse-edn-structures delete-database)) => anything
    (provided (couch "edu-db") => [{}])
    (provided (drop! anything) => anything)))

(fact "Create a function from the edn structure that will create a couchdb view"
  (let [couchdb-entity [{}]
        javascript-function "function(doc) {if (doc.username && doc.username ==='eduard'') {emit(doc._id, doc);}}"
        create-view {:dbname      "edu-db"
                     :action      :create-view
                     :create-view {:view-design   "view-design"
                                   :view-name     "view-name"
                                   :function-type "javascript"
                                   :view-function javascript-function}}]
    ((edn/parse-edn-structures create-view)) => anything
    (provided (couch "edu-db") => couchdb-entity)
    (provided (create-view! couchdb-entity "view-design" "view-name" javascript-function)
      => anything)))

(let [couchdb-entries [["isvag-gemeten-sec-lucht-q"
                        {:_id   "isvag-gemeten-sec-lucht-q",
                         :_rev  "1-d4cee928236645421115a843a7711bc9",
                         :name  "isvag-gemeten-sec-lucht-q",
                         :count 9}]
                       ["isvag-1"
                        {:_id       "isvag-1",
                         :_rev      "2-d4cee928236645421115a843a7711bc9",
                         :name      "isvag-1-name",
                         :startDate "2010-01-28T15:26:43.721Z"
                         :count     30}]]]
  (fact "Create a function from the edn structure that RENAME key entries"
    (fact "We do not filter anything"
      (let [rename-keys {:dbname      "edu-db"
                         :action      :rename-keys
                         :rename-keys {:filter-fn (fn [x] true)
                                       :rename-fn (fn [x] (str x "-new-end"))}}]
        ((edn/parse-edn-structures rename-keys)) => anything
        (provided (couch "edu-db") => couchdb-entries)
        (provided (take-all anything) => couchdb-entries)
        (provided (clutch/dissoc! anything anything) => anything :times 2)
        (provided (clutch/assoc! anything anything anything) => anything :times 2)))

    (fact "We filter by one property"
      (let [rename-keys {:dbname      "edu-db"
                         :action      :rename-keys
                         :rename-keys {:filter-fn (fn [{count :count}] (< 10 count))
                                       :rename-fn (fn [{id :_id}] (str id "-new-end"))}}]
        ((edn/parse-edn-structures rename-keys)) => anything
        (provided (couch "edu-db") => couchdb-entries)
        (provided (take-all anything) => couchdb-entries)
        (provided (clutch/dissoc! anything "isvag-1") => anything :times 1)
        (provided (clutch/assoc! anything "isvag-1-new-end" {:name      "isvag-1-name",
                                                             :startDate "2010-01-28T15:26:43.721Z"
                                                             :count     30}) => anything :times 1))))

  (fact "Create a function from the edn structure that EDIT key entries"
    (let [edit-entries {:dbname       "edu-db"
                        :action       :edit-entries
                        :edit-entries {:filter-fn (fn [{count :count}] (< 10 count))
                                       :edit-fn   (fn [entry] (assoc entry :new-key "new value"))}}]
      ((edn/parse-edn-structures edit-entries)) => anything
      (provided (couch "edu-db") => couchdb-entries)
      (provided (take-all anything) => couchdb-entries)
      (provided (clutch/assoc! anything "isvag-1" {:_id       "isvag-1",
                                                   :_rev      "2-d4cee928236645421115a843a7711bc9",
                                                   :name      "isvag-1-name",
                                                   :startDate "2010-01-28T15:26:43.721Z"
                                                   :count     30,
                                                   :new-key   "new value"}) => anything :times 1))))

(fact "Using the composite structure to create 10 databases"
  (let [composite-edn {:action    :composite
                       :composite {:composite-fn
                                   (fn [] (->>
                                            10
                                            range
                                            (map (fn [x] {:dbname (str "database-" x)}))
                                            (mapv (fn [db] (assoc db :action :create)))))}}]
    ((edn/parse-edn-structures composite-edn)) => anything
    (provided (couch anything) => [{}])
    (provided (create! anything) => anything :times 10)))

(fact "Insert documents in a database"
  (let [db [{}]
        composite-edn {:dbname           "edu-db"
                       :action           :insert-documents
                       :insert-documents {:insert-documents-fn
                                          (fn [] [{:_id  "1"
                                                   :name "Eduard" :surname "Cespedes Borras"
                                                   :mail "haduart@gmail.com"}])}}]
    ((edn/parse-edn-structures composite-edn)) => anything
    (provided (couch anything) => db)
    (provided (clutch/assoc! db "1" {:_id  "1"
                                     :name "Eduard" :surname "Cespedes Borras"
                                     :mail "haduart@gmail.com"}) => anything)))

(fact "Test apply functions"
  (edn/apply-functions {:edn-function  (fn [] "hola que aze")
                        :edn-structure {:dbname "edu-db" :action :create}
                        :file-name     "V1_131__Create_Database.edn"
                        :ts            "2015-02-04T08:02:01.977Z"}) => "hola que aze")

