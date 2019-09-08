(ns com.brunobonacci.bad-boy.core
  (:refer-clojure :exclude [rand-nth])
  (:require [cognitect.aws.client.api :as aws]
            [clojure.tools.logging :as log]
            [where.core :refer [where]]
            [safely.core :refer [safely sleeper]]
            [samsara.trackit :refer [track-distribution track-count]]))



;;(def creds (credentials/system-property-credentials-provider))      ;
;;(reset! dry-run 1)



(def DEFAULT-CONFIG
  {:run-cycle 60000 ;; 1 min
   :groups
   {:all
    {:targets (where [(comp :chaos-testing :Tags) :is? "opt-in"])
     :attack-rate [0.30 :daily]}}})



(def ec2 (aws/client {:api :ec2}))



(def asg (aws/client {:api :autoscaling}))



(def dry-run (atom (System/getenv "DRY_RUN")))



(defn- period
  [period]
  (case period
    :year      (* 365 24 60 60 1000)
    :yearly    (* 365 24 60 60 1000)
    :month     (*  30 24 60 60 1000)
    :monthly   (*  30 24 60 60 1000)
    :week      (*   7 24 60 60 1000)
    :weekly    (*   7 24 60 60 1000)
    :day       (*     24 60 60 1000)
    :daily     (*     24 60 60 1000)
    :hour      (*        60 60 1000)
    :hourly    (*        60 60 1000)
    :minute    (*           60 1000)
    :second    (*              1000)))



(defn aws-request
  [client request]
  (let [response (aws/invoke client request)]
    (when (-> response :Response :Errors :Error)
      (throw (ex-info (-> response :Response :Errors :Error :Message)
                      (-> response :Response :Errors))))
    response))



(defn tags-map
  [tags & {:keys [key-prefix] :or {key-prefix nil}}]
  (->> tags
     (map (juxt :Key :Value))
     (map (fn [[k v]] [(keyword (str key-prefix k)) v]))
     (into {})))



;; mapcat isn't fully lazy
(defn lazy-mapcat
  "maps a function over a collection and
   lazily concatenate all the results."
  [f coll]
  (lazy-seq
   (if (not-empty coll)
     (concat
      (f (first coll))
      (lazy-mapcat f (rest coll))))))



(defn lazy-paginated-query
  "It creates a generic wrapper for a AWS paginated query"
  [query-fn token-name result-fn]
  (fn lazy-query
    ([query]
     (lazy-mapcat result-fn (lazy-query nil query)))
    ([page-token query]
     (let [result (query-fn
                   (cond-> query
                     page-token (assoc token-name page-token)))]
       (lazy-seq
        (if-let [next-page (get result token-name)]
          (cons result
                (lazy-query next-page query))
          [result]))))))



(defn auto-scaling-groups
  [asg filters]
  (let [desc-asg #(aws-request asg {:op :DescribeAutoScalingGroups :request %})
        lazy-desc-asg (lazy-paginated-query desc-asg :NextToken :AutoScalingGroups)]
    (->> (lazy-desc-asg {:MaxRecords 100})
       (map #(select-keys % [:AutoScalingGroupName :MinSize :MaxSize :DesiredCapacity :Instances :Tags]))
       (map #(update % :Tags tags-map))
       (filter filters))))



(defn kill-instances [ec2 instance-ids]
  (log/info (if @dry-run "[DRY-RUN]" "[ACTION]") "KILLING:" instance-ids)
  (when-not @dry-run
    (let [result (aws-request ec2 {:op :TerminateInstances :request {:InstanceIds instance-ids}})]
      (log/info  "KILLING:" instance-ids ", result:" (prn-str result))
      result)))



(defn rand-nth [c]
  (when (> (count c) 0)
    (clojure.core/rand-nth c)))



(defn find-and-kill-one
  [asg ec2 filters]
  (let [groups (auto-scaling-groups asg filters)
        group  (rand-nth groups)
        ists   (:Instances group)
        target (if (= 0 (count ists)) nil (-> (rand-nth ists) :InstanceId))]
    (log/infof "[attack: kill1] Found %d groups" (count groups))
    (log/infof "[attack: kill1] Selected group: %s" (:AutoScalingGroupName group))
    (log/infof "[attack: kill1] Selected target: %s"
               (or target "There is nothing to do here. :-(, lucky day!"))
    (when target
      (kill-instances ec2 [target]))))



(defn random-kill
  [asg ec2 rand-selector filters]
  (let [groups    (auto-scaling-groups asg filters)
        instances (->> groups
                     (map (juxt #(select-keys % [:AutoScalingGroupName :MinSize :MaxSize :DesiredCapacity]) :Instances))
                     (mapcat (fn [[asg insts]] (map (partial merge asg) insts))))
        dead      (rand-selector instances)]
    (track-count        "bad-boy.selection.groups-pool"    (count groups))
    (track-count        "bad-boy.selection.instances-pool" (count instances))

    (when (seq dead)
      (track-distribution "bad-boy.random-kill.kill-size" (count dead))
      (track-count        "bad-boy.random-kill.kills"     (count dead))
      (log/infof "[attack: random-kill] Found %d groups and %d instances, killing: %d"
                 (count groups) (count instances) (count dead)))

    (doseq [instance dead]
      (log/infof "[attack: random-kill] Selected target: %s / %s"
                 (:AutoScalingGroupName instance) (:InstanceId instance))
      (safely
       (kill-instances ec2 [(:InstanceId instance)])
       :on-error
       :max-retries 3
       :default nil))))



(defn random-selection-by-rate
  [{:keys [run-cycle] :as cfg} group]
  {:pre [(>= run-cycle 60000)
         (<= 0 (get-in cfg [:groups group :attack-rate 0]) 1)
         (keyword? (get-in cfg [:groups group :attack-rate 1])) ]}
  (let [[attack-rate attack-period] (get-in cfg [:groups group :attack-rate])
        num-cycles  (/ (period attack-period) run-cycle)
        attack-prob (/ attack-rate num-cycles )]
    (fn [coll]
      (let [attack-prob (/ attack-prob (inc (count coll)))]
        (filter #(when (<= (rand) attack-prob) %) coll)))))



(defn killer-run
  [{:keys [run-cycle] :as cfg} group]
  (let [filters  (get-in cfg [:groups group :targets])
        selector (random-selection-by-rate DEFAULT-CONFIG group)
        sleep*   (sleeper :fix run-cycle)]
    (println "Killer on the run, press CTRL-c to stop it!")
    (loop []
      (track-count "bad-boy.killer-run.runs")
      (random-kill asg ec2 selector filters)
      (sleep*)
      (recur))))
