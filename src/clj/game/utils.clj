(ns game.utils
  (:require [clojure.string :refer [split-lines split join lower-case]]))

(def cid (atom 0))

(defn make-cid []
  (swap! cid inc))

(defn abs [n] (max n (- n)))

(defn merge-costs [costs]
  (vec (reduce #(let [key (first %2) value (last %2)]
              (assoc %1 key (+ (or (key %1) 0) value)))
           {} (partition 2 (flatten costs)))))

(defn remove-once [pred coll]
  (let [[head tail] (split-with pred coll)]
    (vec (concat head (rest tail)))))

(defn has?
  "Checks the string property of the card to see if it contains the given value"
  [card property value]
  (when-let [p (property card)]
    (> (.indexOf p value) -1)))

(defn card-is?
  "Checks the property of the card to see if it is equal to the given value,
  as either a string or a keyword"
  [card property value]
  (let [cv (property card)]
    (cond
      (or (keyword? cv) (and (string? value) (string? cv))) (= value cv)
      (and (keyword? value) (string? cv)) (= value (keyword (lower-case cv)))
      :else (= value cv))))

(defn zone
  "Associate the specified zone to each item in the collection.
  Zone can be a singleton or a sequential collection"
  [zone coll]
  (let [dest (if (sequential? zone) (vec zone) [zone])]
    (map #(assoc % :zone dest) coll)))

(defn to-keyword [string]
  (if (string? string)
    (keyword (lower-case string))
    string))

(defn capitalize [string]
  (str (Character/toUpperCase (first string)) (subs string 1)))

(defn costs-to-symbol [costs]
  (clojure.string/join ", " (map #(let [key (first %) value (last %)]
                                   (case key
                                     :credit (str value "[Credits]")
                                     :click (reduce str (for [i (range value)] "[Click]"))
                                     (str value (str key)))) (partition 2 (flatten costs)))))

(defn vdissoc [v n]
  (vec (concat (subvec v 0 n) (subvec v (inc n)))))

(defn distinct-by [f coll]
  (letfn [(step [xs seen]
            (lazy-seq (when-let [[x & more] (seq xs)]
                        (let [k (f x)]
                          (if (seen k)
                            (step more seen)
                            (cons x (step more (conj seen k))))))))]
    (step coll #{})))

(defn string->num [s]
  (try
    (let [num (bigdec s)]
      (if (and (> num Integer/MIN_VALUE) (< num Integer/MAX_VALUE)) (int num) num))
  (catch Exception e nil)))

(def safe-split (fnil clojure.string/split ""))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn make-label
  "Looks into an ability for :label, if it doesn't find it, capitalizes :msg instead."
  [ability]
  (or (:label ability) (and (string? (:msg ability)) (capitalize (:msg ability))) ""))

(defn cancellable
  "Wraps a vector of prompt choices with a final 'Cancel' option. Optionally sorts the vector alphabetically,
  with Cancel always last."
  ([choices] (cancellable choices false))
  ([choices sorted]
   (if sorted
     (conj (vec (sort-by :title choices)) "Cancel")
     (conj (vec choices) "Cancel"))))

(defn cost-names
  "Converts a cost (value attribute pair) to a string for printing"
  [value attr]
  (when (pos? value)
    (case attr
      :credit (str value " [$]")
      :click  (->> "[Click]" repeat (take value) (apply str))
      nil)))

(defn build-cost-str
  "Gets the complete cost-str for specified costs"
  [costs]
  (->> costs
       (map #(cost-names (second %) (first %)))
       (filter some?)
       (interpose " and ")
       (apply str)))

(defn build-spend-msg
  "Constructs the spend message for specified cost-str and verb(s)."
  ([cost-str verb] (build-spend-msg cost-str verb nil))
  ([cost-str verb verb2]
   (if (or (not (instance? String cost-str))
           (= "" cost-str))
     (str (or verb2 (str verb "s")) " ")
     (str "spends " cost-str " to " verb " "))))

(defn other-side [side]
  (cond (= side :corp) :runner
        (= side :runner) :corp))

(defn side-str
  "Converts kw into str. If str is passed same str is returned."
  [side]
  (cond
    (= side :corp) "Corp"
    (= side "Corp") "Corp"
    (= side :runner) "Runner"
    (= side "Runner") "Runner"))

(defn same-side?
  "Checks if two supplied sides are the same side. Accepts both keyword and str."
  [side1 side2]
  (= (side-str side1) (side-str side2)))

(defn same-card?
  "Checks if the two cards are the same by :cid. Alternatively specify 1-function to use to check the card"
  ([card1 card2] (same-card? :cid card1 card2))
  ([func card1 card2]
    (= (func card1) (func card2))))

;;; Functions for working with zones.
(defn remote-num->name [num]
  (str "Server " num))

(defn remote->name [zone]
  "Converts a remote zone to a string"
  (let [kw (if (keyword? zone) zone (last zone))
        s (str kw)]
    (if (.startsWith s ":remote")
      (let [num (last (split s #":remote"))]
        (remote-num->name num)))))

(defn central->name [zone]
  "Converts a central zone keyword to a string."
  (case (if (keyword? zone) zone (last zone))
    :hq "HQ"
    :rd "R&D"
    :archives "Archives"
    nil))

(defn zone->name [zone]
  "Converts a zone to a string."
  (or (central->name zone)
      (remote->name zone)))

(defn zone->sort-key [zone]
  (case (if (keyword? zone) zone (last zone))
    :archives -3
    :rd -2
    :hq -1
    (string->num
      (last (safe-split (str zone) #":remote")))))

(defn zones->sorted-names [zones]
  (->> zones (sort-by zone->sort-key) (map zone->name)))

(defn is-remote? [zone]
  "Returns true if the zone is for a remote server"
  (not (nil? (remote->name zone))))

(defn is-central? [zone]
  "Returns true if the zone is for a central server"
  (not (is-remote? zone)))

(defn central->zone [zone]
  "Converts a central server keyword like :discard into a corresponding zone vector"
  (case (if (keyword? zone) zone (last zone))
    :discard [:servers :archives]
    :hand [:servers :hq]
    :deck [:servers :rd]
    nil))

(defn get-server-type [zone]
  (or (#{:hq :rd :archives} zone) :remote))

(defn private-card [card]
  (select-keys card [:zone :cid :side :new :host :counter :advance-counter :hosted :icon]))

(defn combine-subtypes [is-distinct subtype-string & new-subtypes]
  "Takes an existing subtype-string, adds in the new-subtypes, and removes
  duplicates if is-distinct is truthy"
  (let [do-distinct #(if is-distinct (distinct %) %)]
    (->> (split (or subtype-string " - ") #" - ")
         (concat new-subtypes)
         do-distinct
         (join " - "))))

(defn remove-subtypes [subtype-string & subtypes-to-remove]
  "Takes an existing subtype-string and removes all instances of
  subtypes-to-remove"
  (->> (split (or subtype-string " - ") #" - ")
       (remove #(some #{%} subtypes-to-remove))
       (join " - ")))
