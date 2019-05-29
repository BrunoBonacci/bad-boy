(ns com.brunobonacci.bad-boy
  (:refer-clojure :exclude [rand-nth])
  (:require [cognitect.aws.client.api :as aws]
            [where.core :refer [where]]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:gen-class))

;;(def creds (credentials/system-property-credentials-provider))



(def ec2 (aws/client {:api :ec2}))
(def asg (aws/client {:api :autoscaling}))
(def dry-run (atom (System/getenv "DRY_RUN")))



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


;; lazy wrapper for query
(defn lazy-query
  [next-token-key result*]
  "Return a function which takes a query as a lambda function and
   returns a lazy pagination over the items"
  (fn lq
    ([q]
     ;; mapcat is not lazy so defining one
     (lazy-mapcat result* (lq q nil)))
    ;; paginate lazily the query
    ([q start-from]
     (let [result (q start-from)]
       (lazy-seq
        (if-let [next-page (get result next-token-key)]
          (cons result
                (lq q next-page))
          [result]))))))



(defn auto-scaling-groups
  [asg filters]
  (->>
   ((lazy-query :NextToken :AutoScalingGroups)
    #(aws/invoke asg
                 (cond-> {:op :DescribeAutoScalingGroups}
                   % (assoc :NextToken %))))
   (filter filters)
   (map #(select-keys % [:AutoScalingGroupName :MinSize :MaxSize :DesiredCapacity :Instances :Tags]))
   (map #(update % :Tags tags-map))))



(defn kill-instances [ec2 instance-ids]
  (println (if @dry-run "[DRY-RUN]" "[ACTION]") "KILLING:" instance-ids)
  (prn (aws/invoke ec2 {:op :TerminateInstances :request {:InstanceIds instance-ids :DryRun @dry-run}})))


(defn rand-nth [c]
  (when (> (count c) 0)
    (clojure.core/rand-nth c)))

(defn find-and-kill-one
  [asg ec2 filters]
  (let [groups (auto-scaling-groups asg filters)
        group  (rand-nth groups)
        ists   (:Instances group)
        target (if (= 0 (count ists)) nil (-> (rand-nth ists) :InstanceId))]
    (println (format "[kill1] Found %d groups" (count groups)))
    (println (format "[kill1] Selected group: %s"
                   (:AutoScalingGroupName group)))
    (println (format "[kill1] Selected target: %s"
                   (or target "There is nothing to do here. :-(, lucky day!")))
    (when target
      (kill-instances ec2 [target]))))



(defn header
  [targets]
  (println
   (format
    "
============================================================

                 ---==| B A D - B O Y |==---

============================================================
              (C) 2019 - Bruno Bonacci - v%s
------------------------------------------------------------
      Chaos testing and infrastructure hardening tool.

   Time    : %s
   Targets : %s
============================================================
" (some-> (io/resource "bad-boy.version") slurp str/trim)
  (java.util.Date.)
  (pr-str targets))))


(defn -main
  [& asg-names]
  (header asg-names)
  (if-not (seq asg-names)
    (println "[no-op] No target selected, please provide a list of regex for autoscaling groups to target.!")
    (let [filters (where
                   (cons :or
                         (map (fn [g] [:AutoScalingGroupName :MATCHES? g]) asg-names)))]
      (find-and-kill-one asg ec2 filters))))
