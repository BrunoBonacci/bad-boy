(ns com.brunobonacci.bad-boy.main
  (:require [com.brunobonacci.bad-boy.command-line :as cli]
            [com.brunobonacci.bad-boy.core :as core]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [samsara.trackit :as trackit]
            [clojure.tools.logging :as log])
  (:gen-class))



(defn version
  []
  (some-> (io/resource "bad-boy.version") slurp str/trim))



(defn help-page
  []
  (some-> (io/resource "help.txt") slurp (format (version))))



(defn metrics-reporting!
  []
  (when (= "1" (System/getenv "BADBOY_METRICS_ENABLED"))
    (log/info "Starting metrics reporting to: "
              (or (System/getenv "BADBOY_METRICS_REPORTER") "console"))
    (trackit/start-reporting!
     {:type        (keyword (or (System/getenv "BADBOY_METRICS_REPORTER") "console"))
      :jvm-metrics :none
      :reporting-frequency-seconds 10
      :push-gateway-url  (or (System/getenv "BADBOY_METRICS_DEST") "http://localhost:9091")})))



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
Killer-run : %s group, rate: %s
============================================================
" (version)
    (java.util.Date.)
    (boolean (or (:dry-run cmd) (System/getenv "DRY_RUN")))
    (pr-str (:targets cmd))
    (pr-str (:killer-run cmd))
    (if (:killer-run cmd)
      (get-in core/DEFAULT-CONFIG [:groups (:killer-run cmd) :attack-rate] "???")
      "none"))))



(defn exit-with-error [n msg]
  (binding [*out* *err*]
    (println msg))
  (System/exit n))



(defn -main
  [& cli]
  (let [cmd (cli/parse-options (str/join " " cli))]

    (cond
      (cli/parse-error? cmd)
      (exit-with-error 1 cmd)

      (:help cmd)
      (exit-with-error 0 (help-page))

      (:version cmd)
      (exit-with-error 0 (format "bad-boy - v%s\n\n" (version)))

      (and (nil? (:targets cmd)) (nil? (:killer-run cmd)))
      (do
        (header cmd)
        (exit-with-error 0 "[no-op] No target selected, please provide a list of names for autoscaling groups to target, or use --default-selection !"))

      (and (:killer-run cmd) (not (get-in core/DEFAULT-CONFIG [:groups (:killer-run cmd)])))
      (do
        (header cmd)
        (exit-with-error 1 (format "Group %s not found." (name (:killer-run cmd)))))


      (:killer-run cmd)
      (let [cfg (if (:targets cmd)
                  (assoc-in core/DEFAULT-CONFIG
                            [:groups (:killer-run cmd) :targets]
                            (cli/build-filters cmd))
                  core/DEFAULT-CONFIG)]
        (header cmd)
        (metrics-reporting!)
        (core/killer-run cfg (:killer-run cmd)))

      :else
      (do
        (header cmd)
        (metrics-reporting!)
        (reset! core/dry-run (boolean (:dry-run cmd)))
        (core/find-and-kill-one core/asg core/ec2 (cli/build-filters cmd))))))
