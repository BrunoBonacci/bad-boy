(defproject com.brunobonacci/bad-boy (-> "./resources/bad-boy.version" slurp .trim)
  :description "A chaos testing and infrastructure hardening tool."

  :url "https://github.com/BrunoBonacci/bad-boy"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/bad-boy.git"}

  :dependencies [[org.clojure/clojure           "1.10.1"]
                 [org.clojure/tools.logging     "0.5.0"]
                 [com.cognitect.aws/api         "0.8.301"]
                 [com.cognitect.aws/endpoints   "1.1.11.537"]
                 [com.cognitect.aws/ec2         "714.2.430.0"]
                 [com.cognitect.aws/autoscaling "712.2.426.0"]
                 [com.brunobonacci/where        "0.5.5"]
                 [instaparse                    "1.4.10"]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :bin {:name "bad-boy"
        :jvm-opts ["-server" "$JVM_OPTS" "-Dfile.encoding=utf-8"]}

  :main com.brunobonacci.bad-boy.main

  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[midje "1.9.8"]
                                  [org.clojure/test.check "0.10.0-alpha4"]
                                  [criterium "0.4.5"]
                                  [org.slf4j/slf4j-log4j12 "1.8.0-beta4"]]
                   :resource-paths ["dev-resources"]
                   :plugins      [[lein-midje "3.2.1"]
                                  [lein-binplus "0.6.5"]]}}

  )
