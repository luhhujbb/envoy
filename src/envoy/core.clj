(ns envoy.core
  (:require [cheshire.core :as json]
            [clojure.data :refer [diff]]
            [clojure.core.async :refer [go-loop go <! >! >!! alt! chan]]
            [org.httpkit.client :as http]
            [envoy.tools :as tools]
            [clojure.string :as string])
  (:import [java.util Base64]))

(def decoder (Base64/getDecoder))

(defn- recurse [path]
  (str path "?recurse"))

(defn- index-of [resp]
  (-> resp
      :headers
      :x-consul-index))

(defn- with-ops [ops]
  {:query-params (tools/remove-nils ops)})

(defn- read-index
  ([path]
   (read-index path {}))
  ([path ops]
  (-> (http/get path (with-ops ops))
      index-of)))

(defn- fromBase64 [s]
  (String. (.decode decoder s)))

(defn- read-values
  ([resp]
   (read-values resp true))
  ([{:keys [body]} to-keys?]
   ;; (println "body => " body)
   (into {}
         (for [{:keys [Key Value]} (json/parse-string body true)]
           [(if to-keys? (keyword Key) Key)
            (when Value (fromBase64 Value))]))))

(defn- find-consul-node [hosts]
     (let [at (atom -1)]
       #(nth hosts (mod (swap! at inc)
                        (count hosts)))))

(defn url-builder
  "Create an envoy kv-path builder"
  [{:keys [hosts port secure?]
    :or {hosts ["localhost"] port 8500 secure? false}
    :as conf}]
  (let [proto (if secure? "https://" "http://")
        consul-node (find-consul-node hosts)]
    (fn [& [path]]
      (let [node (consul-node)]
      (str proto node ":" port "/v1/kv" (when (seq path)
                                          (str "/" (tools/clean-slash path))))))))

(defn put
  ([path v]
   (put path v {}))
  ([path v ops]
   ;; (println "@(http/put" path (merge {:body v} (with-ops ops)))
   @(http/put path (merge {:body v} (with-ops ops)))))

(defn delete
  ([path]
   (delete path {}))
  ([path ops]
   @(http/delete (recurse path)
                 (with-ops ops))))

(defn get-all
  ([path]
   (get-all path {}))
  ([path {:keys [keywordize?] :as ops
          :or {keywordize? true}}]
   (-> @(http/get (recurse (tools/with-slash path))
                  (with-ops (dissoc ops :keywordize?)))
       (read-values keywordize?))))

(defn- start-watcher
  ([path fun stop?]
   (start-watcher path fun stop? {}))
  ([path fun stop? ops]
   (let [ch (chan)]
     (go-loop [index nil current (get-all path)]
              (http/get path
                        (with-ops (merge ops
                                         {:index (or index (read-index path ops))}))
                        #(>!! ch %))
              (alt!
                stop? ([_]
                       (prn "stopping" path "watcher"))
                ch ([resp]
                    (let [new-idx (index-of resp)
                          new-vs (read-values resp)]
                      (when (and index (not= new-idx index))               ;; first time there is no index
                        (when-let [changes (first (diff new-vs current))]
                          (fun changes)))
                      (recur new-idx new-vs))))))))

(defprotocol Stoppable
  (stop [this]))

(deftype Watcher [ch]
  Stoppable
  (stop [_]
    (>!! ch :done)))

(defn watch-path
  ([path fun]
   (watch-path path fun {}))
  ([path fun ops]
  (let [stop-ch (chan)]
    (start-watcher (recurse path) fun stop-ch ops)
    (Watcher. stop-ch))))

(defn consul->map
  [path & [{:keys [offset] :as ops}]]
   (-> (partial get-all path
                        (merge
                            (dissoc ops :offset)
                            {:keywordize? false}))
       (tools/props->map)
       (get-in (tools/cpath->kpath offset))))

(defn- overwrite-with
    [kv-path m & [ops]]
    (let [[consul-url sub-path]  (string/split kv-path #"kv" 2)
          update-kv-path (str consul-url "kv")
          kpath (tools/cpath->kpath sub-path)
          stored-map (reduce (fn [acc [k v]]
                               (merge acc (consul->map
                                            (str kv-path "/" (name k)))))
                               {} m)
         ;;to update correctly we take diff
          [to-add to-remove _] (diff m (get-in stored-map kpath))]
         ;;add
         (doseq [[k v] (tools/map->props to-add)]
             (put (str kv-path "/" k) (str v)))
         ;;remove
         (doseq [[k v] (tools/map->props to-remove)]
            (when (nil? (get-in to-add (tools/cpath->kpath k) nil))
                @(http/delete (str kv-path "/" k))))))

(defn map->consul
  [kv-path m & [{:keys [overwrite?] :or {overwrite? false} :as ops}]]
  (let [kv-path (tools/without-slash kv-path)]
    (if-not overwrite?
       (doseq [[k v] (tools/map->props m)]
          (put (str kv-path "/" k) (str v) (dissoc ops :overwrite?)))
       (overwrite-with kv-path m ops))))

(defn copy
  ([path from to]
   (copy path from to {}))
  ([path from to opts]
   (let [data (consul->map path
                           (merge opts {:offset from}))
         new-map (->> (tools/cpath->kpath to)
                      (tools/nest-map data))]
     (map->consul path
                  new-map
                  opts))))

(defn move
  ([path from to]
   (move path from to {}))
  ([path from to opts]
   (let [dpath (str (tools/with-slash path)
                    (-> (tools/without-slash from {:slash :first})
                        (tools/with-slash)))]
     (copy path from to opts)
     (delete dpath opts))))

(defn merge-with-consul
  ([m path]
   (merge-with-consul m path {}))
  ([m path ops]
   (if-let [consul (consul->map path ops)]
     (tools/merge-maps m consul)
     m)))
