(ns com.brunobonacci.bad-boy.main
  (:require [com.brunobonacci.bad-boy.command-line :as cli]
            [com.brunobonacci.bad-boy.core :as core]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [safely.core :refer [sleep]]
            [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.utils :as ut]
            [where.core :refer [where]]
            [com.brunobonacci.oneconfig :refer [deep-merge configure]])
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
  [{:keys [mulog app-name]}]
  (u/set-global-context!
    {:app-name app-name :env (env)
     :version (version) :puid (ut/puid)})


  (let [stop*
        (let [publisher-conf (or  mulog {:type :console :pretty? true})]
          (log/info "Starting metrics reporting to: " publisher-conf)
          (u/start-publisher! publisher-conf))]

    (fn []
      (when stop*
        (sleep 1000)
        (stop*)
        (log/info "stopping publishers...")
        (sleep 1000)))))



(defn header
  [cfg cmd]
  (println
    (format
      "
============================================================

                 ---==| B A D - B O Y |==---

============================================================
            (C) 2019-2021 - Bruno Bonacci - v%s
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
        (get-in cfg [:groups (:killer-run cmd) :attack-rate] "???")
        "none"))))



(defn exit-with-error [n msg]
  (binding [*out* *err*]
    (println msg))
  (System/exit n))



(defn make-targets
  [{:keys [targets] :as m}]
  (if targets
    (assoc m :targets
      (where
        (cons :or
          (map cli/build-filter targets))))
    m))



(defn load-config
  [app-name]
  (let [config-entry (configure {:key app-name :env (env) :version (version)})
        cfg (deep-merge core/DEFAULT-CONFIG (:value config-entry) {:app-name app-name})]
    ;; build executable filters
    (-> cfg
      (update :default-selection make-targets)
      (update :groups
        (fn [m]
          (->> m
            (map (fn [[k v]]
                   [k (make-targets v)]))
            (into {})))))))



(defn -main
  [& cli]
  (let [cmd (cli/parse-options (str/join " " cli))
        cfg (load-config (:oneconfig cmd "bad-boy"))]

    (cond
      (cli/parse-error? cmd)
      (exit-with-error 1 cmd)

      (or (:help cmd) (nil? (:killer-run cmd)))
      (exit-with-error 0 (help-page))

      (:version cmd)
      (exit-with-error 0 (format "bad-boy - v%s\n\n" (version)))

      (and (:killer-run cmd) (not (get-in core/DEFAULT-CONFIG [:groups (:killer-run cmd)])))
      (do
        (header cfg cmd)
        (exit-with-error 1 (format "Group %s not found." (name (:killer-run cmd)))))


      (:killer-run cmd)
      (let [_    (header cfg cmd)
            stop (start-events-publisher! cfg)]
        (u/log ::app-started :run-mode :killer-run)
        (core/killer-run cfg (:killer-run cmd))
        (stop)
        (shutdown-agents)))))
