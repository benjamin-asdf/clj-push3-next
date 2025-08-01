(ns benjamin-schwerdtner.problems.hiff
  (:require [random-seed.core :as rand]))

;; -------------------------------

;; =================
;; Hierachical IFF
;; =================

;; R. Watson, G. S. Hornby, J. B. Pollack 2006
;; Modeling building-block interdependency
;; https://link.springer.com/chapter/10.1007/BFb0056853


;; --------------------

(defn iff [a b]
  ;; (if (= a b) 1 0)
  (bit-and a b))


;;
;; string of symbols `B` = { b1, ..., bn }, over an alphabet S where n=k^p
;;
;; represents a hierachical block structure
;;
;; k = number of sub-blocks in a block = `block-size`
;; p = is the number of levels in the hierarchy = `hierarchy-levels`
;;
(defn blocks [size coll]
  (partition (/ (count coll) size) coll))

;;
;; T(B) =
;;
(defn hierachical-transform
  "
  In effect, calls `transform` with `block-size` elements to 'interpret' 1 level.

  It goes then up 1 hierachy level and repeats. So `transform` is called on the
  interpretation of the leaf symbols.

  (given by `block-size` and the length of the input).

  Example with 'iff' as transform:

  (hierachical-transform iff 2 [0 0])
  => iff( 0 0 )
  => 1

  (hierachical-transform iff 2 [0 0 1 1])

  => iff( iff(0 0) iff(1 1) )
  => iff(   1       1  )
  =>  1

  (hierachical-transform iff 2 [0 0 1 0])
  0
  "
  [transform block-size [elm :as input]]
  (if (= (count input) 1)
    ;; atom level
    elm
    (apply
     transform
     (map
      (partial hierachical-transform transform block-size)
      ;; block size also means how many args does `t` take.
      (blocks block-size input)))))

;; F(B) =
(defn hierachical-block-fitness
  "
  Returns the hierachical block fitness `F(B)`.

  R. Watson, G. S. Hornby, J. B. Pollack 2006
  Modeling building-block interdependency
  https://link.springer.com/chapter/10.1007/BFb0056853

  This is for creating '(inter)dependent' problems, i.e. where
  the fitness contribution of 1 feature depends on the context of another.


  transform: Interprets `block-size` elements into a single symbol,
  which is input to `f` and `transform`.

  f: f(x) -> number, the fitness of a single symbol.


  The outcome of fitness is scaled by the level of hierachy,

  Example:

(def hierachical-if-and-only-if
  (partial
   hierachical-block-fitness
   (fn t [a b]
     ;; t outcome is ∈ {0,1,nil}
     ({[0 0] 1 [1 1] 1} [a b]))
   (fn f [a]
     ;; 1 -> 1
     ;; 0 -> 1
     ;; nil -> 0
     ({0 1 1 1} a 0))
   2))


  Each atomic bit contributes to H-IFF:

  (hierachical-if-and-only-if [0 1])
  2

  On the scond level of hierachy, the combination of the same bit counts twice:

  (hierachical-if-and-only-if [0 0])
  4


  In a Dawkins like analogy, carnivore teeth count a little bit fitness and herbivore stomach, too.
  But carnivore teeth in the context of carnivore stomach count even more.


"
  [transform f block-size [elm :as input]]
  (if (= (count input) 1)
    (f elm)
    ;; -------
    (+ (* (count input)
          (f (hierachical-transform transform block-size input)))
       (reduce +
               (map
                (partial hierachical-block-fitness transform f block-size)
                (blocks block-size input))))))



(def hierachical-if-and-only-if
  (partial
   hierachical-block-fitness
   (fn t [a b]
     ;; t outcome is ∈ {0,1,nil}
     ({[0 0] 1 [1 1] 1} [a b]))
   (fn f [a]
     ;; 1 -> 1
     ;; 0 -> 1
     ;; nil -> 0
     ({0 1 1 1} a 0))
   2))

(comment
  (hierachical-transform iff 2 [0 0])
  1
  (hierachical-transform iff 2 [0 0 1 1])
  1
  (hierachical-transform iff 2 [0 0 1 0])
  0
  (hierachical-if-and-only-if [0 0 1 1])
  12
  (hierachical-if-and-only-if [0 0])
  4
  (hierachical-if-and-only-if [0 1])
  2
  (hierachical-if-and-only-if [0 0 0 0 1 1 1 1])

  ;; This is basically the canonical function that is hard for hill climbing algorithms
  ;; The local and global optima are maximally far apart.

  ;; max-fit for 64bit:

  (hierachical-if-and-only-if (repeatedly 64 (constantly 0)))
  448
  (hierachical-if-and-only-if (repeatedly 64 (constantly 1)))
  448)


(defn permute-vec
  "Return a permuted vector by `perm`.

  'shuffle-indices'."
  [perm v]
  (mapv #(nth v %) perm))

(defn make-shuffled-hiff
  [{:keys [hierachy-levels]}]
  ;; k == 2
  (let [block-size 2
        bit-string-length (long (Math/pow block-size hierachy-levels))
        bit-perm (into [] (rand/shuffle (range bit-string-length)))]
    {:shuffle-perm bit-perm
     :shuffled-hiff (fn [input-vec]
                      (hierachical-if-and-only-if
                       (permute-vec bit-perm input-vec)))}))


(comment
  [(hierachical-transform iff 2 [0 1 1 1])
   (hierachical-transform iff 2 [1 1 1 1])]
  [0 1]
  (make-shuffled-hiff {:hierachy-levels 2}))
