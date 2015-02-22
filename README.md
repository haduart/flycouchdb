# FlyCouchDB [![alt text][1.1]][1]
[![Build Status](https://travis-ci.org/haduart/flycouchdb.svg)](https://travis-ci.org/haduart/flycouchdb) [![Dependency Status](https://www.versioneye.com/user/projects/54dafaf4c1bbbd9bd70003b1/badge.svg?style=flat)](https://www.versioneye.com/user/projects/54dafaf4c1bbbd9bd70003b1) [![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/haduart/flycouchdb?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge) [![Coverage Status](https://coveralls.io/repos/haduart/flycouchdb/badge.svg)](https://coveralls.io/r/haduart/flycouchdb)

### CouchDB Migrations Made Easy!
![alt text](https://raw.githubusercontent.com/haduart/flycouchdb/master/test/resources/Flying_red_couch.jpg "FlyCouchDB")

[1.1]: http://i.imgur.com/tXSoThF.png (twitter icon with padding)
[1]: https://twitter.com/flycouchdb
[2]: https://twitter.com/haduart
FlyCouchDB is a [clojure](http://clojure.org) migration tool for [Apache CouchDB](http://couchdb.apache.org/) inspired
in what [Flywaydb](http://flywaydb.org) does for a relational database.

And why would I need a migration tool for a flexible/schemaless database? because even if your database
is schemaless that doesn't mean that there's no schema or structure in your data:

*"Usually when you're talking to a database you want to get some specific pieces of data out of it:
I'd like the price, I'd like the quantity, I'd like the customer. As soon as you are doing that
what you are doing is setting up an implicit schema. You are assuming that an order has a price
field. You are assuming tha is called 'price', not cost, or 'price to customer' or whatever.
That implicit schema is still in place and you've got to manage that implicit schema in many
ways in a similar approach that you manage the relational database."*
From [Introduction to NoSQL](https://www.youtube.com/watch?v=qI_g07C_Q5I#t=11m30) by Martin Fowler.

FlyCouchDB helps you maintaining an ordered migration list, allowing you to forget which migration has been run
in a certain server. Just run the migrations and FlyCouchDB would start running from last ran migration:
no mistakes, no migrations applied twice, no problems! Your data would be always in the state that you want,
so you also can forget about adding hacks in your code depending if that field exist or not!

Evolve your database schema easily and reliably across all your instances!

## Current Version

[![Clojars Project](http://clojars.org/flycouchdb/latest-version.svg)](http://clojars.org/flycouchdb)

With Maven:

```bash
<dependency>
  <groupId>flycouchdb</groupId>
  <artifactId>flycouchdb</artifactId>
  <version>0.2.0</version>
</dependency>
```

With Gradle:
```bash
compile "flycouchdb:flycouchdb:0.2.0"
```

### Works on

Windows, Mac OSX, Linux, Java and Android

## Usage

You can run the migrations inside your own project or creating a separate one that will be run independently
whenever you execute it as an executable jar or as a deployable war.

For this example we will create a simple jar migration project. You can find a similar example project with
more advanced migrations here: [FlyCouchDB-example](https://github.com/haduart/flycouchdb-example).

First create a clojure project (it could also work inside a Java project or Scala):
```bash
lein new flycouchdb-example
```

In the `project.clj` add the dependency to flycouchdb with the latest stable version:

```clojure
(defproject flycouchdb-example "0.1.0-SNAPSHOT"
  :description "Example project for FlyCouchDB"
  :url "https://github.com/haduart/flycouchdb-example"
  :license {:name "BSD"
            :url "http://www.opensource.org/licenses/BSD-3-Clause"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [flycouchdb "0.2.0"]]
```

Create a migrations folder inside your resources folder and put there your migrations.

Then just create a clojure main entry point where you will run the migrations from that folder:

```clojure
(ns flycouchdbexample.core
  (:use [clojure.java.io :only (file resource)]
        [flycouchdb.migration :only (migrate flycouchdb)])
  (:gen-class))

(def folder-path (resource "migrations"))

(def flydb (flycouchdb folder-path))

(defn -main
  [& args]
  (do
    (println "Running migrations")
    (migrate flydb)
    (println "Done!")))
```

Build it:
```bash
lein do clean, uberjar
```

And run it:
```bash
 lein run
```
 or
```bash
 java -jar flycouchdb-example-0.1.0-SNAPSHOT-standalone.jar
```

## Migrations

Each migration is a file that contains an edn specific structure depending on the
action that you want to run. The file should strictly follow this format:

`V{VERSION_NUMBER}_{SUBVERSION_NUMBER}__MIGRATION-DESCRIPTION-TEXT.edn`

ex. `V1_131__Create_Database.edn`

The migrations will be run depending on the order of the version first, and within
migrations from the same version they will be ordered depending on the subversion
number.

So `V1_131__Create_Database.edn` will be run before `V1_132__Delete_Database.edn`,
and both of them will be run before `V2_1__Create_another_Database.edn`.

In each migration file it can only be one edn structure representing one action,
except of the compose actions that can execute multiple actions.

So far for the current version there's a minimum set of actions that you can execute:
- Create Database
- Delete Database
- Create View
- Rename Keys
- Edit Documents
- Insert Documents
- Composing actions

### Create Database

This action will create a database in CouchDB.

```clojure
{:dbname "edu-db"
 :action :create}
```

`:dbname` indicates the name of the new database that will be created.

### Delete Database

This action will delete a database in CouchDB with all the documents that contains.

```clojure
{:dbname "edu-db"
 :action :delete}
```

`:dbname` indicates the name of the new database that will be deleted.

### Create View

This action will create a view in CouchDB.

```clojure
{:dbname      "edu-db"
 :action      :create-view
 :create-view {:view-design   "view-design"
               :view-name     "view-name"
               :function-type "javascript"
               :view-function "function(doc) {if (doc.username && doc.username ==='eduard') {emit(doc._id, doc);}}"}}
```

`:dbname` indicates the name of the database where the view will be created.
`:view-design` indicates the name of the design template in CouchDB. Usually one
CouchDB design can contain multiple views, but there's a bug in the current [clutch](https://github.com/clojure-clutch/clutch)
library. So it's better to use a new design template per view.
`:view-name` indicates the name of the view in CouchDB.
`:function-type` this will indicate if the migration will be written in `javascript`,
`clojurescript` or `clojure`. So far only javascript is allowed.
`:view-function` this will be the javascript function that will create the view.

### Rename Keys

This action will rename using the rename-fun all the keys (all the id) that
matches the filter-fn. CouchDB does not support renaming the keys or teh version
number! so internally will delete the documents and will create them again with
the new key!

```clojure
{:dbname      "edu-db"
 :action      :rename-keys
 :rename-keys {:filter-fn (fn [x] true)
               :rename-fn (fn [{id :_id}] (str id "-new-end"))}}
```

`:filter-fn` Is a function that will test whether a document should be renamed or not.
Returning true or false. That function receives as an argument the document itself,
so it can check any entry inside the document.
`:rename-fn` In case the filter-fn returns true this function will be applied.
This function should return a string that will be the value of the new key.

### Edit Documents

This action will edit a document, adding a new entry, removing it or modifying one
whenever the filter-fn allows it.

```clojure
{:dbname       "edu-db"
 :action       :edit-entries
 :edit-entries {:filter-fn (fn [{count :count}] (< 10 count))
                :edit-fn (fn [entry] (assoc entry :new-key "new value"))}}
```

`:filter-fn` Is a function that will test whether a document should be edited or not.
Returning true or false. That function receives as an argument the document itself,
so it can check any entry inside the document.
`:edit-fn` In case the filter-fn returns true this function will be applied.
This function should return a string that will be the value of the new key.

### Insert Documents

This action will insert documents inside a database. Useful to create the initial
database or to create dummy data.

```clojure
{:dbname           "database-0"
 :action           :insert-documents
 :insert-documents {:insert-documents-fn
                    (fn [] [{:_id  "1"
                             :name "Eduard" :surname "Cespedes Borras"
                             :mail "haduart@gmail.com"}])}}
```

`:insert-documents-fn` Is an anonymous function that will return a vector with the
 documents that has to be inserted. It expects that the `:_id` will be the key of
 the document and this will be unique.

 Of course you can use any clojure code to generate this list of documents:

```clojure
{:dbname           "eduard-db"
 :action           :insert-documents
 :insert-documents {:insert-documents-fn
                    (fn [] (->>
                             (range 1 1000)
                             (mapv (fn [x] {:_id     (str x)
                                            :name    (str "user-" x)
                                            :mail    (str "user-" x "@gmail.com")
                                            :minimum (rand-int 500)
                                            :maximum (rand-int 1200)}))))}}
```

### Composing actions

This action allows you to execute multiple actions. You can even create them
in a programmatically way.

```clojure
{:action    :composite
 :composite {:composite-fn (fn [] [{:dbname "database-0" :action :create}
                                   {:dbname "database-0" :action :delete}
                                   {:dbname "database-2" :action :delete}
                                   {:dbname "database-3" :action :delete}])}}
```

`:composite-fn` Is an anonymous function that will return a vector of migrations or
actions.

Of course, like everywhere, you can use any clojure code to generate this list of actions:

```clojure
{:action    :composite
 :composite {:composite-fn (fn [] (->>
                                    10
                                    range
                                    (map (fn [x] {:dbname (str "database-" x)}))
                                    (mapv (fn [db] (assoc db :action :create)))))}}
```

It even allows you to do advanced database manipulations like never before!

```clojure
{:action    :composite
 :composite {:composite-fn (fn []
                             (let [db (be.dsquare.clutch/couch "eduard-db")
                                   user-view (be.dsquare.clutch/get-view db "view-design" "view-name")
                                   results-in-view (take (count user-view) user-view)
                                   averages (atom {})]
                               (do
                                 (->>
                                   results-in-view
                                   (map #(:value %))
                                   (map (fn [{:keys [maximum minimum id]}] {:_id id :average (double (/ (+ maximum minimum) 2))}))
                                   (mapv (fn [x] (swap! averages assoc (:_id x) (dissoc x :_id)))))

                                 [{:dbname       "eduard-db"
                                   :action       :edit-entries
                                   :edit-entries {:filter-fn (fn [{id :_id}] (not (nil? (get @averages id))))
                                                  :edit-fn   (fn [entry] (merge entry (get @averages (:_id entry))))}}])))}}
```



## Contributors

Appreciations go out to:

* [Eduard Cespedes Borras](https://github.com/haduart) [![alt text][1.1]][2]
* [Roberto Barchino Garrido](https://github.com/fisoide)
* [Igor Ruzanov](https://github.com/r00z)
* [Jeroen Hoekx](https://github.com/jhoekx)


## Sponsored
This project is sponsored by [D square N.V](http://dsquare.be)


## License

BSD.  See the LICENSE file at the root of this repository.
