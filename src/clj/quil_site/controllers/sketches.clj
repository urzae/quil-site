(ns quil-site.controllers.sketches
  (:require [compojure.core :refer :all :exclude [routes]]
            [compojure.route :refer [not-found]]
            [clojure.java.io :as io]
            [ring.util.response :as resp]
            [quil-site.views.sketches :as views]
            [quil-site.examples :refer [get-example-source]]
            [quil-site.storage :as s]
            [quil-site.snippets :as snippets]
            [clojure.core.cache :as c]
            [pandect.algo.sha256 :as sha256]))

(def test-source
"(ns my.core
  (:require [quil.core :as q :include-macros true]))

(defn setup []
  (q/frame-rate 60)
  (q/color-mode :rgb))

(defn draw []
  (q/background 255 255 255)
  (let [t (q/millis)]
    (q/fill 255 0 0)
    (q/ellipse 300 300 200 200)))

(q/defsketch my
  :host \"host\"
  :size [600 600]
  :setup setup
  :draw draw)")

(def id (atom 0))
(def sketches (atom (c/lru-cache-factory {} :threshold 128)))

(def id-caches (atom (c/lru-cache-factory {} :threshold 512)))

(defn get-example [id]
  (let [name (subs id (count "example_"))]
    (get-example-source name)))

(defn get-snippet [id]
  (let [name (subs id (count "snippet_"))]
    (snippets/get-snippet-as-full-sketch-source name)))

(defn get-id-for-source [source]
  (let [hash (sha256/sha256 source)]
    (if-let [id (c/lookup @id-caches hash)]
      (do (swap! id-caches c/hit hash)
          id)
      (let [id (s/save-source source)]
        (swap! id-caches assoc hash id)
        id))))

(defn get-source-for-id [id]
  (cond
    (.startsWith id "example_") (get-example id)
    (.startsWith id "snippet_") (get-snippet id)
    (= id "basic") test-source
    :default (s/load-source id)))

(defn sketch-from-source [source id]
  {:cljs source
   :id (or id (get-id-for-source source))})

(defn get-sketch [id]
  (if-let [sketch (c/lookup @sketches id)]
    (do (swap! sketches c/hit id)
        sketch)
    (if-let [source (get-source-for-id id)]
      (let [sketch (sketch-from-source source id)]
        (do (swap! sketches assoc id sketch)
            sketch))
      nil)))

(defn sketch-info [id]
  (let [default {:cljs test-source}
        sketch (select-keys (or (get-sketch id) default)
                            [:id :cljs :size])]
    (-> sketch
        (assoc :result :ok)
        (resp/response))))

(defn create-sketch [sketch]
  (try
    (let [sketch (sketch-from-source (:cljs sketch) nil)
          id (:id sketch)
          now (.format (java.text.SimpleDateFormat. "yyMMdd-HHmm")
                       (java.util.Date.))
          filename (str "shared/" now "-" id ".clj")]
      (swap! sketches assoc id sketch)
      (spit filename (:cljs sketch))
      (sketch-info id))
    (catch java.lang.Exception e
      (.printStackTrace e)
      (resp/response {:result :error
                      :message "Server error"}))))

(defroutes routes
  (context "/sketches" []
    (GET "/create" [] (views/sketch-page "basic" false))
    (GET "/info/:id" [id] (sketch-info id))
    (GET "/show/:id" [id] (views/sketch-page id false))
    (GET "/local/:id" [id] (views/sketch-page id true))
    (POST "/create" req (create-sketch (:body req)))))
