(ns com.brunobonacci.bad-boy
  (:require [cognitect.aws.client.api :as aws]
            [cognitect.aws.credentials :as credentials]
            [where.core :refer [where]])
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



(defn find-and-kill-one
  [asg ec2 filters]
  (let [groups (auto-scaling-groups asg filters)
        group  (rand-nth groups)
        ists   (:Instances group)
        target (if (= 0 (count ists)) nil (-> (rand-nth ists) :InstanceId))]
    (printf "[kill1] Found %d groups\n" (count groups))
    (printf "[kill1] Selected group: %s\n" (:AutoScalingGroupName group))
    (printf "[kill1] Selected target: %s\n" (or target "There is nothing to do here. :-(, lucky one!"))
    (when target
      (kill-instances ec2 [target]))))



(defn -main
  [& asg-names]
  (let [filters (where
                 (cons :or
                       (map (fn [g] [:AutoScalingGroupName :MATCHES? g]) asg-names)))]
    (find-and-kill-one asg ec2 filters)))
