(ns com.brunobonacci.bad-boy-test
  (:require [com.brunobonacci.bad-boy :refer :all]
            [midje.sweet :refer :all]))


(fact "is it cool?"
      (foo) => "do something cool")
