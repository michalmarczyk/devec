(ns devec.core-test
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [collection-check :refer [assert-vector-like
                                      assert-equivalent-vectors]]
            [devec.core :as devec]
            [devec.debug :refer [devector]]))

(deftest collection-check
  (is (assert-vector-like (devector) gen/int))
  (is (every? nil? (.-array ^clojure.lang.PersistentVector$Node
                            (.-root ^clojure.lang.PersistentVector (vector))))))

(deftest examples
  (let [pv (vec (range -1000000 2000000))
        dv (apply devec/conjl (vec (range 2000000)) (range -1 -1000001 -1))]
    (assert-equivalent-vectors pv dv)
    (assert-equivalent-vectors (conj pv :foo) (conj dv :foo))
    (assert-equivalent-vectors
      (into pv (range 1000))
      (into dv (range 1000)))
    (assert-equivalent-vectors (assoc pv 1234 :foo) (assoc dv 1234 :foo))
    (assert-equivalent-vectors (assoc pv 1500000 :foo) (assoc dv 1500000 :foo))
    (assert-equivalent-vectors (pop pv) (pop dv))
    (assert-equivalent-vectors (devec/conjl pv :foo) (devec/conjl dv :foo))
    (assert-equivalent-vectors (devec/popl pv) (devec/popl dv))
    (assert-equivalent-vectors
      (vec (concat (reverse (range 1000)) (range 1000)))
      (as-> (vec (range 2000000)) v
        (apply devec/conjl v (range 2000000))
        (iterate pop v)
        (nth v 1999000)
        (iterate devec/popl v)
        (nth v 1999000)))))

(defn prep-pv [l m r]
  (into (into l m) r))

(defn prep-dv [l m r]
  (into (apply devec/conjl m (rseq l)) r))

(defspec check-conjl-before-conj 1000
  (prop/for-all [l (gen/vector gen/int)
                 m (gen/vector gen/int)
                 r (gen/vector gen/int)]
    (let [pv (prep-pv l m r)
          dv (prep-dv l m r)]
      (assert-equivalent-vectors pv dv)
      true)))

(defspec check-pop 1000
  (prop/for-all [l (gen/not-empty (gen/vector gen/int))
                 m (gen/not-empty (gen/vector gen/int))
                 r (gen/not-empty (gen/vector gen/int))]
    (let [pv (prep-pv l m r)
          dv (prep-dv l m r)]
      (dorun
        (map assert-equivalent-vectors
          (take 4 (iterate pop pv))
          (take 4 (iterate pop dv))))
      true)))
