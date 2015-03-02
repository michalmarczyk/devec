(ns devec.core-bench
  (:require [devec.core :as devec]
            [criterium.core :as c]
            [collection-check :refer [assert-equivalent-vectors]]
            [clojure.test :refer :all]))

(defn subst [m xs]
  (map #(if (contains? m %) (m %) %) xs))

(defmacro echo [expr]
  `(do
     (prn '~expr)
     ~expr))

(defmacro b [expr]
  (let [pvexpr (subst '{% pv} expr)
        dvexpr (subst '{% dv} expr)]
    `(do
       (if-not ~(contains? (meta &form) :nv)
         (assert-equivalent-vectors ~pvexpr ~dvexpr)
         (assert (= ~pvexpr ~dvexpr)))
       (echo (c/bench ~pvexpr))
       (echo (c/bench ~dvexpr))
       (println))))

(defn run-benchmarks []
  (let [pv (vec (range -1000000 2000000))
        dv (apply devec/conjl (vec (range 2000000)) (range -1 -1000001 -1))]
    (assert-equivalent-vectors pv dv)

    (println "basic vector ops")
    (println "================")
    ^:nv (b (nth % 1234))
    ^:nv (b (nth % 1500000))
    (b (conj % :foo))
    (b (assoc % 1234 :foo))
    (b (assoc % 1500000 :foo))
    (b (pop %))
    (println)

    (println "devec ops")
    (println "=========")
    (b (devec/conjl % :foo))
    (b (devec/popl %))
    (println)

    true))

(deftest test-bench
  (is (run-benchmarks)))
