(ns devec.debug
  (:require [devec.core :as devec])
  (:import (clojure.lang PersistentVector PersistentVector$Node)
           (devec.core DoubleEndedVector)))

(def empty-devec @#'devec.core/empty-devec)

(def empty-node (.-root []))

(defn devec
  "Creates a new double-ended vector with the contents of coll."
  [coll]
  (reduce conj empty-devec coll))

(defmacro ^:private gen-devector-method [& params]
  (let [arr (with-meta (gensym "arr__") {:tag 'objects})]
    `(let [~arr (object-array ~(count params))]
       ~@(map-indexed (fn [i param]
                        `(aset ~arr ~i ~param))
                      params)
       (devec.core.DoubleEndedVector.
         (object-array 0) empty-node ~arr 0 0 0 5 ~(count params)
         nil ~(if params -1 1) ~(if params -1 (hash []))))))

(defn devector
  "Creates a new double-ended vector containing the given items."
  ([]
     (gen-devector-method))
  ([x1]
     (gen-devector-method x1))
  ([x1 x2]
     (gen-devector-method x1 x2))
  ([x1 x2 x3]
     (gen-devector-method x1 x2 x3))
  ([x1 x2 x3 x4]
     (gen-devector-method x1 x2 x3 x4))
  ([x1 x2 x3 x4 & xn]
     (loop [v  (devector x1 x2 x3 x4)
            xn xn]
       (if xn
         (recur (.cons ^clojure.lang.IPersistentCollection v (first xn))
                (next xn))
         v))))

(defn accessors-for [v]
  (condp identical? (class v)
    PersistentVector [(constantly (object-array 0))
                      #(.-root ^PersistentVector %)
                      #(.-shift ^PersistentVector %)
                      #(.-tail ^PersistentVector %)
                      (constantly 0)
                      (constantly 0)
                      #(- (count %) (alength ^objects (.-tail %)))]
    DoubleEndedVector [#(.-head ^DoubleEndedVector %)
                       #(.-trie ^DoubleEndedVector %)
                       #(.-shift ^DoubleEndedVector %)
                       #(.-tail ^DoubleEndedVector %)
                       #(.-headoff ^DoubleEndedVector %)
                       #(.-trieoff ^DoubleEndedVector %)
                       #(.-tailoff ^DoubleEndedVector %)]))

(defn dbg-vec [v]
  (let [[extract-head extract-root extract-shift extract-tail
         extract-headoff extract-trieoff extract-tailoff]
        (accessors-for v)
        head  (extract-head v)
        root  (extract-root v)
        shift (extract-shift v)
        tail  (extract-tail v)]
    (letfn [(go [indent shift i node]
              (when node
                (dotimes [_ indent]
                  (print "  "))
                (printf "%02d:%02d " shift i)
                (if (zero? shift)
                  (print ":" (vec (.-array ^PersistentVector$Node node))))
                (println)
                (if-not (zero? shift)
                  (dorun
                    (map-indexed (partial go (inc indent) (- shift 5))
                      (.-array ^PersistentVector$Node node))))))]
      (printf "%s (%d elements):\n" (.getName (class v)) (count v))
      (println
        "headoff" (extract-headoff v)
        "trieoff" (extract-trieoff v)
        "tailoff" (extract-tailoff v))
      (println "head:" (pr-str (vec head)))
      (go 0 shift 0 root)
      (println "tail:" (pr-str (vec tail))))))
