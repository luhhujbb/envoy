(defproject linkfluence/envoy "0.2.5"
  :description "linkfluence fork of envoy"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.slf4j/slf4j-api "1.7.5"]
                 [org.slf4j/slf4j-log4j12 "1.7.5"]
                 [log4j "1.2.17"]
                 [org.clojure/tools.logging "0.2.6"]
                 [cheshire "5.8.0"]
                 [org.clojure/core.async "0.3.443"]
                 [http-kit "2.2.0"]]
  :repositories {"Linkfluence repo" "http://artifactory-i-7ab5159d.infra.aws.rtgi.eu:8081/artifactory/libs-release-local"}
  :aot :all)
