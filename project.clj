(defproject devec "0.0.1"
  :description "Double-Ended Vectors for Clojure"
  :url "https://github.com/michalmarczyk/devec"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]]
  :jvm-opts ^:replace ["-XX:+UseG1GC"]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.7.0"]
                                  [collection-check "0.1.4"]
                                  [criterium "0.4.2"]]}})
