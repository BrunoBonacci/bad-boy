(ns com.brunobonacci.bad-boy.command-line
  (:require [instaparse.core :as insta :refer [defparser]]
            [clojure.java.io :as io]))


(defparser parser (io/resource "cli-grammar.ebnf"))



(defn parse-options
  [cli]
  (->> cli
     (parser)
     (insta/transform
      {:help    #(vector :help    true)
       :version #(vector :version true)
       :dry-run #(vector :dry-run  true)

       :target-name  #(array-map :target-name %)
       :tag (fn [[_ k] [_ v]] {:tag {k v}})

       :preset  (fn [[p]] {:preset p})
       :command (fn [& args] (into {} args))
       })))


;;(parse-options "-h --version tag:chaos-testing='foo with space'")
;;(parse-options "-h --version unlucky*-test")
;;(parse-options "unlucky* --dryrun")
;;(parse-options "")
;;(parse-options "--default-selection")