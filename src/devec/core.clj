(ns devec.core
  #_(:refer-clojure :exclude [subvec])
  (:import (clojure.lang Util RT)))

(defprotocol AsDoubleEndedVector
  (-as-devec [this]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn ^:private throw-unsupported []
  (throw (UnsupportedOperationException.)))

(defmacro ^:private compile-if [test then else]
  (if (eval test)
    then
    else))

(def ^:private empty-node
  clojure.lang.PersistentVector/EMPTY_NODE)

(def ^:private empty-array
  (object-array 0))

#_
(gen-interface
  :name    devec.core.IReversibleVector
  :extends [clojure.lang.IPersistentVector]
  :methods
  [[rvec [] devec.core.IReversibleVector]])

#_
(gen-interface
  :name    devec.core.ISliceableVector
  :extends [clojure.lang.IPersistentVector]
  :methods
  [[subvec [int] devec.core.ISliceableVector]
   [subvec [int int] devec.core.ISliceableVector]])

(gen-interface
  :name    devec.core.IDoubleEndedVector
  :extends [clojure.lang.IPersistentVector]
  :methods
  [[consLeft [Object] devec.core.IDoubleEndedVector]
   [popLeft [] Object]])

(gen-interface
  :name devec.core.IDoubleEndedVectorImpl
  :methods
  [[trieArrayFor [int] "[Ljava.lang.Object;"]
   [popTail [int clojure.lang.PersistentVector$Node]
    clojure.lang.PersistentVector$Node]
   [popHead [int clojure.lang.PersistentVector$Node]
    clojure.lang.PersistentVector$Node]
   [assocInTrie [int clojure.lang.PersistentVector$Node int Object] Object]
   [newPath [int Object] Object]
   [newPathRight [int Object] Object]
   [pushTail [int clojure.lang.PersistentVector$Node Object] Object]
   [pushHead [int clojure.lang.PersistentVector$Node Object] Object]])

(declare empty-devec #_->ReversedDoubleEndedVector)

(deftype DoubleEndedVector [^objects head
                            trie
                            ^objects tail
                            ^int headoff
                            ^int trieoff
                            ^int tailoff
                            ^int shift
                            ^int cnt
                            ^clojure.lang.IPersistentMap _meta
                            ^:unsynchronized-mutable ^int _hash
                            ^:unsynchronized-mutable ^int _hasheq]
  AsDoubleEndedVector
  (-as-devec [this]
    this)

  Object
  (equals [this that]
    (cond
      (identical? this that) true

      (or (instance? clojure.lang.IPersistentVector that)
          (instance? java.util.RandomAccess that))
      (and (== cnt (count that))
           (loop [i (int 0)]
             (cond
               (== i cnt)
               true

               (.equals (.nth this i) (nth that i))
               (recur (unchecked-inc-int i))

               :else false)))

      (or (instance? clojure.lang.Sequential that)
          (instance? java.util.List that))
      (.equals (seq this) (seq that))

      :else false))

  (hashCode [this]
    (if (== _hash (int -1))
      (loop [h (int 1) i (int 0)]
        (if (== i cnt)
          (do (set! _hash (int h))
              h)
          (let [val (.nth this i)]
            (recur (unchecked-add-int
                     (unchecked-multiply-int (int 31) h)
                     (Util/hash val))
                   (unchecked-inc-int i)))))
      _hash))

  (toString [this]
    (pr-str this))

  clojure.lang.IHashEq
  (hasheq [this]
    (if (== _hasheq (int -1))
      (compile-if (resolve 'clojure.core/hash-ordered-coll)
        (let [h (hash-ordered-coll this)]
          (do (set! _hasheq (int h))
              h))
        (loop [h (int 1) xs (seq this)]
          (if xs
            (recur (unchecked-add-int
                     (unchecked-multiply-int (int 31) h)
                     (Util/hasheq (first xs)))
                   (next xs))
            (do (set! _hasheq (int h))
                h))))
      _hasheq))

  clojure.lang.Counted
  (count [this]
    cnt)

  clojure.lang.IMeta
  (meta [this]
    _meta)

  clojure.lang.IObj
  (withMeta [this m]
    (DoubleEndedVector.
      head trie tail headoff trieoff tailoff shift cnt m _hash _hasheq))

  clojure.lang.Indexed
  (nth [this i]
    (cond
      (< i headoff)
      (aget head i)

      (>= i tailoff)
      (if (>= i cnt)
        (throw (IndexOutOfBoundsException.))
        (aget tail (- i tailoff)))

      :else
      (let [idx (unchecked-subtract-int i headoff)
            arr (.trieArrayFor this idx)]
        (aget arr (bit-and idx (int 0x1f))))))

  (nth [this i not-found]
    (if (and (>= i (int 0)) (< i cnt))
      (.nth this i)
      not-found))

  clojure.lang.IPersistentCollection
  (cons [this x]
    (if (< (- cnt tailoff) (int 32))
      (let [tail-len (alength tail)
            new-tail (object-array (inc tail-len))]
        (System/arraycopy tail 0 new-tail 0 tail-len)
        (aset new-tail tail-len x)
        (DoubleEndedVector.
          head trie new-tail headoff trieoff tailoff
          shift (inc cnt) _meta -1 -1))
      (let [tail-node (clojure.lang.PersistentVector$Node. nil tail)
            effective-trie-count (+ (- cnt headoff) trieoff)]
        (if (> (bit-shift-right effective-trie-count 5)
               (bit-shift-left 1 shift))
          (let [arr      (object-array 32)
                new-trie (clojure.lang.PersistentVector$Node. nil arr)
                new-tail (object-array 1)]
            (aset arr 0 trie)
            (aset arr 1 (.newPath this shift tail-node))
            (aset new-tail 0 x)
            (DoubleEndedVector.
              head new-trie new-tail headoff trieoff (+ tailoff 32)
              (+ shift 5) (inc cnt) _meta -1 -1))
          (let [new-tail (object-array 1)]
            (aset new-tail 0 x)
            (DoubleEndedVector.
              head (.pushTail this shift trie tail-node) new-tail
              headoff trieoff (+ tailoff 32)
              shift (inc cnt) _meta -1 -1))))))

  (empty [this]
    (with-meta empty-devec _meta))

  (equiv [this that]
    (cond
      (or (instance? clojure.lang.IPersistentVector that)
          (instance? java.util.RandomAccess that))
      (and (== cnt (count that))
           (loop [i (int 0)]
             (cond
               (== i cnt)
               true

               (= (.nth this i) (nth that i))
               (recur (unchecked-inc-int i))

               :else false)))

      (or (instance? clojure.lang.Sequential that)
          (instance? java.util.List that))
      (Util/equiv (seq this) (seq that))

      :else false))

  clojure.lang.IPersistentStack
  (peek [this]
    (if (pos? cnt)
      (aget tail (dec (alength tail)))))

  (pop [this]
    (cond
      (zero? cnt)
      (throw (IllegalStateException. "Can't pop empty vector"))

      (== 1 cnt)
      (with-meta empty-devec _meta)

      (> (- cnt tailoff) 1)
      (let [tail-len (dec (alength tail))
            new-tail (object-array tail-len)]
        (System/arraycopy tail 0 new-tail 0 tail-len)
        (DoubleEndedVector.
          head trie new-tail headoff trieoff tailoff
          shift (dec cnt) _meta -1 -1))

      (== headoff tailoff)
      (DoubleEndedVector.
        empty-array trie head 0 0 0
        5 (dec cnt) _meta -1 -1)

      :else
      (let [new-tail (.trieArrayFor this (- (- cnt headoff) 2))
            ^clojure.lang.PersistentVector$Node
            new-trie (.popTail this shift trie)]
        (cond
          (nil? new-trie)
          (DoubleEndedVector.
            head empty-node new-tail headoff 0 headoff
            5 (dec cnt) _meta -1 -1)

          (> shift 5)
          (let [arr ^objects (.-array new-trie)]
            (cond
              (nil? (aget arr 1))
              (DoubleEndedVector.
                head (aget arr 0) new-tail
                headoff
                (bit-and (bit-not (bit-shift-left 0x1f shift)) trieoff)
                (- tailoff 32)
                (- shift 5) (dec cnt) _meta -1 -1)

              (nil? (aget arr 2))
              (let [^clojure.lang.PersistentVector$Node
                    child1 (aget arr 0)
                    arr1   (.-array child1)
                    ^clojure.lang.PersistentVector$Node
                    child2 (aget arr 1)
                    arr2   (.-array child2)
                    i      (bit-and (bit-shift-left 0x1f shift) trieoff)
                    i'     (- 32 i)
                    j      (bit-and (bit-shift-left 0x1f shift) (- tailoff 33))
                    j'     (inc j)]
                (if (<= (+ i' j') 32)
                  (let [new-arr  (object-array 32)
                        new-trie (clojure.lang.PersistentVector$Node.
                                   nil new-arr)]
                    (System/arraycopy arr1 i new-arr 0 i')
                    (System/arraycopy arr2 0 new-arr i' j')
                    (DoubleEndedVector.
                      head new-trie new-tail
                      headoff
                      (bit-and
                        (bit-not (bit-shift-left 0x1f shift))
                        trieoff)
                      (- tailoff 32)
                      (- shift 5) (dec cnt) _meta -1 -1))
                  (DoubleEndedVector.
                    head new-trie new-tail headoff trieoff (- tailoff 32)
                    shift (dec cnt) _meta -1 -1)))

              :else
              (DoubleEndedVector.
                head new-trie new-tail headoff trieoff (- tailoff 32)
                shift (dec cnt) _meta -1 -1)))

          :else
          (DoubleEndedVector.
            head new-trie new-tail headoff trieoff (- tailoff 32)
            shift (dec cnt) _meta -1 -1)))))

  clojure.lang.IPersistentVector
  (assocN [this i x]
    (cond
      (and (<= 0 i) (< i cnt))
      (cond
        (< i headoff)
        (DoubleEndedVector.
          (doto head (aset i x)) trie tail headoff trieoff tailoff
          shift cnt _meta -1 -1)

        (>= i tailoff)
        (DoubleEndedVector.
          head trie (doto tail (aset (- i tailoff) x)) headoff trieoff tailoff
          shift cnt _meta -1 -1)

        :else
        (DoubleEndedVector.
          head (.assocInTrie this shift trie (+ trieoff (- i headoff)) x) tail
          headoff trieoff tailoff
          shift cnt _meta -1 -1))

      (== i cnt)
      (.cons this x)

      :else
      (throw (IndexOutOfBoundsException.))))

  (length [this]
    (.count this))

  clojure.lang.Reversible
  (rseq [this]
    ;; FIXME
    (seq (map #(nth this %) (range (dec cnt) -1 -1))))

  clojure.lang.Associative
  (assoc [this k v]
    (if (Util/isInteger k)
      (.assocN this k v)
      (throw (IllegalArgumentException. "Key must be integer"))))

  (containsKey [this k]
    (and (Util/isInteger k)
         (<= (int 0) (int k))
         (< (int k) cnt)))

  (entryAt [this k]
    (if (.containsKey this k)
      (clojure.lang.MapEntry. k (.nth this (int k)))
      nil))

  clojure.lang.ILookup
  (valAt [this k not-found]
    (if (Util/isInteger k)
      (let [i (int k)]
        (if (and (>= i (int 0)) (< i cnt))
          (.nth this i)
          not-found))
      not-found))

  (valAt [this k]
    (.valAt this k nil))

  clojure.lang.IFn
  (invoke [this k]
    (if (Util/isInteger k)
      (let [i (int k)]
        (if (and (>= i (int 0)) (< i cnt))
          (.nth this i)
          (throw (IndexOutOfBoundsException.))))
      (throw (IllegalArgumentException. "Key must be integer"))))

  (applyTo [this args]
    (let [n (RT/boundedLength args 1)]
      (case n
        0 (throw (clojure.lang.ArityException.
                   n (.. this (getClass) (getSimpleName))))
        1 (.invoke this (first args))
        2 (throw (clojure.lang.ArityException.
                   n (.. this (getClass) (getSimpleName)))))))

  clojure.lang.Seqable
  (seq [this]
    ;; FIXME
    (seq (map #(nth this %) (range cnt))))

  clojure.lang.Sequential

  #_#_
  devec.core.IReversibleVector
  (rvec [this]
    ;; TODO
    )

  #_#_#_
  devec.core.ISliceableVector
  (subvec [this start]
    (.subvec this start cnt))

  (subvec [this start end]
    ;; TODO
    )

  devec.core.IDoubleEndedVector
  (consLeft [this x]
    (if (== headoff 32)
      (let [new-head  (doto (object-array 1) (aset 0 x))
            head-node (clojure.lang.PersistentVector$Node. nil head)]
        (cond
          (pos? trieoff)
          (DoubleEndedVector.
            new-head (.pushHead this shift trie head-node) tail
            1 (- trieoff 32) (inc tailoff)
            shift (inc cnt) _meta -1 -1)

          (some? (aget (.-array ^clojure.lang.PersistentVector$Node trie) 31))
          (let [arr       (object-array 32)
                new-child (.newPathRight this shift head-node)]
            (aset arr 0 new-child)
            (aset arr 1 trie)
            (DoubleEndedVector.
              new-head (clojure.lang.PersistentVector$Node. nil arr) tail
              1 (- (bit-shift-left 1 (+ shift 5)) 32) (inc tailoff)
              (+ shift 5) (inc cnt) _meta -1 -1))

          :else
          (let [arr       (.-array ^clojure.lang.PersistentVector$Node trie)
                new-child (.newPathRight this (- shift 5) head-node)
                new-arr   (object-array 32)]
            ;; not bothering to compute the actual number of slots
            (System/arraycopy arr 0 new-arr 1 31)
            (aset new-arr 0 new-child)
            (DoubleEndedVector.
              new-head (clojure.lang.PersistentVector$Node. nil new-arr) tail
              1 (- (bit-shift-left 1 shift) 32) (inc tailoff)
              shift (inc cnt) _meta -1 -1))))
      (let [new-head (object-array (inc headoff))]
        (System/arraycopy head 0 new-head 1 headoff)
        (aset new-head 0 x)
        (DoubleEndedVector.
          new-head trie tail (inc headoff) trieoff (inc tailoff)
          shift (inc cnt) _meta -1 -1))))

  (popLeft [this]
    (cond
      (zero? cnt)
      (throw (IllegalStateException. "Can't pop empty vector"))

      (== 1 cnt)
      (with-meta empty-devec _meta)

      (pos? headoff)
      (let [head-len (dec headoff)
            new-head (object-array head-len)]
        (System/arraycopy head 1 new-head 0 head-len)
        (DoubleEndedVector.
          new-head trie tail head-len trieoff (dec tailoff)
          shift (dec cnt) _meta -1 -1))

      (zero? tailoff)
      (let [tail-len (dec cnt)
            new-tail (object-array tail-len)]
        (System/arraycopy tail 1 new-tail 0 tail-len)
        (DoubleEndedVector.
          empty-array empty-node new-tail 0 0 0
          5 (dec cnt) _meta -1 -1))

      :else
      (let [new-head (.trieArrayFor this 0)
            new-head (if (zero? headoff)
                       (let [arr (object-array 31)]
                         (System/arraycopy new-head 1 arr 0 31)
                         arr)
                       new-head)
            new-hoff (alength new-head)
            ^clojure.lang.PersistentVector$Node
            new-trie (.popHead this shift trie)]
        (cond
          (nil? new-trie)
          (DoubleEndedVector.
            new-head empty-node tail new-hoff 0 new-hoff
            5 (dec cnt) _meta -1 -1)

          (> shift 5)
          (let [arr ^objects (.-array new-trie)]
            (cond
              (nil? (aget arr 1))
              (DoubleEndedVector.
                new-head (aget arr 0) tail
                new-hoff
                (bit-and (bit-not (bit-shift-left 0x1f shift)) (+ trieoff 32))
                (dec tailoff)
                (- shift 5) (dec cnt) _meta -1 -1)

              (nil? (aget arr 2))
              (let [^clojure.lang.PersistentVector$Node
                    child1 (aget arr 0)
                    arr1   (.-array child1)
                    ^clojure.lang.PersistentVector$Node
                    child2 (aget arr 1)
                    arr2   (.-array child2)
                    i      (bit-and (bit-shift-left 0x1f shift) (+ trieoff 32))
                    i'     (- 32 i)
                    j      (bit-and (bit-shift-left 0x1f shift) (dec tailoff))
                    j'     (inc j)]
                (if (<= (+ i' j') 32)
                  (let [new-arr  (object-array 32)
                        new-trie (clojure.lang.PersistentVector$Node.
                                   nil new-arr)]
                    (System/arraycopy arr1 i new-arr 0 i')
                    (System/arraycopy arr2 0 new-arr i' j')
                    (DoubleEndedVector.
                      new-head new-trie tail
                      new-hoff
                      (bit-and
                        (bit-not (bit-shift-left 0x1f shift))
                        (+ trieoff 32))
                      (dec tailoff)
                      (- shift 5) (dec cnt) _meta -1 -1))
                  (DoubleEndedVector.
                    new-head new-trie tail new-hoff (+ trieoff 32) (dec tailoff)
                    shift (dec cnt) _meta -1 -1)))

              :else
              (DoubleEndedVector.
                new-head new-trie tail new-hoff (+ trieoff 32) (dec tailoff)
                shift (dec cnt) _meta -1 -1)))

          :else
          (DoubleEndedVector.
            new-head new-trie tail new-hoff (+ trieoff 32) (dec tailoff)
            shift (dec cnt) _meta -1 -1)))))

  devec.core.IDoubleEndedVectorImpl
  (trieArrayFor [this i]
    (let [i (+ i trieoff)]
      (loop [shift shift node trie]
        (if (zero? shift)
          (.-array ^clojure.lang.PersistentVector$Node node)
          (let [arr (.-array ^clojure.lang.PersistentVector$Node node)]
            (recur (- shift 5)
                   (aget arr (bit-and (bit-shift-right i shift) 0x1f))))))))

  (popTail [this shift node]
    (let [sub (bit-and
                (bit-shift-right
                         (+ trieoff (- (- cnt headoff) 2)) shift)
                0x1f)]
      (cond
        (> shift 5)
        (let [new-child (.popTail this (- shift 5) (aget (.-array node) sub))]
          (if (and (nil? new-child) (zero? sub))
            nil
            (let [arr (aclone (.-array node))]
              (aset arr sub new-child)
              (clojure.lang.PersistentVector$Node. nil arr))))

        (zero? sub)
        nil

        :else
        (let [arr (aclone (.-array node))]
          (aset arr sub nil)
          (clojure.lang.PersistentVector$Node. nil arr)))))

  (popHead [this shift node]
    (let [sub (bit-and (bit-shift-right trieoff shift) 0x1f)]
      (cond
        (> shift 5)
        (let [new-child (.popHead this (- shift 5)
                          (aget (.-array node) sub))]
          (if (and (nil? new-child) (== 31 sub))
            nil
            (let [arr (aclone (.-array node))]
              (aset arr sub new-child)
              (clojure.lang.PersistentVector$Node. nil arr))))

        (== 31 sub)
        nil

        :else
        (try
          (let [arr (aclone (.-array node))]
            (aset arr sub nil)
            (clojure.lang.PersistentVector$Node. nil arr))
          (catch Exception e
            (prn shift sub)
            (throw e))))))

  (assocInTrie [this shift node i x]
    (if (zero? shift)
      (let [arr (aclone (.-array node))]
        (aset arr (bit-and i 0x1f) x)
        (clojure.lang.PersistentVector$Node. nil arr))
      (let [arr (aclone (.-array node))
            sub (bit-and (bit-shift-right i shift) 0x1f)]
        (aset arr sub (.assocInTrie this (- shift 5) (aget arr sub) i x))
        (clojure.lang.PersistentVector$Node. nil arr))))

  (newPath [this shift node]
    (if (zero? shift)
      node
      (let [ret (clojure.lang.PersistentVector$Node. nil (object-array 32))]
        (aset (.-array ret) 0 (.newPath this (- shift 5) node))
        ret)))

  (newPathRight [this shift node]
    (if (zero? shift)
      node
      (let [ret (clojure.lang.PersistentVector$Node. nil (object-array 32))]
        (aset (.-array ret) 31 (.newPathRight this (- shift 5) node))
        ret)))

  (pushTail [this shift parent tail-node]
    (let [sub    (bit-and
                   (bit-shift-right (+ trieoff (- (dec cnt) headoff)) shift)
                   0x1f)
          arr    (aclone (.-array parent))
          ret    (clojure.lang.PersistentVector$Node. nil arr)
          node   (if (== shift 5)
                   tail-node
                   (let [child (aget arr sub)]
                     (if child
                       (.pushTail this (- shift 5) child tail-node)
                       (.newPath this (- shift 5) tail-node))))]
      (aset arr sub node)
      ret))

  (pushHead [this shift parent head-node]
    (let [sub    (bit-and (bit-shift-right (dec trieoff) shift) 0x1f)
          arr    (aclone (.-array parent))
          ret    (clojure.lang.PersistentVector$Node. nil arr)
          node   (if (== shift 5)
                   head-node
                   (let [child (aget arr sub)]
                     (if child
                       (.pushHead this (- shift 5) child head-node)
                       (.newPathRight this (- shift 5) head-node))))]
      (aset arr sub node)
      ret))

  java.io.Serializable

  java.lang.Comparable
  (compareTo [this that]
    (if (identical? this that)
      0
      (let [^clojure.lang.IPersistentVector v
            (cast clojure.lang.IPersistentVector that)
            vcnt (.count v)]
        (cond
          (< cnt vcnt) -1
          (> cnt vcnt) 1
          :else
          (loop [i (int 0)]
            (if (== i cnt)
              0
              (let [comp (Util/compare (.nth this i) (.nth v i))]
                (if (zero? comp)
                  (recur (unchecked-inc-int i))
                  comp))))))))

  java.lang.Iterable
  (iterator [this]
    (let [i (java.util.concurrent.atomic.AtomicInteger. 0)]
      (reify java.util.Iterator
        (hasNext [_] (< (.get i) cnt))
        (next [_]
          (try
            (.nth this (unchecked-dec-int (.incrementAndGet i)))
            (catch IndexOutOfBoundsException e
              (throw (java.util.NoSuchElementException.
                       "no more elements in RRB vector iterator")))))
        (remove [_] (throw-unsupported)))))

  java.util.Collection
  (contains [this o]
    (boolean (some #(= % o) this)))

  (containsAll [this c]
    (every? #(.contains this %) c))

  (isEmpty [_]
    (zero? cnt))

  (toArray [this]
    (into-array Object this))

  (toArray [this arr]
    (if (>= (count arr) cnt)
      (do (dotimes [i cnt]
            (aset arr i (.nth this i)))
          arr)
      (into-array Object this)))

  (size [_] cnt)

  (add [_ o]             (throw-unsupported))
  (addAll [_ c]          (throw-unsupported))
  (clear [_]             (throw-unsupported))
  (^boolean remove [_ o] (throw-unsupported))
  (removeAll [_ c]       (throw-unsupported))
  (retainAll [_ c]       (throw-unsupported))

  java.util.RandomAccess
  java.util.List
  (get [this i] (.nth this i))

  (indexOf [this o]
    (loop [i (int 0)]
      (cond
        (== i cnt) -1
        (= o (.nth this i)) i
        :else (recur (unchecked-inc-int i)))))

  (lastIndexOf [this o]
    (loop [i (unchecked-dec-int cnt)]
      (cond
        (neg? i) -1
        (= o (.nth this i)) i
        :else (recur (unchecked-dec-int i)))))

  (listIterator [this]
    (.listIterator this 0))

  (listIterator [this i]
    (let [i (java.util.concurrent.atomic.AtomicInteger. i)]
      (reify java.util.ListIterator
        (hasNext [_] (< (.get i) cnt))
        (hasPrevious [_] (pos? (int i)))
        (next [_]
          (try
            (.nth this (unchecked-dec-int (.incrementAndGet i)))
            (catch IndexOutOfBoundsException e
              (throw (java.util.NoSuchElementException.
                       "no more elements in RRB vector list iterator")))))
        (nextIndex [_] (.get i))
        (previous [_] (.nth this (.decrementAndGet i)))
        (previousIndex [_] (unchecked-dec-int (.get i)))
        (add [_ e]  (throw-unsupported))
        (remove [_] (throw-unsupported))
        (set [_ e]  (throw-unsupported)))))

  (subList [this a z]
    ;; FIXME
    (subvec this a z))

  (add [_ i o]               (throw-unsupported))
  (addAll [_ i c]            (throw-unsupported))
  (^Object remove [_ ^int i] (throw-unsupported))
  (set [_ i e]               (throw-unsupported)))

(def ^:private ^devec.core.DoubleEndedVector empty-devec
  (DoubleEndedVector.
    empty-array empty-node (object-array 0) 0 0 0 5 0 nil -1 -1))

(extend-protocol AsDoubleEndedVector

  clojure.lang.PersistentVector
  (-as-devec [this]
    (DoubleEndedVector.
      empty-array (.-root this) (.-tail this)
      0 0 (- (.count this) (alength (.-tail this)))
      (.-shift this) (.count this) (meta this) -1 -1))

  #_#_
  clojure.lang.APersistentVector$SubVector
  (-as-devec [this]
    ))

#_
(deftype ReversedDoubleEndedVector [v]
  )

(doseq [v [#'AsDoubleEndedVector
           #'-as-devec
           #'->DoubleEndedVector
           #_#'->ReversedDoubleEndedVector]]
  (alter-meta! v assoc :private true))

(defn conjl
  "Conjoins an item on to the given vector from the left, returning a
  double-ended vector."
  ([]
     [])
  ([v]
     v)
  ([v x]
     (.consLeft ^devec.core.IDoubleEndedVector (-as-devec v) x))
  ([v x & xs]
     (if xs
       (recur (conjl v x) (first xs) (next xs))
       (conjl v x))))

(defn popl
  "Pops an item off a double-ended vector, returning a shorter
  double-ended vector."
  [v]
  (.popLeft ^devec.core.IDoubleEndedVector (-as-devec v)))

(defn peekl
  "(nth v 0), counterpart to devec.core/popl."
  [v]
  (nth v 0))

#_
(defn rvec
  "Reverses the given vector."
  [v]
  (.rvec ^devec.core.IReversibleVector (-as-devec v)))

#_
(defn subvec
  "Returns a true (non-view) slice of the input vector bound by the
  given indices."
  ([v start]
     (.subvec ^devec.core.ISliceableVector (-as-devec v) start))
  ([v start end]
     (.subvec ^devec.core.ISliceableVector (-as-devec v) start end)))
