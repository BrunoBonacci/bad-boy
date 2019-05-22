(ns perf
  (:require [com.brunobonacci.bad-boy :refer :all]
            [criterium.core :refer [bench quick-bench]]))


(comment

  ;; perf tests

  (bench (Thread/sleep 1000))

  )
