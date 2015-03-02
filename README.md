# devec

Double-Ended Vectors for Clojure, seamlessly interoperable with
Clojure's built-in PersistentVectors.

## Usage

*This is an experimental library!*

devec's purpose is to expand the API of regular Clojure vectors with
four new operations:

1. "conj to front" (`devec.core/conjl`);

2. "pop off the front" (`devec.core/popl`);

3. non-view slice (`devec.core/subvec`);

4. reverse (`devec.core/rvec`).

1 and 2 are already implemented, 3 and 4 will arrive in a future
release.

All of these can take as inputs either regular Clojure vectors or
existing devec double-ended vectors. Interoperability with
core.rrb-vector is possible in principle and will be implemented in a
future release.

Currently transients and vectors of primitives ("gvec") are not
supported; they will be in a future release.

In the present proof-of-concept release some features (notably seq
over devec) are implemented naively so that the data structure may be
tested and benchmarked on key vector operations (`conj`, `nth`,
`assoc`, `pop`). Optimized implementations are forthcoming in a future
release.

ClojureScript support is likewise forthcoming.

The above notices will be replaced with confirmations that the desired
features are implemented in future releases. A more detailed
discussion of devec's internals is forthcoming.

## Performance

An initial benchmark suite using
[Criterium](https://github.com/hugoduncan/criterium) is included and
can be run with `lein test devec.core-bench`. Here are the results of
some lookup benchmarks:

```clj
;; pv:
(vec (range -1000000 2000000))

;; dv:
(apply devec/conjl (vec (range 2000000)) (range -1 -1000001 -1))

(criterium.core/bench (nth pv 1234))
WARNING: Final GC required 13.97028911742518 % of runtime
WARNING: Final GC required 3.206823423747328 % of runtime
Evaluation count : 2410190280 in 60 samples of 40169838 calls.
             Execution time mean : 9.289051 ns
    Execution time std-deviation : 0.304145 ns
   Execution time lower quantile : 8.918948 ns ( 2.5%)
   Execution time upper quantile : 9.977462 ns (97.5%)
                   Overhead used : 15.613667 ns

Found 7 outliers in 60 samples (11.6667 %)
	low-severe	 7 (11.6667 %)
 Variance from outliers : 19.0283 % Variance is moderately inflated by outliers
(criterium.core/bench (nth dv 1234))
WARNING: Final GC required 3.777409164183418 % of runtime
Evaluation count : 2570210700 in 60 samples of 42836845 calls.
             Execution time mean : 8.068430 ns
    Execution time std-deviation : 0.332720 ns
   Execution time lower quantile : 7.637039 ns ( 2.5%)
   Execution time upper quantile : 8.924783 ns (97.5%)
                   Overhead used : 15.613667 ns

Found 4 outliers in 60 samples (6.6667 %)
	low-severe	 3 (5.0000 %)
	low-mild	 1 (1.6667 %)
 Variance from outliers : 27.1040 % Variance is moderately inflated by outliers

(criterium.core/bench (nth pv 1500000))
WARNING: Final GC required 3.694663028274491 % of runtime
Evaluation count : 2238508020 in 60 samples of 37308467 calls.
             Execution time mean : 8.701542 ns
    Execution time std-deviation : 0.327771 ns
   Execution time lower quantile : 8.311595 ns ( 2.5%)
   Execution time upper quantile : 9.373833 ns (97.5%)
                   Overhead used : 15.613667 ns

Found 4 outliers in 60 samples (6.6667 %)
	low-severe	 3 (5.0000 %)
	low-mild	 1 (1.6667 %)
 Variance from outliers : 23.8529 % Variance is moderately inflated by outliers
(criterium.core/bench (nth dv 1500000))
WARNING: Final GC required 3.079413482216065 % of runtime
Evaluation count : 2402544300 in 60 samples of 40042405 calls.
             Execution time mean : 9.531229 ns
    Execution time std-deviation : 0.398040 ns
   Execution time lower quantile : 9.165994 ns ( 2.5%)
   Execution time upper quantile : 10.594534 ns (97.5%)
                   Overhead used : 15.613667 ns

Found 5 outliers in 60 samples (8.3333 %)
	low-severe	 2 (3.3333 %)
	low-mild	 3 (5.0000 %)
 Variance from outliers : 28.6561 % Variance is moderately inflated by outliers
```

## Underlying data structure

The underlying data structure is internally very similar to a Clojure
PersistentVector, with the following differences:

1. it uses a "head" array in addition to a tail;

2. its trie section admits "empty prefixes".

Lookups which land in the trie section transform the supplied index by
adding an `int` and subtracting another `int`, then proceed by radix
search. The trie may be higher by at most one level than it would be
in a PersistentVector of the same length.

## Releases and dependency information

devec is currently being developed against Clojure 1.7.0-alpha5,
however it should work under (and eventually will be tested against)
earlier versions.

[devec releases are available from Clojars](https://clojars.org/devec).

Please follow the link above to discover the current release number.

[Leiningen](http://leiningen.org/) dependency information:

    [org.clojure/core.rrb-vector "${version}"]

[Maven](http://maven.apache.org/) dependency information:

    <dependency>
      <groupId>org.clojure</groupId>
      <artifactId>core.rrb-vector</artifactId>
      <version>${version}</version>
    </dependency>

[Gradle](http://www.gradle.org/) dependency information:

    compile "org.clojure:core.rrb-vector:${version}"

## Developer information

Patches will only be accepted from developers who have signed the
Clojure Contributor Agreement and would be happy for the code they
contribute to devec to become part of the Clojure project.

## Clojure(Script) code reuse

devec's double-ended vectors support the same basic functionality
regular Clojure's vectors do (with the omissions listed above). Where
possible, this is achieved by reusing code from Clojure's gvec and
ClojureScript's PersistentVector implementations. The Clojure(Script)
source files containing the relevant code carry the following
copyright notice:

    Copyright (c) Rich Hickey. All rights reserved.
    The use and distribution terms for this software are covered by the
    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
    which can be found in the file epl-v10.html at the root of this distribution.
    By using this software in any fashion, you are agreeing to be bound by
      the terms of this license.
    You must not remove this notice, or any other, from this software.

## Licence

Copyright © 2015 Michał Marczyk

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
