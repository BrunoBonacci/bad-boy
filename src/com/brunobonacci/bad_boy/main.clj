(ns com.brunobonacci.bad-boy.main
  (:require [com.brunobonacci.bad-boy.command-line :as cli]
            [com.brunobonacci.bad-boy.core :as core]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [safely.core :refer [sleep]]
            [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.utils :as ut])
  (:gen-class))



(defn version
  []
  (some-> (io/resource "bad-boy.version") slurp str/trim))



(defn env
  "returns the current environment the system is running in.
   This has to be provided by the infrastructure"
  []
  (or (System/getenv "ENV") "local"))



(defn help-page
  []
  (some-> (io/resource "help.txt") slurp (format (version))))



(defn start-events-publisher!
  "start the events publisher, returns a function to stop the publishers"
  []
  (u/set-global-context!
    {:app-name "bad-boy" :env (env)
     :version (version) :puid (ut/puid)})


  (let [stop*
        (let [publisher-conf
              (read-string
                (or (System/getenv "BADBOY_PUBLISHER_CONF")
                  (pr-str {:type :console :pretty? true})))]
          (log/info "Starting metrics reporting to: " publisher-conf)
          (u/start-publisher! publisher-conf))]

    (fn []
      (when stop*
        (sleep 1000)
        (stop*)
        (log/info "stopping publishers...")
        (sleep 1000)))))



(defn header
  [cmd]
  (println
    (format
      "
============================================================

                 ---==| B A D - B O Y |==---

============================================================
            (C) 2019-2020 - Bruno Bonacci - v%s
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
      (let [cfg  (if (:targets cmd)
                   (assoc-in core/DEFAULT-CONFIG
                     [:groups (:killer-run cmd) :targets]
                     (cli/build-filters cmd))
                   core/DEFAULT-CONFIG)
            _    (header cmd)
            stop (start-events-publisher!)]
        (u/log ::app-started :run-mode :killer-run)
        (core/killer-run cfg (:killer-run cmd))
        (stop)
        (shutdown-agents))

      :else
      (let [_    (header cmd)
            stop (start-events-publisher!)]
        (u/log ::app-started :run-mode :find-and-kill)
        (reset! core/dry-run (boolean (:dry-run cmd)))
        (core/find-and-kill-one core/asg core/ec2 (cli/build-filters cmd))
        (stop)
        (shutdown-agents)))))
