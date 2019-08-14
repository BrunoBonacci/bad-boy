(ns com.brunobonacci.bad-boy.main
  (:require [com.brunobonacci.bad-boy.command-line :as cli]
            [com.brunobonacci.bad-boy.core :as core]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:gen-class))



(defn version
  []
  (some-> (io/resource "bad-boy.version") slurp str/trim))


(defn header
  [cmd]
  (println
   (format
    "
============================================================

                 ---==| B A D - B O Y |==---

============================================================
              (C) 2019 - Bruno Bonacci - v%s
------------------------------------------------------------
      Chaos testing and infrastructure hardening tool.
------------------------------------------------------------
   Time    : %s
   Dry-run : %b
   Targets : %s
============================================================
" (version)
    (java.util.Date.)
    (boolean (:dry-run cmd))
    (pr-str (:targets cmd)))))



(defn exit-with-error [n msg]
  (binding [*out* *err*]
    (println msg))
  (System/exit n))



(defn -main
  [& cli]
  (let [cmd (cli/parse-options (str/join " " cli))]
    (header cmd)

    (cond
      (cli/parse-error? cmd)
      (exit-with-error 1 cmd)

      (:help cmd)
      (exit-with-error 0 cmd)

      (:version cmd)
      (exit-with-error 0 (format "bad-boy - v%s\n\n" (version)))

      (nil? (:targets cmd))
      (exit-with-error 0 "[no-op] No target selected, please provide a list of regex for autoscaling groups to target, or use --default-selection !")

      :else
      (do
        (reset! core/dry-run (boolean (:dry-run cmd)))
        (core/find-and-kill-one core/asg core/ec2 (cli/build-filters cmd))))))
