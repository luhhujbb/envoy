(defproject luhhujbb/envoy "0.3.1"
  :description "linkfluence fork of envoy"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.slf4j/slf4j-api "1.7.5"]
                 [org.slf4j/slf4j-log4j12 "1.7.5"]
                 [log4j "1.2.17"]
                 [org.clojure/tools.logging "0.2.6"]
                 [cheshire "5.9.0"]
                 [org.clojure/core.async "0.4.500"]
                 [org.clojure/algo.generic "0.1.3"]
                 [http-kit "2.3.0"]]
  :aot :all)
