(ns com.brunobonacci.bad-boy.core-test
  (:require [com.brunobonacci.bad-boy.core :refer :all]
            [midje.sweet :refer :all]))


(fact
 "testing random-selection-by-rate"

 (let [selector
       (random-selection-by-rate
        {:run-cycle 60000
         :groups
         {:all
          {:attack-rate [0.3 :daily]}}}
        :all)]

   (->>
    (mapcat
     (fn [_] (selector (range 100)))
     (range (* 24 60 1000)))
    count
    (#(quot % 1000))
    (#(<= 29 % 31))))

 => true
 )
